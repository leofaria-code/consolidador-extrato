# ADR-008 — Métricas: Micrometer + endpoint Prometheus, com Grafana opcional na demo

- **Status:** aceita · 11/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** extensão do Inc-6 (US-12 — opcional de observabilidade): logs + correlação respondem "o que aconteceu com ESTE pedido"; faltava responder "quanto, com que taxa, e está piorando?" sem grep em log
- **Relaciona-se com:** ADR-004 (a idempotência vira métrica: `repetido` × `incorporado`), ADR-006 (hit/miss do Caffeine built-in), ADR-007 (DLQ e disjuntor medidos, não só logados), ADR-002 (`shared-contracts` continua só com tipos de fronteira — nenhuma dependência de observabilidade lá), ADR-003 (métricas não podem exigir broker: o gate do plano B cobre o endpoint)

## Contexto

O Inc-6 entregou logs JSON estruturados e correlation id ponta a ponta — a ferramenta certa para **depurar um caso**. Mas três perguntas da operação (e da arguição) não se respondem com log: qual a taxa de duplicados que a idempotência está absorvendo? quantas mensagens foram à DLQ e por quê? qual a proporção de consultas servidas do cache? A resposta por log exige agregação externa; a resposta certa é métrica agregável no processo, raspada por quem quiser.

Restrições: banca em 4 dias (risco baixo obrigatório); `mvn verify -Pplano-b-jvm` tem que continuar passando **sem Docker** (critério 6); LGPD do domínio Open Finance — nenhum identificador de cliente/conta pode virar label.

## Alternativas consideradas

1. **OpenTelemetry completo (traces + métricas + OTLP/collector)** — a versão "de mercado": spans automáticos HTTP/Kafka com timeline. Custo: collector no compose, exporter configurado por serviço, e sobreposição com a história de correlação já entregue e testada no Inc-6 — reconta-la em spans a 4 dias da banca é risco sem critério novo atendido. **Rejeitada agora, anotada como evolução**: o BOM traz `quarkus-micrometer-opentelemetry`; como a instrumentação é a API Micrometer, migrar é trocar a dependência de registry, não reescrever contadores.
2. **MicroProfile Metrics (`smallrye-metrics`)** — a opção "MP puro". O Quarkus a substituiu por Micrometer como recomendação oficial (guia de migração próprio); seguir a API em fim de vida é dívida no dia zero. Rejeitada.
3. **Logs-como-métrica (contar no Loki/grep)** — nenhuma dependência nova, mas sem agregação barata, sem percentil, sem gauge, e acopla a pergunta operacional ao formato da linha de log. Rejeitada.
4. **Micrometer + `quarkus-micrometer-registry-prometheus`** — registry em memória, endpoint `/q/metrics` automático, JVM/HTTP/FT/cache built-in. Modelo **pull**: o serviço não sabe que o Prometheus existe — plano B intocado por construção. Está no BOM LTS da plataforma. **Escolhida.**

## Decisão

**Alternativa 4** nos 3 serviços, com três regras:

**Regra 1 — reusar built-in antes de escrever contador.** Com o registry no classpath: hit/miss do Caffeine (`metrics-enabled` no cache da consulta → `cache_gets_total{result=...}`), disjuntor/fallback/retry do SmallRye FT (`ft_*`), HTTP e JVM. A consulta inteira não ganhou **uma linha de Java**.

**Regra 2 — contadores custom pequenos, de negócio, prefixo `extrato.<contexto>.`** (Prometheus converte para `snake_case` + `_total`):

| Métrica (Prometheus) | Tags | Onde | O que prova |
|---|---|---|---|
| `extrato_ingestao_lancamentos_total` | `resultado=aceito\|rejeitado` | `LancamentoResource` | volume e qualidade na borda |
| `extrato_ingestao_lancamentos_publicados_total` | — | `PublicadorLancamentos` | o que entrou no tópico |
| `extrato_consolidacao_lancamentos_total` | `resultado=incorporado\|repetido` | `ProcessadorLancamentos` | **idempotência medida** (ADR-004) |
| `extrato_consolidacao_dlq_enviados_total` | `motivo=processamento\|deserializacao` | `ConsumidorLancamentos` / `FalhaDeserializacaoLancamentos` | US-08 quantificada |
| `extrato_consolidacao_reconsolidacoes_total` | — | `ProcessadorReconsolidacao` | o guichê trabalhando (US-09) |

Contadores incrementam **só no desfecho** — tentativa falha de `@Retry` não infla série (o retry já tem métrica própria, `ft_retry_*`).

**Regra 3 — cardinalidade baixa é lei.** Tags só com valores enumerados (`resultado`, `motivo`). Identificador de cliente/conta/correlação **nunca** vira label: explode séries no Prometheus e vaza dado pessoal em superfície não protegida (LGPD). Quem responde "qual cliente?" é o log correlacionado do Inc-6 — cada ferramenta na sua pergunta.

**Demo (opcional por profile):** `docker compose --profile observabilidade up` sobe Prometheus (scrape 5s em `/q/metrics`) + Grafana anônimo-viewer com datasource e dashboard provisionados por arquivo — reproduzível do zero, nada clicado à mão. O profile é opt-in: `demo.ps1` e o workflow e2e do CI não mudam.

## Consequências

- (+) Rubrica: resiliência e idempotência ganham evidência **quantitativa** ao vivo (painel de DLQ fica vermelho na frente da banca); assíncrono fica visível como a distância entre `aceito` e `incorporado` no gráfico de fluxo.
- (+) Custo marginal ~15 linhas de produção em 6 classes; todo o resto é built-in ou arquivo de infra versionado.
- (+) Caminho de evolução documentado: OTel = troca de registry, instrumentação preservada.
- (−) Nomes das métricas built-in (`cache_gets_total`, `ft_*`) são do SmallRye/Micrometer e podem mudar entre versões — mitigação: teste de presença no plano B (`MetricasConsultaTest`) pega divergência do cache; os `ft_*` do dashboard foram conferidos contra o `/q/metrics` real do plano A em 11/07 (achado: fallback é `ft_invocations_total{fallback="applied"}`, não `ft_fallback_calls_total` — ver uso-de-ia.md).
- (−) Sem tracing distribuído: a timeline visual de um pedido continua sendo lida por correlation id nos logs. Aceito — é exatamente o trade-off da alternativa 1.
- (−) Duas imagens a mais na demo (Prometheus/Grafana) — confinadas ao profile; quem roda `docker compose up` puro não paga nada.
