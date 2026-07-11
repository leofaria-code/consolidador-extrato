# ADR-007 — Resiliência: 3 retentativas em processo com backoff exponencial + DLQ inspecionável

- **Status:** aceita · 07/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** ADR candidato #5 da Sessão 6 (dúvida da Sandy: "quantas tentativas, quais intervalos?" — negócio não tem número; decisão técnica documentada do time, com ponto de partida proposto em ata: 3× backoff exponencial, configurável)
- **Relaciona-se com:** ADR-004 (a idempotência é o que torna o reprocesso da DLQ seguro), ADR-001 (tradução `@RetryableTopic` → failure-strategy + SmallRye Fault Tolerance), ADR-006 (timeout da chamada interna consulta→consolidação)

## Contexto

A US-08 fixa o comportamento, não os números: falha **temporária** → retentar com intervalos crescentes, sem descartar; falha **permanente** (mensagem envenenada) → fila morta inspecionável após esgotar as tentativas, **sem travar o fluxo principal e sem descarte silencioso**; mensagem da DLQ corrigida → reprocesso pelo fluxo normal. A Sessão 4 acrescenta o contrato da DLQ ("mensagem original + causa") e a Sessão 6 (decisão 8) registra o ponto de partida: **3 retentativas com backoff exponencial, ajustável por configuração**.

Superfícies que precisam da política: (a) consumidor Kafka `lancamentos-in` (Inc-1/2); (b) fila RabbitMQ `reconsolidacao` (Inc-4); (c) chamada HTTP interna consulta→consolidação no cache miss (ADR-006, mitigação prometida).

## Alternativas consideradas

1. **Retry infinito, sem DLQ** — nada se perde, mas uma mensagem envenenada trava a partição para sempre (viola "o fluxo continua" da US-08). Descartada.
2. **Descarte após N tentativas, sem DLQ** — fluxo nunca trava, mas é descarte silencioso — a proibição mais explícita da US-08 ("o extrato nunca minta por omissão"). Descartada.
3. **Reentrega pelo broker (nack + re-poll) como mecanismo de retry** — terceiriza o backoff ao broker; no Kafka, nack sem estratégia reprocessa imediatamente (sem intervalo crescente) e pode causar tempestade de reentrega em rebalance. O broker fica como *camada de entrega*, não de política. Descartada como mecanismo primário.
4. **Retry em processo (SmallRye Fault Tolerance) + DLQ do connector ao esgotar** — política explícita no código/config: `@Retry` com backoff exponencial dentro do consumidor; esgotadas as tentativas, a `failure-strategy=dead-letter-queue` do connector publica no tópico DLQ com a causa nos headers. **Escolhida.**

## Decisão

**Alternativa 4**, com os parâmetros da Sessão 6 como padrão e tudo ajustável por configuração (sem recompilar):

| Parâmetro | Padrão | Racional |
|---|---|---|
| Retentativas | **3** | Ata da Sessão 6 (decisão 8) |
| Backoff | **exponencial, base 1s, fator 2** (1s → 2s → 4s), com jitter | "Intervalos crescentes" da US-08; jitter evita sincronização de retries |
| Após esgotar | **DLQ** (`lancamentos-recebidos-dlq`; na fila Rabbit, dead-letter exchange) | US-08: guardada e inspecionável, fluxo segue |
| Conteúdo na DLQ | mensagem original + causa/exceção nos headers | Contrato da Sessão 4; o connector Kafka já grava `dead-letter-reason`/`dead-letter-cause` |
| Reprocesso da DLQ | republicar no tópico original | Entra pelo fluxo normal; **seguro por construção** — a dedup da ADR-004 absorve o que já tinha sido processado |
| Chamada interna (cache miss, ADR-006) | `@Timeout` 2s; sem retry automático | Miss com consolidação fora → erro claro e rápido (transparência da US-05/07); hits continuam servindo do cache; retry aqui só amplificaria a carga sobre um serviço já em dificuldade |
| Fila `reconsolidacao` (Inc-4) | mesmos 3×/backoff; consumo **um a um** | O "guichê" da Sessão 4 — pedidos não competem com a consulta em produção |

**Distinção temporária × permanente é comportamental, não classificatória:** não tentamos adivinhar a natureza da falha — toda falha ganha 3 chances com intervalos crescentes (temporárias tipicamente curam); a que persiste é tratada como permanente e vai à DLQ. Essa régua única é o que o teste da banca demonstra: falha nas 2 primeiras tentativas → sucesso na 3ª, sem DLQ; falha em todas → DLQ com causa, próximo lançamento segue processando.

## Consequências

- (+) Mensagem envenenada custa no máximo ~7s de atraso na partição (1+2+4) e sai do caminho — o fluxo principal continua (US-08).
- (+) Parâmetros rastreáveis a ata (Sessão 6, decisão 8) e ajustáveis por configuração — a pergunta "por que 3?" tem resposta documental, e a resposta a "e se 3 não bastar?" é um deploy de config, não de código.
- (+) Reprocesso da DLQ não exige cuidado especial do operador: idempotência (ADR-004) torna o reenvio em lote seguro — é exatamente o cenário "reprocessamento de lote" da US-02.
- (−) Retry em processo segura o consumidor da partição durante o backoff (~7s no pior caso). *Aceito:* preserva a ordem por conta (chave de partição) — processar a próxima antes de desistir da atual reordenaria a conta; e o guichê da reconsolidação é sequencial por definição.
- (−) DLQ exige operação (alguém precisa olhar). *Mitigação:* Inc-6 (observabilidade) loga o encaminhamento à DLQ com correlation id; a inspeção/reprocesso é demonstrada no roteiro da banca.
- (−) `@Timeout` sem retry na consulta significa que um miss durante indisponibilidade da consolidação vira erro para o cliente. *Aceito e transparente:* o caso típico é hit; a US-05 prevê degradação com transparência, e retry automático aqui pioraria a indisponibilidade (amplificação de carga).

## Nota (11/07): falha de deserialização é uma classe própria de veneno

A política acima cobre falhas de **processamento** — a mensagem existe e o método falha. Mensagem **ilegível** (bytes que nem viram objeto) falha *antes*: no deserializer do connector, onde retry/failure-strategy não alcançam. Validado no plano A: sem tratamento, o connector re-tenta o poll para sempre e a **partição trava** — o lançamento válido atrás do lixo nunca processa (violação da US-08 que os 30+ testes verdes não viam). Tratamento: `DeserializationFailureHandler` que encaminha os **bytes crus** à mesma DLQ com a causa nos headers (sem descarte silencioso — Sessão 4) e libera o consumo; o consumidor confirma o payload nulo e segue. Régua completa, agora em três camadas: ilegível → handler → DLQ; falha transitória → 3× backoff; falha permanente → DLQ. Prova: `FalhaDeserializacaoTest` (plano B, o handler em si) + demo do plano A (partição travada → drenada).
