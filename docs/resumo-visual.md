# Resumo visual — a cola da banca

> 1 página, 5 diagramas, os números do projeto. GitHub renderiza os Mermaid nativamente.

**Números:** 3 serviços · 9 ADRs (+índice) · 41 testes plano B + 35 asserções e2e no CI · 3 contratos PACT (2 HTTP + 1 mensagem) · 4 bugs reais achados no plano A · demo de 1 comando (`./demo.ps1`).

**Mecanismos de transporte** (código de cor nos diagramas): 🔵 **tópico Kafka** (pub-sub, ordem por chave) · 🟠 **fila RabbitMQ** (work queue, um a um) · seta simples = **HTTP/REST** (síncrono) · os 3 efeitos numa **transação SQL local** · **cache Caffeine** in-process.

## 1 · Mapa de transações e mecanismos — o que corre por onde (ADR-002)

> **Por que cada mecanismo:** **tópico Kafka** para eventos pub-sub com ordem por conta (quem publica não conhece quem consome); **fila RabbitMQ** para trabalho um-a-um com aceite imediato (o "guichê" da reconsolidação); **HTTP/REST** síncrono para comando e leitura — o miss do cache é governado por PACT.

```mermaid
flowchart TB
    Canal([Canal / App bancário])
    I[Ingestão :8081]
    Co[Consolidação :8082]
    Q[Consulta :8083]
    KT1{{"TÓPICO KAFKA · lancamentos-recebidos"}}
    KT2{{"TÓPICO KAFKA · posicao-atualizada"}}
    RQ[/"FILA RABBITMQ · reconsolidacao"/]

    Canal -->|"HTTP · POST /lancamentos"| I
    I -->|publica| KT1
    KT1 -->|"consome · ordem por chave = conta"| Co
    Co -->|"publica via outbox"| KT2
    KT2 -->|"consome · broadcast (group.id por instância)"| Q
    Canal -->|"HTTP · POST /reconsolidacoes"| Co
    Co -->|enfileira| RQ
    RQ -->|"consome 1 a 1 · o guichê"| Co
    Canal -->|"HTTP · GET /extrato"| Q
    Q -->|"HTTP · GET /interno/posicoes (cache miss · par do PACT)"| Co

    classDef kafka fill:#e8f2ff,stroke:#3b82f6,color:#1e3a5f;
    classDef rabbit fill:#fff0e6,stroke:#f97316,color:#7c2d12;
    class KT1,KT2 kafka;
    class RQ rabbit;
```

## 2 · Fluxo feliz — os três efeitos e o outbox (ADRs 004/005)

> Transportes na sequência: **HTTP** (Canal↔serviços), **tópico Kafka** (ingestão→consolidação→consulta) e **transação SQL local** (os 3 efeitos).

```mermaid
sequenceDiagram
    autonumber
    participant C as Canal/App
    participant I as Ingestão
    participant K as Kafka
    participant Co as Consolidação
    participant DB as Base (Postgres)
    participant Q as Consulta

    C->>I: HTTP POST /lancamentos (X-Correlation-Id)
    I-->>C: 202 ACEITO (id ecoado)
    I->>K: publica tópico lancamentos-recebidos (chave = conta)
    K->>Co: consome (retry 3x backoff, ADR-007)
    rect rgb(232, 242, 255)
        note over Co,DB: UMA transação SQL local (ADR-005)
        Co->>DB: 1. grava lançamento (UNIQUE = dedup, ADR-004)
        Co->>DB: 2. atualiza posição (conta x competência)
        Co->>DB: 3. grava outbox evento_pendente
    end
    Co->>K: publica tópico posicao-atualizada (pós-ack)
    K->>Q: entrega evento (broadcast, group.id por instância)
    Q->>Q: invalida cache Caffeine (idempotente)
    C->>Q: HTTP GET /extrato/{cliente}/{competência}
    Q->>Co: HTTP GET /interno/posicoes (miss · PACT · timeout+disjuntor)
    Q-->>C: 200 extrato + carimbo do DADO (US-07)
```

## 3 · Estrutura de dados da consolidação (a dedup e a outbox são TABELAS)

```mermaid
erDiagram
    LANCAMENTO_INCORPORADO {
        bigint id PK
        string instituicao_origem "UNIQUE(1/2) - identidade"
        string id_lancamento_origem "UNIQUE(2/2) - dedup ADR-004"
        string id_cliente
        string agencia
        string conta
        string tipo "CREDITO|DEBITO"
        decimal valor
        date competencia "mes da OCORRENCIA (US-03)"
    }
    POSICAO_CONSOLIDADA {
        bigint id PK
        string id_cliente
        string conta "UNIQUE conta x competencia"
        date competencia
        decimal entradas
        decimal saidas
        decimal saldo
        timestamp atualizado_em "o carimbo do dado (US-07)"
    }
    EVENTO_PENDENTE {
        bigint id PK
        string id_cliente "so referencia - minimizacao US-10"
        date competencia
        string correlacao_id "sobrevive a fronteira assincrona"
        timestamp criado_em
        timestamp publicado_em "null = pendente; outbox ADR-005"
    }
    LANCAMENTO_INCORPORADO }o--|| POSICAO_CONSOLIDADA : "agrega em"
    POSICAO_CONSOLIDADA ||--o{ EVENTO_PENDENTE : "atualização gera"
```

## 4 · Régua de veneno em 3 camadas (ADR-007 + nota)

> Vale para os DOIS consumidores — o do **tópico Kafka** (`lancamentos-recebidos`) e o da **fila RabbitMQ** (`reconsolidacao`). A régua é a mesma; só muda o destino físico da DLQ.

```mermaid
flowchart TD
    M[mensagem chega<br/>tópico Kafka OU fila RabbitMQ] --> D{deserializa?}
    D -- "não (lixo)" --> H[handler: bytes crus p/ DLQ<br/>com a causa nos headers] --> S[fluxo SEGUE<br/>sem travar a partição/fila]
    D -- sim --> P{processa?}
    P -- falha --> R[retry 3x backoff exponencial<br/>1s - 2s - 4s, jitter]
    R -- curou --> OK[incorporado]
    R -- "esgotou (permanente)" --> DLQ["DLQ com a causa<br/>Kafka: failure-strategy=dead-letter-queue<br/>Rabbit: reject + auto-bind-dlq"] --> S
    P -- sucesso --> OK
    DLQ -. "reprocessar-dlq.ps1<br/>(seguro: dedup ADR-004)" .-> M
```

## 5 · Cache da consulta: hit, miss, broadcast e disjuntor (ADR-006/007)

> Transportes aqui: **HTTP** para `GET /extrato` e o miss em `GET /interno/posicoes`; a invalidação chega por **tópico Kafka** (`posicao-atualizada`).

```mermaid
flowchart TD
    G[HTTP GET /extrato] --> C{cache Caffeine?<br/>TTL 5min = meta US-05}
    C -- hit --> R[200 + carimbo do DADO]
    C -- miss --> CB{disjuntor<br/>fonte-posicoes}
    CB -- fechado --> F[HTTP GET /interno/posicoes<br/>par do PACT, Timeout 2s]
    F -- ok --> P[popula cache + guarda última-boa] --> R
    F -- falha --> FB{tem última<br/>resposta boa?}
    CB -- "aberto: pára de<br/>martelar a fonte" --> FB
    FB -- sim --> UB[serve cópia + carimbo antigo<br/>expõe a idade US-05/07] --> R
    FB -- não --> E503[503 + Retry-After<br/>nunca 500 opaco]
    EV[TÓPICO KAFKA<br/>posicao-atualizada] -->|"broadcast: group.id<br/>por instância"| INV[invalida entrada<br/>em TODAS as réplicas]
    INV --> C
```
