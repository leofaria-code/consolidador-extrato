# Observabilidade — logs, correlação, métricas e dashboard

> Guia consolidado do opcional de observabilidade (US-12), entregue em duas camadas:
> **Inc-6** (logs estruturados + correlação ponta a ponta) e **Inc-7** (métricas + stack visual, ADR-008).
> Cada camada responde uma pergunta diferente — e a regra da casa é usar cada uma na sua:
>
> | Pergunta | Ferramenta |
> |---|---|
> | "O que aconteceu com **este** pedido?" | logs + correlation id (Inc-6) |
> | "**Quanto**, com que taxa, está piorando?" | métricas + dashboard (Inc-7, ADR-008) |
> | "O serviço está de pé / pronto?" | health checks (`/q/health`) |

## Camada 1 — Logs estruturados + correlação (Inc-6 / US-12)

### Logs

- **Plano A / prod**: JSON estruturado (`quarkus-logging-json`) — cada linha vira um documento com o `correlationId` como campo (o MDC vira campo no JSON).
- **Dev/teste**: console legível com o id visível: `HH:mm:ss LEVEL corr=<id> [classe] mensagem`.
- **LGPD (US-12)**: logs só com identificadores opacos — nunca nome/documento do cliente.

### Correlação ponta a ponta

O mesmo id atravessa o fluxo inteiro; chaves padronizadas na classe `Correlacao` de cada serviço:

| Fronteira | Portador do id |
|---|---|
| HTTP (entrada/saída dos 3 serviços) | header `X-Correlation-Id` (aceito do chamador ou gerado; ecoado no response) — `FiltroCorrelacao` |
| Tópicos Kafka (`lancamentos-recebidos`, `posicao-atualizada`) | header de mensagem `correlation-id` — `PublicadorLancamentos`, `PublicadorPosicaoAtualizada` |
| Outbox (ADR-005) | coluna `correlacao_id` (o id sobrevive à transação e segue no evento) |
| Fila RabbitMQ (`reconsolidacao`) | propriedade AMQP `correlationId` |
| HTTP interno consulta→consolidação (cache miss) | `X-Correlation-Id` reenviado — `PropagadorCorrelacao` |

**Como seguir um pedido na demo:**

```bash
CORR=$(uuidgen)
curl -s -X POST localhost:8081/lancamentos -H "X-Correlation-Id: $CORR" -H 'Content-Type: application/json' -d @lancamento.json
docker compose logs ingestao consolidacao consulta | grep "$CORR"
# ingestão (aceite) → consolidação (incorporado) → outbox → evento → invalidação do cache, todos com o mesmo id
```

> **Nota técnica (achado do Inc-6, registro em `uso-de-ia.md` 07/07):** o MDC do Quarkus **não é confiável em threads de mensageria** (put+get na mesma thread devolve null). Por isso os consumidores extraem o id da mensagem (`Correlacao.deMensagem`) e o carregam **explicitamente por parâmetro** na cadeia de processamento; MDC/`%X` só nas bordas HTTP, onde funciona.

Provado em: `CorrelacaoIngestaoTest`, `CorrelacaoFluxoTest`, `CorrelacaoConsultaTest`.

## Camada 2 — Métricas (Inc-7 / ADR-008)

### Endpoint

Cada serviço expõe `/q/metrics` (formato Prometheus, via Micrometer + `quarkus-micrometer-registry-prometheus` do BOM). Modelo **pull**: o serviço não sabe quem raspa — por isso o plano B (`mvn verify -Pplano-b-jvm`, sem Docker) continua intacto por construção.

```bash
curl -s localhost:8081/q/metrics | grep extrato_ingestao
curl -s localhost:8082/q/metrics | grep -E "extrato_consolidacao_(lancamentos|dlq|reconsolidacoes)"
curl -s localhost:8083/q/metrics | grep -E "cache_gets|ft_"
```

### Métricas de negócio (contadores custom)

Prefixo `extrato.<contexto>.` (Prometheus converte para `snake_case` + sufixo `_total`):

| Métrica | Tags | Onde incrementa | O que prova |
|---|---|---|---|
| `extrato_ingestao_lancamentos_total` | `resultado=aceito\|rejeitado` | `LancamentoResource` | volume e qualidade na borda |
| `extrato_ingestao_lancamentos_publicados_total` | — | `PublicadorLancamentos` | o que entrou no tópico |
| `extrato_consolidacao_lancamentos_total` | `resultado=incorporado\|repetido` | `ProcessadorLancamentos` | **idempotência medida** (ADR-004) |
| `extrato_consolidacao_dlq_enviados_total` | `motivo=processamento\|deserializacao` | `ConsumidorLancamentos` / `FalhaDeserializacaoLancamentos` | US-08 quantificada |
| `extrato_consolidacao_reconsolidacoes_total` | — | `ProcessadorReconsolidacao` | o guichê trabalhando (US-09) |

Contadores incrementam **só no desfecho** — tentativa falha de `@Retry` não infla série (retry tem métrica própria, `ft_retry_*`).

### Métricas built-in reusadas (regra 1 da ADR-008: não reinventar)

| Métrica | Origem | Uso |
|---|---|---|
| `cache_gets_total{cache="extrato-consolidado", result="hit"\|"miss"}` | Caffeine via `metrics-enabled` (consulta) | hit ratio do cache (ADR-006) |
| `ft_invocations_total{fallback="applied"}` | SmallRye Fault Tolerance | fallback "última resposta boa" acionado (ADR-007) — **atenção: NÃO existe `ft_fallback_calls_total`**; nome conferido contra o `/q/metrics` real em 11/07 |
| `ft_circuitbreaker_state_current{state="open"}` | SmallRye FT | disjuntor aberto (1 = sim) |
| `ft_retry_retries_total` / `ft_retry_calls_total` | SmallRye FT | retentativas da ADR-007 |
| `http_server_requests_seconds_*`, JVM | Micrometer | latência/erro por endpoint, saúde da JVM |

A consulta inteira não tem **uma linha de Java** de instrumentação — é 100% built-in.

### Regra de cardinalidade (LGPD — é lei, não estilo)

Tags só com valores **enumerados** (`resultado`, `motivo`). Identificador de cliente/conta/correlação **nunca** vira label: explodiria séries no Prometheus e vazaria dado pessoal em superfície não protegida. Quem responde "qual cliente?" é o log correlacionado da Camada 1.

### Testes (plano B, sem Docker)

`MetricasIngestaoTest` · `MetricasConsolidacaoTest` (o mesmo lançamento 2× produz `incorporado` E `repetido`) · `MetricasConsultaTest` (hit/miss built-in). Asserções de **presença**, nunca de valor exato — counters acumulam entre testes da mesma JVM.

## Stack visual — Prometheus + Grafana (padrão no compose desde 11/07)

```bash
docker compose up -d --build
```

| Serviço | Endereço | O que tem |
|---|---|---|
| Prometheus | `http://localhost:9090` | `/targets`: 4 alvos UP (ingestao/consolidacao/consulta/consulta-replica — todos sobem por padrão; ADR-008, revisão 11/07) |
| Grafana | `http://localhost:3000` (sem login — viewer anônimo) | dashboard **"Consolidador de Extrato — visão da banca"** |

Portas ocupadas na sua máquina? `PROMETHEUS_PORT=9091 GRAFANA_PORT=3001 docker compose up -d`.

Tudo provisionado por arquivo em [`infra/observabilidade/`](../infra/observabilidade/) (scrape 5s, datasource, dashboard JSON) — **nada clicado à mão**: a stack sobe pronta do zero, e edição pela UI do Grafana não sobrevive ao restart (proposital: o JSON versionado é a fonte de verdade).

### Os 4 painéis do dashboard

1. **Fluxo de lançamentos** — `aceitos` (ingestão, por segundo) × `rejeitados no último minuto` (ingestão) × `incorporados` × `repetidos ignorados` (consolidação). A distância entre aceito e incorporado é o assíncrono; a série de repetidos é a idempotência trabalhando; a de rejeitados usa `increase(...[1m])` para deixar um `400` isolado visível em tempo real.
2. **Cache hit ratio** — gauge do Caffeine (consulta).
3. **DLQ (acumulado)** — por `motivo`; **vermelho** quando ≥ 1 (tem mensagem aguardando `reprocessar-dlq`).
4. **Disjuntor e fallback do cache miss** — fallbacks aplicados, retentativas e estado do disjuntor.

Roteiro de demonstração ao vivo (lote → repetido → veneno → derrubar consolidação): ver o ato bônus em [`roteiro-banca.md`](roteiro-banca.md).

## O que NÃO tem (decisão, não esquecimento)

- **Tracing distribuído (OpenTelemetry)** — rejeitado no Inc-7 (risco a 4 dias da banca, sobreposição com a correlação já entregue); a migração futura é troca de registry, não reescrita — racional completo na [ADR-008](adr/ADR-008-metricas-micrometer-prometheus.md).
- **Alertas/coletor central de logs** — fora do escopo do projeto de curso; o dashboard é a superfície de observação da demo.
- **Health checks customizados** — os `/q/health` são os default do SmallRye (JVM de pé); readiness de dependências ficou como evolução.
