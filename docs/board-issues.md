# Board de issues do grupo

> Fonte: cronograma comprimido do `CLAUDE.md` (banca antecipada 13–14/07, entrega 12/07 à noite).
> **Issues criadas no GitHub em 05/07 via `gh` CLI.** Sandy/Marcos/Rodrigo ainda sem assignee —
> aguardando usernames reais (ver nota de cada issue); rodar `gh issue edit <n> --add-assignee <user>`
> assim que chegarem.

**Milestone:** [`banca-2026-07`](https://github.com/leofaria-code/consolidador-extrato/milestone/1) · **Labels:** `incremento`, `adr`, `docs`, `p0` (bloqueia banca)

| # | Issue | Título | Prazo | Responsável | Critérios da rubrica |
|---|-------|--------|-------|-------------|----------------------|
| 1 | [#1](https://github.com/leofaria-code/consolidador-extrato/issues/1) ✅ fechada | Incremento 1 — Tópico de ingestão + consumidor idempotente (Kafka) | 06/07 | Sandy | assíncrono (15), idempotência (12) |
| 2 | [#2](https://github.com/leofaria-code/consolidador-extrato/issues/2) | Incremento 2 — Consolidação + base segregada + evento `posicao-atualizada` | 08/07 | Marcos + Sandy | decomposição (15), assíncrono (15) |
| 3 | [#3](https://github.com/leofaria-code/consolidador-extrato/issues/3) | Incremento 3 — Cache na consulta + invalidação + carimbo | 08/07 | Marcos | cache (10) |
| 4 | [#4](https://github.com/leofaria-code/consolidador-extrato/issues/4) | Incremento 4 — Fila de reconsolidação (RabbitMQ) + retry/DLQ | 10/07 | Sandy | resiliência (12) |
| 5 | [#5](https://github.com/leofaria-code/consolidador-extrato/issues/5) | Incremento 6 — Observabilidade: logs JSON + correlation id | 10/07 | Leo | execução (5), resiliência (12) |
| 6 | [#6](https://github.com/leofaria-code/consolidador-extrato/issues/6) | Incremento 5 — Contract test PACT consulta↔consolidação | 11/07 | Rodrigo | testabilidade (13) |
| 7 | [#7](https://github.com/leofaria-code/consolidador-extrato/issues/7) | ADRs pendentes da Sessão 6 (cache miss, idempotência, consistência dos 3 efeitos, resiliência) | 09/07 | Leo | ADRs (13) |
| 8 | [#8](https://github.com/leofaria-code/consolidador-extrato/issues/8) | Perfil `plano-b-jvm` — `mvn verify` verde sem Docker | 11/07 | Rodrigo | testabilidade (13) |
| 9 | [#9](https://github.com/leofaria-code/consolidador-extrato/issues/9) | AVALIACAO.md preenchido + docker-compose/demo + ensaio da banca | 12/07 | Todos (Leo coordena) | todos os 9 |

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

## Pendência: atribuir Sandy/Marcos/Rodrigo

Usernames confirmados — Sandy: `mycrosan` · Marcos: `marcospaim` · Rodrigo: `RBisso`.
Convites de colaborador (`write`) já enviados em 05/07 pelo Leo, **ainda pendentes de aceite**
(GitHub só permite `--add-assignee` depois que o convidado aceita — tentativa em 05/07 falhou
com "not found" por isso, não por username errado).

Assim que cada um aceitar, rodar:

```bash
REPO=leofaria-code/consolidador-extrato
gh issue edit 1 -R $REPO --add-assignee mycrosan     # já fechada, mas mantém rastreabilidade
gh issue edit 2 -R $REPO --add-assignee marcospaim,mycrosan
gh issue edit 3 -R $REPO --add-assignee marcospaim
gh issue edit 4 -R $REPO --add-assignee mycrosan
gh issue edit 6 -R $REPO --add-assignee RBisso
gh issue edit 8 -R $REPO --add-assignee RBisso
```

Conferir aceite: `gh api repos/$REPO/invitations` (some da lista quando aceito) ou
`gh api repos/$REPO/collaborators --jq '.[].login'` (aparece na lista quando aceito).
