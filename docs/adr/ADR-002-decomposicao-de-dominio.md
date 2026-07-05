# ADR-002 — Decomposição: três contextos com bases segregadas

- **Status:** aceita · 05/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo (validada com o arquiteto na Sessão 6 de refinamento)

## Contexto

O pacote de requisitos (`docs/requisitos/`) revela três agrupamentos com vocabulário e ritmo próprios na linguagem ubíqua: **ingestão** (esteira, identidade do lançamento, idempotência, consentimento vigente — Sessão 2), **consolidação** (posição, competência, reconsolidação, expurgo — Sessões 3–5) e **consulta** (cache, carimbo, atualizar sob demanda, trilha de acesso — Sessão 3). Cargas também divergem: ingestão é intensiva em escrita e picos; consulta é intensiva em leitura com exigência de latência; consolidação é o coração transacional.

## Alternativas consideradas

1. **Monolito modular** — mais simples de operar; porém não exercita bases segregadas nem comunicação assíncrona entre serviços (inviabiliza o CORE do projeto) e acopla os perfis de carga divergentes.
2. **Dois serviços** (ingestão+consolidação juntos; consulta separada) — reduz um deploy; porém funde dois vocabulários distintos, mistura o ciclo de vida do consumo da esteira com o núcleo transacional, e enfraquece o corte DDD que os requisitos sugerem.
3. **Três serviços, um por contexto** — espelha a linguagem ubíqua; permite escalar ingestão e consulta de forma independente; cria as fronteiras onde os padrões do módulo (tópico, fila, cache, contrato) existem de verdade.

## Decisão

Três serviços — `extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta` — mais o módulo `shared-contracts` (apenas tipos que **cruzam fronteira**: mensagens, eventos, pedidos).

Regras de fronteira (Sessão 6, decisão 1):

- **Cada serviço tem sua base; nenhum serviço lê a base do outro.** Integração só por mensagem/evento ou API explícita.
- Ingestão → Consolidação: **tópico** de lançamentos (contrato `LancamentoRecebido`).
- Consolidação → mundo: **evento** `PosicaoAtualizadaEvento` (referência, não dado).
- Atendimento/operação → Consolidação: **fila de trabalho** (`PedidoReconsolidacao`).
- Consulta ↔ Consolidação: par HTTP interno — alvo do **contract test** (desenho exato da consulta em cache miss: ADR futura, decisão #2 da Sessão 6).

## Consequências

- (+) Corte rastreável à linguagem ubíqua (critério 1: bounded contexts + bases segregadas + ADR de corte).
- (+) Cada fronteira exercita um padrão avaliado — nada de tecnologia sem propósito de domínio.
- (−) Três deploys e mais configuração local (mitigado por Docker Compose/Dev Services no perfil A).
- (−) Consistência entre serviços passa a ser eventual — tratada explicitamente (premissas da Sessão 6, decisão 3; ADR futura de consistência dos três efeitos).
