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
   Evidência: _(tópico `lancamentos-recebidos` + fila `reconsolidacao`; garantia declarada em docs/arquitetura.md §Fluxos)_ — TODO código.

3. **Idempotência e consistência** — _
   Evidência (parcial — mecanismo de idempotência entregue; consistência dos 3 efeitos aguarda Inc-2/ADR):
   - Chave `IdentidadeLancamento` (instituicaoOrigem + idLancamentoOrigem, `shared-contracts`).
   - `GuardaIdempotenciaEmMemoria` (`Set#add` atômico) — provisória até a base segregada assumir (Inc-2, ADR candidato #3 da Sessão 6).
   - `ConsumidorIdempotenteTest.reprocessarAMesmaMensagemNaoDuplica` (US-02): 3 reenvios do mesmo lançamento + 1 distinto → exatamente 2 incorporados. Verde em `mvn verify -Pplano-b-jvm` (commit 88503a9, verificado 05/07).
   - Pendente: ADR de idempotência (unicidade na base × janela de deduplicação) e ADR de consistência dos 3 efeitos.

4. **Cache** — _
   Evidência: _(cache mês corrente; invalidação por evento + TTL; ADR da estratégia; carimbo US-07)_ — TODO.

5. **Resiliência** — _
   Evidência: _(retry 3× backoff exponencial + DLQ; teste falha transitória × permanente)_ — TODO.

6. **Testabilidade** — _
   Evidência (parcial — PACT ainda não implementado):
   - `mvn verify -Pplano-b-jvm` verde sem Docker: 5 módulos, 7 testes, 0 falhas (verificado em 05/07 — critério satisfeito por build real, não por leitura de código).
   - Connector in-memory do SmallRye (`RecursosEmMemoria`) substitui Kafka nos testes de ingestão e consolidação.
   - Pendente: contract test PACT consulta↔consolidação (Incremento 5, issue #6).

7. **Decisões arquiteturais** — _
   Evidência: `docs/adr/` (ADR-001 stack com alternativas e custos; ADR-002 decomposição; pendentes mapeadas na Sessão 6 → ADRs futuras). Rastreabilidade decisão↔fala de stakeholder via `docs/requisitos/`.

8. **Uso crítico de IA** — _
   Como usamos IA e o que validamos manualmente: ver `docs/uso-de-ia.md` (log contínuo, honesto: inclui o que a IA errou e o que o grupo rejeitou/validou).

9. **Execução** — _
   Como rodar: `README.md` §Como rodar (TODO); perfil A declarado, testes no perfil B.

## Opcionais entregues (grupo de 4 → mínimo 1)

- **Observabilidade básica** (logs estruturados + correlação de id entre serviços) — exigência de negócio real (US-12/Sessão 5, LGPD) — TODO.
- **Uso documentado e crítico de IA** (bônus; também evidencia o critério 8) — `docs/uso-de-ia.md`, em andamento desde o início.
