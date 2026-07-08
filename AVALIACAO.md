# Auto-avaliação — Consolidador de Extrato / Open Finance

Grupo: Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato)
Tema/domínio: Consolidador de extrato Open Finance — ingestão de lançamentos de múltiplas instituições por tópicos, consolidação por conta×competência e consulta com cache.
Perfil de execução: A (docker) · Fallbacks usados: perfil B (pura-JVM) para toda a suíte de testes.

> ⚠️ **Documento em construção** — níveis e evidências são preenchidos a cada incremento.
> Convenção: evidência = caminho de arquivo/classe/teste ou hash de commit.

## Evidências por critério

1. **Decomposição de domínio** — nível auto-atribuído: _
   Evidência: 3 serviços por contexto (`extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta`), bases segregadas (regra "ninguém lê a base do outro"), `docs/arquitetura.md`, `docs/adr/ADR-002-decomposicao-de-dominio.md`, linguagem ubíqua em `docs/requisitos/user-stories.md`.

2. **Comunicação assíncrona** — _
   Evidência (os 3 canais do desenho, cada um com a semântica certa):
   - **Tópico** `lancamentos-recebidos` (publica-assina): `PublicadorLancamentos` (chave = instituição+agência+conta, ordem por conta) → `ConsumidorLancamentos`.
   - **Tópico** `posicao-atualizada` (US-10): publicado via outbox (`PublicadorPosicaoAtualizada`), entrega "pelo menos uma vez", só referência — garantias em `docs/arquitetura.md` §Fluxos, provadas em `FluxoConsolidacaoTest`.
   - **Fila de trabalho** `reconsolidacao` (US-09, RabbitMQ): producer/consumer com aceite imediato e consumo um a um — o "guichê" (`ReconsolidacaoResource` → `ConsumidorReconsolidacao`), provada em `ReconsolidacaoTest`.

3. **Idempotência e consistência** — _
   Evidência:
   - Chave `IdentidadeLancamento` (instituicaoOrigem + idLancamentoOrigem, `shared-contracts`).
   - **Idempotência na base** (ADR-004): constraint UNIQUE em `lancamento_incorporado` = memória de dedup que não expira; verificação na mesma transação; constraint como última defesa contra corrida.
   - **Consistência dos 3 efeitos** (ADR-005): gravar + consolidar + registrar evento numa transação local (`ServicoConsolidacao`); outbox com marcação pós-ack; análise de queda ponto a ponto na ADR.
   - Testes (US-02): `ConsumidorIdempotenteTest` — 3 reenvios + 1 distinto → 2 incorporados **e** totais de processamento único; `FluxoConsolidacaoTest.repetidoNaoGeraEventoNemRegistroNaOutbox`. Verde em `mvn verify -Pplano-b-jvm` (verificado 07/07).

4. **Cache** — _
   Evidência:
   - `ServicoExtrato` (`@CacheResult`, Caffeine, chave cliente×competência) com **TTL 5 min = meta de frescor da US-05** — racional na `docs/adr/ADR-006-consulta-em-cache-miss.md` (réplica e Redis compartilhado rejeitados com o porquê).
   - Invalidação por evento: `ConsumidorPosicaoAtualizada` (idempotente por natureza — premissa Sessão 6).
   - Carimbo do **dado** (US-07): `ExtratoConsolidado.atualizadoEm` = mais recente entre as posições.
   - Atualizar sob demanda com limite por cliente (Sessão 6, decisão 5): `ControleAtualizacaoForcada` → 429.
   - Hit/miss/invalidação **demonstráveis** por teste (`ExtratoConsultaTest`, contador do dublê da fonte). Verde em `mvn verify -Pplano-b-jvm` (07/07).

5. **Resiliência** — _
   Evidência:
   - Política em `docs/adr/ADR-007-resiliencia-retry-dlq.md`: 3 retentativas em processo com backoff exponencial (1s×2, jitter) + DLQ com causa nos headers; parâmetros da ata da Sessão 6 (decisão 8), ajustáveis por configuração.
   - Implementação: `@Retry`+`@ExponentialBackoff` nos consumidores (`ConsumidorLancamentos`, `ConsumidorReconsolidacao`); `failure-strategy=dead-letter-queue` (Kafka) e `reject`+`auto-bind-dlq` (Rabbit); `@Timeout` 2s sem retry na chamada interna do cache miss.
   - Fila de trabalho `reconsolidacao` (US-09): aceite imediato + guichê um a um (`max-outstanding-messages=1`); reapuração idempotente por recálculo absoluto.
   - **Teste da banca** (`RetentativaEDlqTest`): falha transitória supera em exatamente 3 tentativas; mensagem envenenada consome 1+3 e o fluxo continua. `ReconsolidacaoTest`: contestação corrigida pela reapuração. Verde no plano B (07/07); encaminhamento físico à DLQ = demonstração no plano A (ADR-003).

6. **Testabilidade** — _
   Evidência (parcial — PACT ainda não implementado):
   - `mvn verify -Pplano-b-jvm` verde sem Docker: 5 módulos, 7 testes, 0 falhas (verificado em 05/07 — critério satisfeito por build real, não por leitura de código).
   - Connector in-memory do SmallRye (`RecursosEmMemoria`) substitui Kafka nos testes de ingestão e consolidação.
   - Estratégia dos dois perfis (A alta fidelidade × B Docker-free como gate) documentada em `docs/adr/ADR-003-perfis-de-teste.md`.
   - Pendente: contract test PACT consulta↔consolidação (Incremento 5, issue #6).

7. **Decisões arquiteturais** — _
   Evidência: `docs/adr/` — 7 ADRs, todas com alternativas rejeitadas e o porquê (ADR-001 stack; ADR-002 decomposição; ADR-003 perfis de teste A/B; ADR-004 idempotência na base; ADR-005 outbox transacional; ADR-006 cache miss; ADR-007 resiliência). **Os 5 candidatos da Sessão 6 estão fechados** (o candidato #1 já havia sido fechado como ADR-002 no bootstrap; os 4 pendentes da issue #7 — cache miss, idempotência, consistência, resiliência — fecharam como ADRs 004–007). Rastreabilidade decisão↔fala de stakeholder via `docs/requisitos/`.

8. **Uso crítico de IA** — _
   Como usamos IA e o que validamos manualmente: ver `docs/uso-de-ia.md` (log contínuo, honesto: inclui o que a IA errou e o que o grupo rejeitou/validou).

9. **Execução** — _
   Como rodar: `README.md` §Como rodar (TODO); perfil A declarado, testes no perfil B.

## Opcionais entregues (grupo de 4 → mínimo 1)

- **Observabilidade básica** (logs estruturados + correlação de id entre serviços) — exigência de negócio real (US-12/Sessão 5, LGPD):
  - Correlação ponta a ponta: `X-Correlation-Id` nas bordas HTTP (3 serviços, com eco no response) → header Kafka no tópico de ingestão → **persistido pela outbox** (`correlacao_id`) → header no evento `posicao-atualizada` → logado na invalidação da consulta. Fila de reconsolidação usa a propriedade AMQP. Provado em `CorrelacaoIngestaoTest`/`CorrelacaoFluxoTest`/`CorrelacaoConsultaTest`.
  - Logs JSON (`quarkus-logging-json`) no plano A/prod; console legível com `corr=` em dev/teste. Logs só com identificadores opacos (US-12).
  - Nota técnica de valor (critério 8): o MDC do Quarkus provou-se **não confiável em threads de mensageria** (probe: put+get na mesma thread → null) — consumidores carregam a correlação explicitamente; MDC/`%X` só nas bordas HTTP. Registro completo em `docs/uso-de-ia.md` (07/07).
- **Uso documentado e crítico de IA** (bônus; também evidencia o critério 8) — `docs/uso-de-ia.md`, em andamento desde o início.
