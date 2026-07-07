# CLAUDE.md — Contexto do projeto para sessões de IA

> Leia isto antes de qualquer tarefa. Evita reprocessar o material do curso inteiro.

## O que é

Projeto final do módulo BE-JV-010 (Arquitetura de Software Ágil II — curso Java Especialista, turma Caixa). Tema: **Consolidador de Extrato / Open Finance** — ingestão por tópicos → agregação → cache de consulta. **Banca: 13–14/07/2026 (antecipada; era 15/07 — entrega fechada até 12/07 à noite).** Avaliação por rubrica de 9 critérios (pesos: decomposição 15, assíncrono 15, idempotência 12, resiliência 12, testabilidade 13, ADRs 13, cache 10, IA 5, execução 5) — o `AVALIACAO.md` mapeia critério → evidência e é obrigatório.

## Grupo e papéis

Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato).

## Stack (ADR-001 — não rediscutir sem motivo)

Java 25 LTS · Quarkus **3.33.2** (LTS, BOM `io.quarkus.platform`) · Maven multi-módulo · SmallRye Reactive Messaging (Kafka/RabbitMQ) · SmallRye Fault Tolerance · quarkus-cache/redis-cache · Panache · PACT (Quarkiverse). Equivalências Spring→Quarkus: tabela na ADR-001.

## Estrutura

- `docs/requisitos/` — personas, 6 sessões de elicitação, `user-stories.md` (**fonte de verdade do domínio**; em divergência, transcrições prevalecem; Sessão 6 = refinamento do time e corrige as anteriores).
- `docs/adr/` — decisões. Pendentes mapeadas na Sessão 6: consulta em cache miss, idempotência, consistência dos 3 efeitos, resiliência.
- `docs/arquitetura.md` — contextos + fluxos/garantias.
- `docs/uso-de-ia.md` — **log do critério 8: registre TODA sessão de IA relevante aqui** (o que foi pedido, o que veio errado, o que foi validado à mão).
- `shared-contracts/` — só tipos que cruzam fronteira. `extrato-ingestao/` (8081) · `extrato-consolidacao/` (8082) · `extrato-consulta/` (8083).

## Convenções

- Idioma: **pt-BR** em docs, javadoc e mensagens de commit (código em inglês técnico natural: nomes de domínio em português — `PosicaoConsolidada`, `LancamentoRecebido`).
- Commits: convencionais (`feat:`, `docs:`, `test:`...), granulares, mensagem explica o *porquê*. Commits ao longo das semanas são critério de avaliação — nunca acumular um dump.
- Perfis: `plano-a-docker` (padrão, brokers reais) e `plano-b-jvm` (**`mvn verify -Pplano-b-jvm` tem que passar sem Docker** — critério 6).
- Todo padrão implementado referencia a US e/ou sessão que o justifica (rastreabilidade é o diferencial do projeto).

## Delegação de IA (economia de tokens)

Sessão principal: arquitetura, ADRs, revisão, AVALIACAO.md. Subagentes com spec fechada: implementação de módulos. Explore (read-only): varredura do gabarito/aulas. Verificação: `mvn verify` real, nunca opinião.

## Peculiaridade do ambiente Cowork (sessões do Leo)

O mount do Windows corrompe operações de lock/rename do git. **Não rodar `git commit` direto no mount**: copiar o repo para `/tmp/repo`, commitar lá e sincronizar o `.git` de volta via tar (ver histórico da sessão de bootstrap). Push é sempre feito pelo Leo no Windows.

## Estado atual (atualizar a cada sessão!)

- [x] Pacote de requisitos completo (`docs/requisitos/`, 9 arquivos)
- [x] Repo GitHub: `leofaria-code/consolidador-extrato` (privado)
- [x] Bootstrap multi-módulo: 4 módulos, esqueleto REST+health+teste smoke
- [x] ADR-001 (stack), ADR-002 (decomposição), arquitetura.md, AVALIACAO.md esqueleto
- [x] Board de issues drafado em `docs/board-issues.md` (9 issues, milestone banca-2026-07). **GitHub MCP não autorizado** (autorizar conector engineering:github nas configurações) e sandbox sem rede para api.github.com — criar issues via MCP na próxima sessão ou via bloco `gh` do próprio arquivo.
Cronograma comprimido (banca antecipada):

- [x] 05–06/07 — Incremento 1: tópico de ingestão + consumidor idempotente (Kafka) — concluído (commit 88503a9), issue #1 fechada, `mvn verify -Pplano-b-jvm` verde (7 testes, 0 falhas)
- [x] 07/07 — Incremento 2: consolidação + base segregada + evento posicao-atualizada (branch `feat/inc-2-consolidacao` → PR; ADR-004 idempotência na base, ADR-005 outbox; 11 testes verdes no plano B)
- [x] 07/07 — Incremento 3: cache na consulta + invalidação + carimbo (branch `feat/inc-3-cache-consulta`, empilhada sobre o Inc-2; ADR-006 cache miss; 19 testes verdes no plano B)
- [ ] 09–10/07 — Incremento 4: fila de reconsolidação (RabbitMQ) + retry/DLQ · Incremento 6: observabilidade (logs JSON + correlation id) — opcional obrigatório
- [ ] 11/07 — Incremento 5: contract test PACT consulta↔consolidação
- [ ] 12/07 — AVALIACAO.md preenchido + docker-compose/demo + ensaio da banca
- [ ] 13–14/07 — banca (buffer no dia 13 de manhã se for dia 14)
