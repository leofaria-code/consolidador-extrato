# Auto-avaliação — Consolidador de Extrato / Open Finance

Grupo: Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato)
Tema/domínio: Consolidador de extrato Open Finance — ingestão de lançamentos de múltiplas instituições por tópicos, consolidação por conta×competência e consulta com cache.
Perfil de execução: A (docker) · Fallbacks usados: perfil B (pura-JVM) para toda a suíte de testes.

> ✅ **Notas validadas pelo grupo em 10/07 (noite)** — Leo, Sandy, Marcos e Rodrigo. Processo no `docs/uso-de-ia.md`: gaps nomeados foram fechados com código e prova ao vivo antes da ratificação.
> Convenção: evidência = caminho de arquivo/classe/teste ou hash de commit.
> **Notas abaixo são uma proposta gerada em sessão de IA (10/07, reavaliada em 11/07 após o hardening pré-banca)**, para o grupo ajustar antes do fechamento. Escala 0–100 por critério (independente do peso) — **não há escala oficial da rubrica documentada no material do curso**, então não é possível confirmar se é a mesma que a banca usa; os pesos por critério (que somam 100) vêm do enunciado do projeto (`CLAUDE.md`). Trajetória das notas: os critérios 4 e 5 começaram em 85 **com gaps nomeados** — e em vez de argumentar por nota, o grupo **fechou os gaps com código e prova ao vivo** (disjuntor+última-boa+DLQ scriptada; broadcast de invalidação com 2 instâncias). O 100,0 proposto é ousado de propósito: cada critério tem evidência executável e os trade-offs remanescentes estão documentados como decisão, não como falta. Cabe ao grupo calibrar.

## Resumo (recalcule aqui se os pesos mudarem)

Nota final = Σ (peso × nota ÷ 100). Peso e nota são independentes — se um peso mudar, só essa linha precisa recalcular, sem reavaliar a nota do critério.

| # | Critério | Peso | Nota proposta (0–100) | Contribuição (peso × nota ÷ 100) |
|---|---|---|---|---|
| 1 | Decomposição de domínio | 15 | 100 | 15,0 |
| 2 | Comunicação assíncrona | 15 | 100 | 15,0 |
| 3 | Idempotência e consistência | 12 | 100 | 12,0 |
| 4 | Cache | 10 | 100 | 10,0 |
| 5 | Resiliência | 12 | 100 | 12,0 |
| 6 | Testabilidade | 13 | 100 | 13,0 |
| 7 | Decisões arquiteturais (ADRs) | 13 | 100 | 13,0 |
| 8 | Uso crítico de IA | 5 | 100 | 5,0 |
| 9 | Execução | 5 | 100 | 5,0 |
| | **Total** | **100** | | **100,0** |

## Evidências por critério

1. **Decomposição de domínio** — peso 15 · nota proposta **100/100**
   Evidência: 3 serviços por contexto (`extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta`), bases segregadas (regra "ninguém lê a base do outro"), `docs/arquitetura.md`, `docs/adr/ADR-002-decomposicao-de-dominio.md`, linguagem ubíqua em `docs/requisitos/user-stories.md`.

2. **Comunicação assíncrona** — peso 15 · nota proposta **100/100**
   Evidência (os 3 canais do desenho, cada um com a semântica certa):
   - **Tópico** `lancamentos-recebidos` (publica-assina): `PublicadorLancamentos` (chave = instituição+agência+conta, ordem por conta) → `ConsumidorLancamentos`.
   - **Tópico** `posicao-atualizada` (US-10): publicado via outbox (`PublicadorPosicaoAtualizada`), entrega "pelo menos uma vez", só referência — garantias em `docs/arquitetura.md` §Fluxos, provadas em `FluxoConsolidacaoTest`.
   - **Fila de trabalho** `reconsolidacao` (US-09, RabbitMQ): producer/consumer com aceite imediato e consumo um a um — o "guichê" (`ReconsolidacaoResource` → `ConsumidorReconsolidacao`), provada em `ReconsolidacaoTest`.

3. **Idempotência e consistência** — peso 12 · nota proposta **100/100**
   Evidência:
   - Chave `IdentidadeLancamento` (instituicaoOrigem + idLancamentoOrigem, `shared-contracts`).
   - **Idempotência na base** (ADR-004): constraint UNIQUE em `lancamento_incorporado` = memória de dedup que não expira; verificação na mesma transação; constraint como última defesa contra corrida.
   - **Consistência dos 3 efeitos** (ADR-005): gravar + consolidar + registrar evento numa transação local (`ServicoConsolidacao`); outbox com marcação pós-ack; análise de queda ponto a ponto na ADR.
   - Testes (US-02): `ConsumidorIdempotenteTest` — 3 reenvios + 1 distinto → 2 incorporados **e** totais de processamento único; `FluxoConsolidacaoTest.repetidoNaoGeraEventoNemRegistroNaOutbox`. Verde em `mvn verify -Pplano-b-jvm` (verificado 07/07).

4. **Cache** — peso 10 · nota proposta **100/100** *(85 → 100; o gap nomeado foi fechado em 11/07)*
   Evidência:
   - `ServicoExtrato` (`@CacheResult`, Caffeine, chave cliente×competência) com **TTL 5 min = meta de frescor da US-05** — racional na `docs/adr/ADR-006-consulta-em-cache-miss.md` (réplica e Redis compartilhado rejeitados com o porquê).
   - Invalidação por evento: `ConsumidorPosicaoAtualizada` (idempotente por natureza — premissa Sessão 6).
   - Carimbo do **dado** (US-07): `ExtratoConsolidado.atualizadoEm` = mais recente entre as posições.
   - Atualizar sob demanda com limite por cliente (Sessão 6, decisão 5): `ControleAtualizacaoForcada` → 429.
   - Hit/miss/invalidação **demonstráveis** por teste (`ExtratoConsultaTest`, contador do dublê da fonte) **e a invalidação por evento provada no plano A com Kafka real** (10/07, pós-fix do `@Blocking` — extrato cacheado vazio → evento → dado novo em segundos, com o `corr` no log; o TTL de 300s mascarava o bug até então).
   - **Broadcast de invalidação (11/07 — fecha o gap que justificava o 85)**: consumer group único por instância (`${quarkus.uuid}`) — o evento é entregue a TODAS as réplicas, não distribuído entre elas. **Provado ao vivo com 2 instâncias** (compose `--profile escala`): um POST → as duas logam a invalidação → as duas servem o dado novo. A limitação de escala horizontal reduziu-se a eficiência (hits locais por réplica — trade-off documentado na ADR-006, upgrade Redis por medição), não a correção — por isso 100.

5. **Resiliência** — peso 12 · nota proposta **100/100** *(85 → 95 → 100; os dois gaps nomeados foram fechados em 11/07)*
   Evidência:
   - Política em `docs/adr/ADR-007-resiliencia-retry-dlq.md`: 3 retentativas em processo com backoff exponencial (1s×2, jitter) + DLQ com causa nos headers; parâmetros da ata da Sessão 6 (decisão 8), ajustáveis por configuração.
   - Implementação: `@Retry`+`@ExponentialBackoff` nos consumidores (`ConsumidorLancamentos`, `ConsumidorReconsolidacao`); `failure-strategy=dead-letter-queue` (Kafka) e `reject`+`auto-bind-dlq` (Rabbit); `@Timeout` 2s sem retry na chamada interna do cache miss.
   - Fila de trabalho `reconsolidacao` (US-09): aceite imediato + guichê um a um (`max-outstanding-messages=1`); reapuração idempotente por recálculo absoluto.
   - **Teste da banca** (`RetentativaEDlqTest`): falha transitória supera em exatamente 3 tentativas; mensagem envenenada consome 1+3 e o fluxo continua. `ReconsolidacaoTest`: contestação corrigida pela reapuração. Verde no plano B (07/07).
   - **DLQ física validada no plano A (10/07)**: veneno injetado direto no tópico → 1+3 tentativas → `lancamentos-recebidos-dlq` com **mensagem original + causa nos headers** (`dead-letter-reason` com a violação exata, classe da exceção, tópico/partição/offset — o contrato da Sessão 4) e o fluxo seguiu (lançamento válido atrás do veneno incorporado). DLQ do Rabbit (`reconsolidacao-dlq`) visível na management UI.
   - **Régua de veneno em 3 camadas (11/07)**: mensagem **ilegível** → `FalhaDeserializacaoLancamentos` encaminha os bytes crus à DLQ com a causa e libera o fluxo (sem o handler, o poll re-tenta para sempre e a **partição trava** — o 4º bug do plano A, demonstrado e corrigido ao vivo); falha **transitória** → 3× backoff; falha **permanente** → DLQ. Nota na ADR-007; `FalhaDeserializacaoTest` no plano B. Ao todo, **4 bugs reais de fronteira código↔infra** achados e corrigidos na validação com brokers reais — registro em `docs/uso-de-ia.md` (10–11/07).
   - **Disjuntor + última resposta boa no cache miss (11/07)**: `FonteResiliente` com `@Timeout` + `@CircuitBreaker` (nomeado, resetável via `CircuitBreakerMaintenance`) + `@Fallback` que serve a última cópia conhecida — degradação com transparência (US-05), o carimbo (US-07) expõe a idade; sem cópia → **503 com `Retry-After`**, nunca 500 opaco. Provado em `DisjuntorFonteTest` (abre com 4 falhas e **para de chamar a fonte**) e ao vivo no plano A (consolidação derrubada → última-boa/503; religada → normal).
   - **Reprocesso da DLQ automatizado (11/07)**: `reprocessar-dlq.ps1`/`.sh` — um comando, consumer group com memória de offset, seguro pela dedup (ADR-004); ilegível volta à DLQ pelo handler. Fecha o terceiro critério de aceite da US-08 de ponta a ponta.
   - *(Gaps anteriores desta nota — "@CircuitBreaker/@Fallback não exercitados" e "reprocesso manual" — fechados; por isso 100.)*

6. **Testabilidade** — peso 13 · nota proposta **100/100**
   Evidência:
   - `mvn verify -Pplano-b-jvm` verde sem Docker: 5 módulos, **35 testes, 0 falhas** (reverificado 11/07) — e **rodando em CI a cada PR** (`.github/workflows/verify.yml`, selo verde no repo): a mesma condição da correção, provada continuamente.
   - **PACT consulta↔consolidação** (Sessão 6, decisão 2): consumer `ContratoPosicoesConsumerPactTest` (deserializa no record real `PosicaoDaConta`; 2 interações — posições e extrato vazio), pact **em disco, versionado** (`pacts/`), provider `ContratoPosicoesProviderPactTest` verifica contra a aplicação real com estados semeados pelo caminho real. Roda no `mvn verify`, inclusive plano B.
   - **PACT de MENSAGEM do tópico `lancamentos-recebidos`** (o 2º par da Sessão 6, fechado pós-aula-08): consumidor declara o shape mínimo (identidade da ADR-004, tipo, valor, ocorrência — opcionais do erratum #1 fora, de propósito); provider verifica contra a serialização real. Os DOIS estilos de contrato da aula-08 (request/response e mensagem) presentes. Pact em disco × Broker: decisão documentada na ADR-003 (nota de 11/07).
   - Connector in-memory do SmallRye (`RecursosEmMemoria`) substitui Kafka/RabbitMQ nos testes; dublês contáveis provam cache; `@InjectSpy` prova retry.
   - Estratégia dos dois perfis (A alta fidelidade × B Docker-free como gate) documentada em `docs/adr/ADR-003-perfis-de-teste.md`.

7. **Decisões arquiteturais** — peso 13 · nota proposta **100/100**
   Evidência: `docs/adr/` — 7 ADRs, todas com alternativas rejeitadas e o porquê, **com índice navegável** (`docs/adr/README.md`: decisão/US/rejeitadas em 1 linha). **Os 5 candidatos da Sessão 6 estão fechados** (o #1 como ADR-002 no bootstrap; os 4 da issue #7 como ADRs 004–007). As ADRs são vivas: notas de evolução datadas registram o que a prática ensinou (004: expurgo×dedup — US-11 exige US-04; 005: retenção da outbox; 007: a 3ª camada de veneno; 003: pact-em-disco × Broker). Rastreabilidade decisão↔fala de stakeholder via `docs/requisitos/`.

8. **Uso crítico de IA** — peso 5 · nota proposta **100/100**
   Como usamos IA e o que validamos manualmente: ver `docs/uso-de-ia.md` (log contínuo, honesto: inclui o que a IA errou e o que o grupo rejeitou/validou) — inclusive a sessão de 10/07 (README + estas notas propostas), que registra o próprio processo de propor uma nota sem escala oficial disponível.

9. **Execução** — peso 5 · nota proposta **100/100**
   Como rodar: `README.md` §Arquitetura em 30 segundos (visão dos 3 serviços/portas), §Instalar dependências/§Compilar/§Testar/§Rodar em modo dev, e §Testando o fluxo ponta a ponta (roteiro de `curl` completo: health check → `POST /lancamentos` → `GET /extrato` → `atualizar=true` → `POST /reconsolidacoes`) — quem só lê o README consegue subir e exercitar os 3 serviços sem contexto adicional. **Demo do zero com um comando** (`./demo.ps1` → `docker-compose.yml` com Kafka/RabbitMQ/Postgres + os 3 serviços — aceite da issue #9), incluindo roteiro da DLQ ao vivo (§Demo da banca), **coleção Postman versionada** (`postman/` — 13 requests na ordem do roteiro, 22 asserções, validada com Newman) e **CI verde na `main`**. Perfil A (Docker) para a demo, perfil B (pura-JVM) para os testes/CI (§Perfis de execução).

## Opcionais entregues (grupo de 4 → mínimo 1)

- **Observabilidade básica** (logs estruturados + correlação de id entre serviços) — exigência de negócio real (US-12/Sessão 5, LGPD):
  - Correlação ponta a ponta: `X-Correlation-Id` nas bordas HTTP (3 serviços, com eco no response) → header Kafka no tópico de ingestão → **persistido pela outbox** (`correlacao_id`) → header no evento `posicao-atualizada` → logado na invalidação da consulta. Fila de reconsolidação usa a propriedade AMQP. Provado em `CorrelacaoIngestaoTest`/`CorrelacaoFluxoTest`/`CorrelacaoConsultaTest`.
  - Logs JSON (`quarkus-logging-json`) no plano A/prod; console legível com `corr=` em dev/teste. Logs só com identificadores opacos (US-12).
  - Nota técnica de valor (critério 8): o MDC do Quarkus provou-se **não confiável em threads de mensageria** (probe: put+get na mesma thread → null) — consumidores carregam a correlação explicitamente; MDC/`%X` só nas bordas HTTP. Registro completo em `docs/uso-de-ia.md` (07/07).
- **Uso documentado e crítico de IA** (bônus; também evidencia o critério 8) — `docs/uso-de-ia.md`, em andamento desde o início.
