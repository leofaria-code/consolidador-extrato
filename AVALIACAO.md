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
   Evidência (parcial — fila `reconsolidacao` fica para o Inc-4):
   - Tópico `lancamentos-recebidos`: `PublicadorLancamentos` (chave = instituição+agência+conta, ordem por conta) → `ConsumidorLancamentos`.
   - Tópico `posicao-atualizada` (US-10): publicado via outbox (`PublicadorPosicaoAtualizada`), entrega "pelo menos uma vez", só referência — garantias declaradas em `docs/arquitetura.md` §Fluxos e provadas em `FluxoConsolidacaoTest`.

3. **Idempotência e consistência** — _
   Evidência:
   - Chave `IdentidadeLancamento` (instituicaoOrigem + idLancamentoOrigem, `shared-contracts`).
   - **Idempotência na base** (ADR-004): constraint UNIQUE em `lancamento_incorporado` = memória de dedup que não expira; verificação na mesma transação; constraint como última defesa contra corrida.
   - **Consistência dos 3 efeitos** (ADR-005): gravar + consolidar + registrar evento numa transação local (`ServicoConsolidacao`); outbox com marcação pós-ack; análise de queda ponto a ponto na ADR.
   - Testes (US-02): `ConsumidorIdempotenteTest` — 3 reenvios + 1 distinto → 2 incorporados **e** totais de processamento único; `FluxoConsolidacaoTest.repetidoNaoGeraEventoNemRegistroNaOutbox`. Verde em `mvn verify -Pplano-b-jvm` (verificado 07/07).

4. **Cache** — _
   Evidência: _(cache mês corrente; invalidação por evento + TTL; ADR da estratégia; carimbo US-07)_ — TODO.

5. **Resiliência** — _
   Evidência: _(retry 3× backoff exponencial + DLQ; teste falha transitória × permanente)_ — TODO.

6. **Testabilidade** — _
   Evidência (parcial — PACT ainda não implementado):
   - `mvn verify -Pplano-b-jvm` verde sem Docker: 5 módulos, 7 testes, 0 falhas (verificado em 05/07 — critério satisfeito por build real, não por leitura de código).
   - Connector in-memory do SmallRye (`RecursosEmMemoria`) substitui Kafka nos testes de ingestão e consolidação.
   - Estratégia dos dois perfis (A alta fidelidade × B Docker-free como gate) documentada em `docs/adr/ADR-003-perfis-de-teste.md`.
   - Pendente: contract test PACT consulta↔consolidação (Incremento 5, issue #6).

7. **Decisões arquiteturais** — _
   Evidência: `docs/adr/` (ADR-001 stack com alternativas e custos; ADR-002 decomposição; ADR-003 perfis de teste A/B; ADR-004 idempotência na base; ADR-005 outbox transacional — as duas últimas fecham os candidatos #3 e #4 da Sessão 6). Rastreabilidade decisão↔fala de stakeholder via `docs/requisitos/`. Pendentes: consulta em cache miss (Inc-3), resiliência (Inc-4).

8. **Uso crítico de IA** — _
   Como usamos IA e o que validamos manualmente: ver `docs/uso-de-ia.md` (log contínuo, honesto: inclui o que a IA errou e o que o grupo rejeitou/validou).

9. **Execução** — _
   Como rodar: `README.md` §Como rodar (TODO); perfil A declarado, testes no perfil B.

## Opcionais entregues (grupo de 4 → mínimo 1)

- **Observabilidade básica** (logs estruturados + correlação de id entre serviços) — exigência de negócio real (US-12/Sessão 5, LGPD) — TODO.
- **Uso documentado e crítico de IA** (bônus; também evidencia o critério 8) — `docs/uso-de-ia.md`, em andamento desde o início.
