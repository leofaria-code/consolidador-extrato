# ADR-006 — Consulta em cache miss: chamada interna à consolidação, cache Caffeine, TTL como salvaguarda

- **Status:** aceita · 07/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** ADR candidato #2 da Sessão 6 (dúvida do Marcos: "a consulta tem base própria com uma cópia, ou busca na consolidação quando o cache falha?")
- **Relaciona-se com:** ADR-002 (regra "ninguém lê a base do outro" — a chamada é pela porta da frente), ADR-005 (o evento que invalida o cache vem da outbox), Sessão 6 decisão 2 (par do contract test)

## Contexto

A consulta serve o extrato consolidado, mas a posição **mora** na base da consolidação — e a ADR-002 proíbe ler a base do outro. O Fábio deixou os dois desenhos como defensáveis: *"réplica própria alimentada por evento é mais desacoplada e mais complexa; cache + chamada interna à consolidação é mais simples e cria uma dependência síncrona no cache miss. Escolham, meçam o trade-off e escrevam o porquê."*

Requisitos que pesam: resposta em fração de segundo no caso típico (US-06), cache-first com invalidação imediata por evento + TTL de salvaguarda (US-06), carimbo do **dado** (US-07/Sessão 6 decisão 4), atualizar sob demanda com limite por cliente (Sessão 6 decisão 5), frescor < 5 min (US-05).

## Alternativas consideradas

1. **Réplica própria na consulta, alimentada por evento** — máximo desacoplamento em runtime (consulta serve mesmo com a consolidação fora). Porém o argumento decisivo é estrutural: o evento `posicao-atualizada` carrega **só referência, sem dado** (minimização — US-10/Sessão 5). Para popular a réplica, a consulta teria de **buscar a posição na consolidação de qualquer forma** a cada evento — ou seja, a dependência da API interna não desaparece, apenas muda de "no miss" para "em todo evento". Alternativa: engordar o evento com os totais — violaria a minimização decidida com a Dra. Patrícia. Resta então réplica = cache + chamada + uma base a mais + reconciliação de divergência. Estritamente mais complexa, sem eliminar o acoplamento.
2. **Cache + chamada interna no miss** — a consulta mantém um cache das posições consultadas; no miss, busca na consolidação pela **API explícita** (porta da frente, ADR-002) e popula o cache. Dependência síncrona no miss é o custo — mitigada porque (a) o caso típico é hit (mês corrente, invalidação cirúrgica por evento), e (b) o par HTTP consulta↔consolidação é exatamente o que a Sessão 6 (decisão 2) elegeu como alvo do **contract test** — o acoplamento fica governado por contrato verificado no build (Inc-5/PACT).
3. **Cache compartilhado (Redis) escrito pela consolidação** — a consolidação escreveria as posições num Redis lido pela consulta. Rejeitada: é "ler a base do outro" com outro nome — o Redis viraria uma base compartilhada implícita, sem contrato.

## Decisão

**Alternativa 2.** Desenho:

- **Cache em processo (Caffeine, `quarkus-cache`)**, nome `extrato-consolidado`, chave = (cliente × competência). **TTL (expire-after-write) = 5 min** — igual à meta de frescor da US-05: mesmo que uma invalidação se perca, o dado nunca fica mais velho que a meta.
- **Invalidação por evento**: a consulta consome o tópico `posicao-atualizada` e invalida a entrada (cliente × competência) do evento. Consumidor **naturalmente idempotente** (invalidar duas vezes = invalidar uma) — cumpre a premissa da Sessão 6, decisão 3.
- **Miss** → GET interno `/interno/posicoes` da consolidação (contrato `PosicaoDaConta` em `shared-contracts`), popula o cache, responde.
- **Carimbo**: cada posição carrega `atualizadoEm` **do dado** (vindo da base da consolidação); o extrato exibe o carimbo mais recente. Nunca a hora em que o cache foi populado (Sessão 6, decisão 4).
- **Atualizar sob demanda (US-07)**: `?atualizar=true` invalida a entrada e recarrega do banco — com **limite por cliente** (intervalo mínimo configurável, padrão 30s; excedente recebe 429). Sessão 6, decisão 5.
- **Multi-instância**: Caffeine é local por instância; com N instâncias, cada uma invalida a sua (todas assinam o tópico) e o TTL cobre janelas de rebalance. Se a consulta escalar horizontalmente com exigência de hit compartilhado, `quarkus-redis-cache` é o upgrade documentado — a anotação `@CacheResult` não muda.

## Nota (11/07): broadcast de invalidação — correção em escala horizontal

O "cada uma invalida a sua" acima virou implementação: o consumer group do canal `posicao-atualizada-in` é **único por instância** (`${quarkus.uuid}`) — o evento deixa de ser *distribuído entre* as réplicas (semântica de fila) e passa a ser **entregue a todas** (semântica de broadcast, que é o correto para invalidação). `auto.offset.reset=latest`: instância recém-nascida tem cache frio, invalidar histórico não lhe diz nada; evento perdido enquanto estava fora é coberto pelo TTL. **Provado ao vivo com 2 instâncias** (compose `--profile escala`): um POST → as duas logam a invalidação → as duas servem o dado novo. Com isso, a limitação de escala horizontal se reduz a **eficiência** (hits não compartilhados entre réplicas — cada uma aquece o seu cache), não a **correção**; compartilhar hits segue sendo o upgrade Redis documentado acima, a adotar por medição, não por precaução.

## Consequências

- (+) O par HTTP mais estável do sistema vira contrato verificado (PACT, Inc-5) — o custo da dependência síncrona é pago com governança, não com esperança.
- (+) Sem segunda base, sem reconciliação, sem evento gordo: a minimização da US-10 permanece intacta.
- (+) Hit típico responde da memória (fração de segundo — US-06); TTL = meta de frescor dá teto determinístico para dado velho.
- (−) No miss com a consolidação **fora**, a consulta degrada (erro no miss; hits continuam servindo). *Mitigação:* timeout/fallback do cliente HTTP entram com o SmallRye Fault Tolerance no Inc-4 (ADR de resiliência); a US-05 já prevê degradação com transparência via carimbo.
- (−) Cache local não compartilha hits entre instâncias. *Aceito no MVP* (1 instância por serviço na demo); upgrade Redis documentado acima.
- (−) Rajada de eventos para o mesmo cliente causa invalidações repetidas e mais misses. *Aceito:* invalidação é barata e o caso raro; medir antes de otimizar (nota do Marcos: a medição dos dois desenhos virou análise estrutural — a réplica exige a mesma chamada HTTP, então a comparação de latência se reduz ao hit ratio do cache, que o TTL+invalidação já otimiza).
