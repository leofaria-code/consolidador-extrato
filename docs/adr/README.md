# Índice das ADRs

> Toda decisão segue o formato Contexto → Alternativas (com os porquês das rejeições) → Decisão → Consequências (custos assumidos + mitigação). Regra da casa, herdada do Fábio (Sessão 6): a resposta nunca é "porque funcionou" — é "consideramos X e Y, escolhemos Y por isso".

| ADR | Decisão em uma linha | Atende | Rejeitadas |
|---|---|---|---|
| [001 — Stack](ADR-001-stack-quarkus.md) | Quarkus 3.33 LTS + Java 25, com tabela de tradução dos padrões Spring do curso | contexto Caixa + próximo módulo | Spring Boot; híbrido |
| [002 — Decomposição](ADR-002-decomposicao-de-dominio.md) | 3 serviços (1 por contexto da linguagem ubíqua), bases segregadas, "ninguém lê a base do outro" | critério 1; Sessão 6 dec. 1 | monolito modular; 2 serviços |
| [003 — Perfis de teste](ADR-003-perfis-de-teste.md) | Plano B (pura-JVM) como gate obrigatório + plano A (brokers reais) para o que só broker prova; inclui a nota pact-em-disco × Broker | critério 6 | só-Docker; só-in-memory |
| [004 — Idempotência](ADR-004-idempotencia-unicidade-na-base.md) | Unicidade na base: a memória de dedup é o próprio lançamento incorporado, sem expiração; nota sobre a interação com o expurgo (US-11×US-04) | US-02/US-03; Sessão 6 dec. 8 | memória em processo; janela com TTL |
| [005 — Consistência](ADR-005-consistencia-tres-efeitos-outbox.md) | Três efeitos numa transação local + outbox transacional; análise de queda ponto a ponto; evento "pelo menos uma vez" | US-05/US-10; Sessão 6 dec. 3 | XA/2PC; publicar pós-commit |
| [006 — Cache miss](ADR-006-consulta-em-cache-miss.md) | Cache Caffeine + chamada interna à consolidação (par do PACT); TTL 5 min = meta de frescor; carimbo do dado | US-06/US-07; Sessão 6 dec. 2/4/5 | réplica por evento; Redis compartilhado |
| [007 — Resiliência](ADR-007-resiliencia-retry-dlq.md) | 3 retentativas em processo com backoff exponencial + DLQ com causa nos headers; @Timeout sem retry no cache miss | US-08/US-09; Sessão 6 dec. 8 | retry infinito; descarte; reentrega do broker |
| [008 — Métricas](ADR-008-metricas-micrometer-prometheus.md) | Micrometer + /q/metrics (pull); built-in antes de contador custom; tags só enumeradas (LGPD); Grafana opcional por profile | US-12 (opcional); critérios 2/3/5 medidos | OTel agora; MP Metrics; logs-como-métrica |

Decisões ainda em aberto viram ADR **antes** do incremento que depende delas (aceite da issue #7). Candidatas mapeadas e já fechadas: as 5 da Sessão 6.
