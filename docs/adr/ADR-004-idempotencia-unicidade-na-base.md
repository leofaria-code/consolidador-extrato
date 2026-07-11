# ADR-004 — Idempotência do consumidor: unicidade na base, sem janela de expiração

- **Status:** aceita · 07/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** ADR candidato #3 da Sessão 6 (dúvida da Sandy: "guardamos toda identidade já vista, pra sempre?")
- **Relaciona-se com:** ADR-002 (a base segregada da consolidação é quem guarda a memória), ADR-005 (a verificação participa da mesma transação dos três efeitos)

## Contexto

A US-02 exige que lançamento repetido seja **ignorado silenciosamente** — reenvio é comportamento normal das origens (retry, reprocessamento de lote, reentrega do broker). A identidade do lançamento é o par (`instituicao_origem`, `id_lancamento_origem`) — Sessão 2. A US não diz **como lembrar** do que já foi processado; a Sessão 6 devolveu a decisão ao time, com a dica do Fábio: *"o lançamento incorporado já fica guardado com a identidade dele — a própria base, com unicidade na identidade, é uma memória de deduplicação que não expira"*. E o alerta: se houver janela separada, *"documentem o que acontece com o repetido que chega depois da janela"*.

Cenário que pressiona a decisão: a **carga inicial** de um cliente recém-consentido reprocessa até 12 meses de histórico (US-03); reprocessamento pós-DLQ (US-08) e reconsolidação (US-09) também reapresentam lançamentos antigos — ou seja, **repetido "velho" é caso real**, não borda teórica.

## Alternativas consideradas

1. **Memória local em processo** (o `GuardaIdempotenciaEmMemoria` provisório do Inc-1) — simples, mas não sobrevive a restart, não escala para mais de uma instância e cresce sem limite. Sempre foi declarado provisório; descartada como definitiva.
2. **Janela de deduplicação** (chaves recentes em cache/Redis com TTL) — lookup rápido e memória limitada; porém o repetido que chega **depois** da janela duplica silenciosamente — exatamente o que a US-02 proíbe, e o caso é real (carga inicial de 12 meses, reprocesso de DLQ). Exigiria uma segunda linha de defesa na base de qualquer forma.
3. **Unicidade na base segregada** — o próprio `lancamento_incorporado`, com **constraint UNIQUE** em (`instituicao_origem`, `id_lancamento_origem`), é a memória de deduplicação: não expira, sobrevive a restart, vale para N instâncias, e não é estrutura extra — é o dado que a consolidação já precisa guardar (US-05 e reconsolidação US-09 reaproveitam a mesma tabela). Custo: um SELECT por mensagem e a tabela cresce com o histórico (mitigável: o expurgo da US-11 já remove dados por consentimento; particionamento por competência fica como evolução).

## Decisão

**Alternativa 3.** Dentro da transação de consolidação (ADR-005):

1. Verificação de existência pela identidade (`SELECT` pela chave única) — se já incorporado, retorna `REPETIDO`: log em nível *debug* com identificadores opacos, sem erro, offset segue normal.
2. Se primeira vez, o `INSERT` do lançamento grava também a memória de deduplicação — mesmo ato, mesma transação.
3. A **constraint UNIQUE é a última linha de defesa** contra corrida (duas instâncias processando o mesmo lançamento simultaneamente): a segunda transação falha na constraint, faz rollback e a reentrega cai no caso "repetido". A corrida é rara por construção — a chave Kafka (instituição+agência+conta) direciona reenvios da mesma conta à mesma partição, processada sequencialmente.

O contrato `GuardaIdempotencia`/implementação em memória do Inc-1 é **removido**: a guarda deixa de ser um componente separado e vira regra de domínio dentro do serviço transacional de consolidação — não há como manter a semântica "exatamente uma vez por identidade" fora da transação que grava.

## Consequências

- (+) Dedup **não expira**: repetido de 12 meses atrás é ignorado igual ao de 12 segundos atrás — sem o "depois da janela" que a alternativa 2 teria de documentar como falha aceita.
- (+) Zero infraestrutura extra (sem Redis/estrutura dedicada); a fonte de verdade da dedup é a mesma da consolidação — impossível divergirem.
- (+) Reprocessos legítimos (DLQ, reconsolidação, carga inicial) ficam seguros de graça — US-08 depende disso ("idempotência garante segurança do reprocesso").
- (−) Um SELECT por mensagem no caminho quente. *Mitigação:* índice único cobre a busca; se medição futura apontar gargalo, uma janela em cache pode ser adicionada **na frente** da base (a base permanece como verdade — a janela viraria otimização, não mecanismo).
- (−) A tabela cresce com o histórico. *Mitigação:* expurgo por consentimento (US-11) já remove dados; retenção/particionamento por competência é evolução documentável.

## Nota (11/07): a interação expurgo × memória de dedup

Como a memória de deduplicação **é** a base de lançamentos, o expurgo da US-11 apaga as duas coisas juntas: se uma origem reenviar um lançamento já expurgado, a dedup não o reconhece e ele seria **reincorporado** — recriando exatamente o dado que o expurgo eliminou. A guarda correta para esse caso não é a idempotência, e sim a **US-04**: consentimento revogado/vencido bloqueia a ingestão *antes* do tópico — o reenvio pós-expurgo nunca chega ao consumidor. Consequência de desenho: **US-11 sem US-04 é incompleta**; quando o expurgo for implementado, a verificação de consentimento vigente precisa vir junto (ou antes). Registrado para a evolução — e para a arguição, se perguntarem "o que acontece com um repetido do que foi expurgado?".
