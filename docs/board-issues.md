# Board de issues do grupo

> Fonte: cronograma do `CLAUDE.md`. **Banca: 15/07** (antecipação desfeita em 11/07).
> **Issues criadas no GitHub em 05/07 via `gh` CLI.** Status e assignees abaixo conferidos direto
> na API em 10/07 (`gh issue list --json ...`) — não é mais o rascunho inicial do bootstrap.

**Milestone:** [`banca-2026-07`](https://github.com/leofaria-code/consolidador-extrato/milestone/1) · **Labels:** `incremento`, `adr`, `docs`, `p0` (bloqueia banca)

| # | Issue | Título | Status (GitHub, 10/07) | Prazo | Responsável | Critérios da rubrica |
|---|-------|--------|-------------------------|-------|-------------|----------------------|
| 1 | [#1](https://github.com/leofaria-code/consolidador-extrato/issues/1) | Incremento 1 — Tópico de ingestão + consumidor idempotente (Kafka) | ✅ fechada (05/07) | 06/07 | Sandy | assíncrono (15), idempotência (12) |
| 2 | [#2](https://github.com/leofaria-code/consolidador-extrato/issues/2) | Incremento 2 — Consolidação + base segregada + evento `posicao-atualizada` | ✅ fechada (07/07, PR [#10](https://github.com/leofaria-code/consolidador-extrato/pull/10)) | 08/07 | Marcos + Sandy | decomposição (15), assíncrono (15) |
| 3 | [#3](https://github.com/leofaria-code/consolidador-extrato/issues/3) | Incremento 3 — Cache na consulta + invalidação + carimbo | ✅ fechada (07/07, PR [#11](https://github.com/leofaria-code/consolidador-extrato/pull/11)) | 08/07 | Marcos | cache (10) |
| 4 | [#4](https://github.com/leofaria-code/consolidador-extrato/issues/4) | Incremento 4 — Fila de reconsolidação (RabbitMQ) + retry/DLQ | ✅ fechada (07/07, PR [#13](https://github.com/leofaria-code/consolidador-extrato/pull/13)) | 10/07 | Sandy | resiliência (12) |
| 5 | [#5](https://github.com/leofaria-code/consolidador-extrato/issues/5) | Incremento 6 — Observabilidade: logs JSON + correlation id | ✅ fechada (08/07, PR [#14](https://github.com/leofaria-code/consolidador-extrato/pull/14)) | 10/07 | Leo | execução (5), resiliência (12) |
| 6 | [#6](https://github.com/leofaria-code/consolidador-extrato/issues/6) | Incremento 5 — Contract test PACT consulta↔consolidação | ✅ fechada manualmente (10/07) — lição: keyword de auto-close só em inglês; "Fecha #N" não fecha (e "Closes #9?" numa frase fecha sem querer — aconteceu). | 11/07 | Rodrigo | testabilidade (13) |
| 7 | [#7](https://github.com/leofaria-code/consolidador-extrato/issues/7) | ADRs pendentes da Sessão 6 (cache miss, idempotência, consistência dos 3 efeitos, resiliência) | ✅ fechada (07/07) | 09/07 | Leo | ADRs (13) |
| 8 | [#8](https://github.com/leofaria-code/consolidador-extrato/issues/8) | Perfil `plano-b-jvm` — `mvn verify` verde sem Docker | ✅ fechada (10/07, com OK do Rodrigo) — critério satisfeito de forma contínua; hoje provado no CI a cada PR | 11/07 | Rodrigo | testabilidade (13) |
| 9 | [#9](https://github.com/leofaria-code/consolidador-extrato/issues/9) | AVALIACAO.md preenchido + docker-compose/demo + ensaio da banca | ⏳ aberta — notas validadas pelo grupo (100,0) e demo/coleções/CI entregues; **resta só o ensaio cronometrado** | 12/07 | Todos (Leo coordena) | todos os 9 |

## Corpos das issues

### 1. Incremento 1 — Tópico de ingestão + consumidor idempotente (Kafka)
Módulo `extrato-ingestao` publica `LancamentoRecebido` em tópico Kafka; `extrato-consolidacao` consome com garantia de idempotência (chave de deduplicação — ver ADR pendente). Referenciar US e sessão que justificam o padrão. **Aceite:** reentrega da mesma mensagem não duplica efeito; teste cobrindo o caso.

### 2. Incremento 2 — Consolidação + base segregada + evento `posicao-atualizada`
Agregação em `PosicaoConsolidada`, persistida em base própria do módulo (Panache); ao consolidar, publicar evento `posicao-atualizada`. **Aceite:** os 3 efeitos (persistir, publicar, marcar processado) tratados conforme ADR de consistência.

### 3. Incremento 3 — Cache na consulta + invalidação + carimbo
`extrato-consulta` com quarkus-cache/redis-cache; invalidação ao receber `posicao-atualizada`; resposta com carimbo de atualização. Comportamento em cache miss conforme ADR pendente. **Aceite:** hit/miss/invalidação demonstráveis.

### 4. Incremento 4 — Fila de reconsolidação (RabbitMQ) + retry/DLQ
Fila de reconsolidação com SmallRye Fault Tolerance: retry com backoff + DLQ. **Aceite:** falha simulada vai para DLQ após N tentativas; log rastreável.

### 5. Incremento 6 — Observabilidade
Logs JSON estruturados + correlation id propagado pelos 3 módulos e pelas mensagens. **Aceite:** um fluxo completo rastreável por um único id.

### 6. Incremento 5 — Contract test PACT consulta↔consolidação
PACT (Quarkiverse): consulta como consumer, consolidação como provider. **Aceite:** verificação roda no `mvn verify` (inclusive `plano-b-jvm`).

### 7. ADRs pendentes da Sessão 6
Quatro ADRs: consulta em cache miss; idempotência; consistência dos 3 efeitos; resiliência. Cada uma referencia US/sessão. **Aceite:** revisadas pelo grupo antes dos incrementos que dependem delas.

### 8. Perfil `plano-b-jvm`
`mvn verify -Pplano-b-jvm` passa sem Docker (critério 6). **Aceite:** CI/local verde sem daemon Docker.

### 9. Entrega final
AVALIACAO.md completo (critério → evidência), docker-compose de demo, roteiro de apresentação, ensaio. **Aceite:** demo roda do zero com um comando.

## Assignees — resolvido

Sandy (`mycrosan`), Marcos (`marcospaim`) e Rodrigo (`RBisso`) aceitaram o convite de colaborador
e já aparecem como assignee em todas as issues correspondentes (conferido via
`gh api repos/.../collaborators` e `gh issue list --json assignees` em 10/07 — sem convites
pendentes). Nada a fazer aqui.

## Pendências — todas resolvidas

- ~~Fechar a issue #6 manualmente~~ — feito em 10/07. A lição dupla das keywords ficou registrada na
  própria tabela: "Fecha #N" (PT) não fecha; "Closes #9?" no meio de uma frase fecha sem querer.
- ~~Avaliar fechar a issue #8~~ — fechada em 10/07 com OK do Rodrigo; hoje o critério é provado
  continuamente pelo workflow `verify` a cada PR.
