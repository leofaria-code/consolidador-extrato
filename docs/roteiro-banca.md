# Roteiro da banca — 15/07/2026

> **Tempo combinado: 20 min + tolerância de 5 (teto 25).** Ensaiar mirando 20–22 para sobrar respiro. Quem responde o quê: cada um defende o
> critério que implementou — a banca percebe quando só um sabe tudo.
> **Regra de ouro do Fábio (Sessão 6):** a resposta nunca é "porque funcionou" —
> é "consideramos X e Y, escolhemos Y por isso" (toda resposta abaixo aponta a ADR).

## Antes de entrar na sala (checklist)

- [ ] `./demo.ps1` rodado **antes** — a stack leva ~2 min para ficar saudável; conferir `curl http://localhost:808{1,2,3}/q/health`.
- [ ] Portas 8081–8083 e 15672 livres (nenhum `quarkus:dev` esquecido).
- [ ] **Postman aberto com a coleção importada** (`postman/consolidador-extrato.postman_collection.json`) — é o caminho visual dos passos 0–5; fallback: `npx newman run postman/...json` no terminal.
- [ ] Terminal com fonte grande + os comandos deste roteiro prontos para colar (fallback dos passos do Postman e caminho único dos passos 6–7).
- [ ] UI do RabbitMQ logada (`localhost:15672`, guest/guest) numa aba.
- [ ] **Swagger UI aberto** (`:8081|:8082|:8083/q/swagger-ui`) — superfície exploratória para a arguição: "e se mandar X?" se responde ao vivo, sem sair da tela.
- [ ] **Cronometrista definido** (sugestão: Rodrigo até o passo 7b, Marcos depois) — sinais no minuto 10 e no 18.
- [ ] **Plano B da demo** (se o Docker falhar na hora): `mvn verify -Pplano-b-jvm` ao vivo (38 testes, ~1 min, zero infra) + prints da validação de 10/07 no `uso-de-ia.md`. A rubrica pede o gate sem Docker de qualquer forma.

## Sequência da demo (com narrativa)

| # | Quem | O que mostrar | Comando/ação |
|---|------|---------------|--------------|
| 0 | Leo | Arquitetura em 30s: 3 contextos, bases segregadas, "ninguém lê a base do outro" | Diagrama do README |
| 1 | Sandy | **Aceite assíncrono**: POST → 202 + eco do correlation id | POST `/lancamentos` com `X-Correlation-Id: banca-01` (README §fluxo) |
| 1b | Sandy | **Lote + fora de ordem (US-03)**: item de JUNHO no lote reabre a competência antiga — extrato de junho e julho lado a lado | Pasta 6 da coleção Postman |
| 2 | Marcos | **Cache miss → fonte → hit**: 1º GET busca na consolidação, 2º responde da memória; carimbo do DADO | `GET /extrato/cliente-001/2026-07` ×2 |
| 3 | Sandy | **Idempotência**: repetir o MESMO POST → extrato inalterado (US-02: "extrato atrasado e certo > na hora e errado") | mesmo POST do passo 1; GET de novo |
| 4 | Marcos | **Atualizar sob demanda com limite**: 1ª forçada 200, 2ª imediata 429 | `GET ...?atualizar=true` ×2 |
| 5 | Sandy | **Guichê**: POST reconsolidação → 202 imediato → log do guichê com o mesmo corr | POST `/reconsolidacoes`; `docker compose logs consolidacao \| grep <corr>` |
| 6 | Sandy | **Resiliência (o clímax)**: veneno direto no tópico → fluxo NÃO trava → DLQ com mensagem original + causa nos headers | README §Demo da banca (2 comandos); mostrar `reconsolidacao-dlq` na UI do Rabbit |
| 6b | Sandy | **Final feliz do veneno**: reprocesso da DLQ em um comando — corrigidos entram pelo fluxo normal, seguro pela idempotência | `./reprocessar-dlq.ps1` |
| 7 | Leo | **Correlação ponta a ponta**: o mesmo id do POST aparece no log da ingestão, consolidação e invalidação da consulta (JSON) | `docker compose logs \| grep banca-01` |
| 7b | Marcos | **Disjuntor + última resposta boa (promovido a ato principal)**: derrubar a consolidação AO VIVO → miss forçado serve a última cópia com carimbo antigo (transparência); cliente sem cópia → 503 com Retry-After; religar → normaliza | `docker compose stop consolidacao` → GET `?atualizar=true` → GET cliente novo → `docker compose start consolidacao` |
| 8 | Rodrigo | **Testabilidade**: suíte inteira sem Docker + os DOIS contratos PACT (HTTP e mensagem) verificados no build + CI duplo (verify + e2e com Newman e guarda semântico) | `mvn verify -Pplano-b-jvm` (ou output salvo) + `pacts/*.json` + aba Actions com os selos verdes |
| 9 | Leo | **Fechamento — a jornada de engenharia (critério 8 no palco)**: 4 bugs reais que só os brokers de verdade revelaram (DLX, payload do Rabbit, @Blocking, deserialização que TRAVAVA a partição) + o guarda de CI que reprovou o próprio criador na estreia. Mensagem final: "o repositório se auto-fiscaliza — 38 testes, 27 asserções e2e e 2 contratos a cada PR" | 1 min de fala, aba do `uso-de-ia.md` aberta |

**Como executar os passos 0–5 (duas opções):**

- **Visual (recomendada): Run Collection no Postman** — a coleção espelha exatamente esses passos em pastas (`0 · Saúde` … `6 · Domínio: lote e fora de ordem`), com **27 asserções que ficam verdes na tela** enquanto quem está apresentando narra cada pasta. As asserções são relativas ao estado, então pode rodar de novo sem medo (inclusive no ensaio).
- **Terminal (fallback):** os `curl` do README §Testando o fluxo ponta a ponta.

Os passos **6–7 continuam no terminal + UI do Rabbit de propósito**: DLQ e correlação são onde a banca precisa ver o *broker* de verdade (headers de causa no tópico, `x-death` na fila, o mesmo `corr` nos logs JSON dos 3 serviços) — não uma abstração de client HTTP.

## Mapa de tempo (teto 25 — mirar 20–22)

| Minuto | Ato(s) | Quem |
|---|---|---|
| 0–2 | 0 · arquitetura em 30s + o que vão ver | Leo |
| 2–5 | 1, 1b · aceite assíncrono, lote/fora de ordem | Sandy |
| 5–8 | 2, 4 · cache miss→hit, carimbo, 429 | Marcos |
| 8–10 | 3, 5 · idempotência, guichê | Sandy |
| **10** | ☑️ *checkpoint: começar o veneno agora* | |
| 10–14 | 6, 6b · veneno → DLQ → reprocesso (clímax 1) | Sandy |
| 14–16 | 7 · correlação ponta a ponta | Leo |
| 16–18 | 7b · disjuntor + última-boa (clímax 2) | Marcos |
| **18** | ☑️ *checkpoint: Rodrigo assume* | |
| 18–21 | 8 · testes, PACT ×2, CI duplo | Rodrigo |
| 21–22 | 9 · fechamento: a jornada | Leo |
| 22–25 | respiro/transição para a arguição | — |

*Se o checkpoint dos 10 min estourar: cortar o passo 4 (429) e o 6b (reprocesso) — são os de menor peso na rubrica; nunca cortar 6, 7b nem 8.*

## Ato bônus (só se perguntarem — já validado ao vivo)

- **Escala horizontal**: `docker compose --profile escala up -d consulta-replica` → réplica na 8084; um POST invalida as DUAS (broadcast — ADR-006 nota). *Se pretenderem usar, subir a réplica ANTES da sala (leva ~40s).*

## Arguição — perguntas prováveis × resposta curta (e onde está escrito)

1. **"Por que três serviços e não um monolito modular?"** (decomposição, peso 15)
   Três vocabulários e ritmos distintos na linguagem ubíqua (esteira/posição/cache); monolito não exercitaria bases segregadas nem mensageria entre serviços. Corte validado com o arquiteto na Sessão 6. → ADR-002.

2. **"Por que tópico para lançamentos e fila para reconsolidação?"** (assíncrono, peso 15)
   Semânticas diferentes: lançamentos são publica-assina (quem publica não conhece quem consome; ordem por conta via chave de partição); reconsolidação é fila de trabalho — o "guichê", um a um, com aceite imediato. → arquitetura.md §Fluxos.

3. **"Como vocês lembram do que já foi processado? Isso não cresce para sempre?"** (idempotência, peso 12)
   O lançamento incorporado JÁ fica guardado com a identidade — unicidade na base é memória de dedup que não expira, sem estrutura extra. Janela separada foi rejeitada: teria que responder o que fazer com repetido pós-janela. → ADR-004 (dica do próprio Fábio na Sessão 6).

4. **"E se o serviço cair entre gravar e publicar o evento?"** (consistência)
   Outbox transacional: o evento pendente entra NA MESMA transação dos outros dois efeitos; um scheduler publica depois ("pelo menos uma vez" — pode atrasar/repetir, premissa aceita em ata; consumidores idempotentes). Sem transação distribuída de propósito. → ADR-005.

5. **"Por que 3 retentativas? E por que retry em processo, não do broker?"** (resiliência, peso 12)
   3× backoff exponencial é a ata da Sessão 6 (decisão 8), ajustável por config — "e se não bastar?" é deploy de config. Em processo preserva a ordem por conta e dá intervalos crescentes reais; broker fica como camada de entrega. Régua única: toda falha ganha 3 chances; a que persiste vai à DLQ com a causa nos headers — **demonstrado ao vivo no passo 6**. → ADR-007.

6. **"Cache: por que não uma réplica própria na consulta? Por que não Redis? E se escalar?"** (cache, peso 10)
   O evento carrega só referência (minimização/LGPD) — a réplica exigiria chamar a consolidação a cada evento de qualquer forma: mais complexa sem eliminar o acoplamento. A dependência do miss fica governada pelo PACT. Em escala horizontal, a invalidação é **broadcast** (consumer group por instância — demonstrável com `--profile escala`: 2 réplicas, um POST, as duas invalidam); o que fica local é o hit — eficiência, não correção; Redis é upgrade por medição. TTL 5min = meta de frescor da US-05. E se a consolidação cair no miss? Disjuntor + **última resposta boa** com o carimbo expondo a idade; sem cópia, 503 com Retry-After. → ADR-006 (+notas) e ADR-007.

7. **"Por que Quarkus se o curso é em Spring? Cadê o @RetryableTopic?"**
   Decisão estratégica (adoção na Caixa + próximo módulo); os PADRÕES são os mesmos e a tradução foi parte do aprendizado. `@RetryableTopic` vira DUAS camadas no Quarkus: `@Retry` (política de tentativa) + `failure-strategy` (destino da falha) — separação que até ajuda: são decisões independentes. → ADR-001 (tabela de equivalências).

8. **"O que a IA errou? Como vocês sabem que o código é de vocês?"** (IA, peso 5)
   Log honesto no `uso-de-ia.md`: a IA propôs Spring (rejeitamos com contexto), errou versão do quarkus-pact (Central corrigiu), e o MDC "de manual" falhou em thread de mensageria — provado por probe, resolvido com correlação explícita. Regra do grupo: **o build é o árbitro, nunca a opinião do modelo**. E o plano A achou 4 bugs que os testes não pegariam — inclusive um que travava a partição —, e até o guarda de CI reprovou o próprio artefato na estreia (não-determinismo do gerador): está tudo lá, com data e diagnóstico.

9. **"O que quebra se alguém mudar o endpoint interno?"** (testabilidade, peso 13)
   O provider reproduz o pact versionado (`pacts/`) contra a aplicação real no `mvn verify` — mudança incompatível quebra o build da consolidação antes de quebrar a consulta em produção; a mudança de contrato aparece como diff em PR. → Inc-5, ADR-006.

10. **"Os testes passam sem Docker — então para que os brokers?"**
    Plano B prova domínio e fiação (o gate, critério 6); plano A prova o que só broker real prova: partição, DLQ física, binds. E pagou o aluguel: 4 bugs reais (DLX não declarado; conversão de payload do Rabbit; @Blocking no consumidor de eventos; deserialização de lixo travando a partição). → ADR-003 + uso-de-ia 10/07. O CI roda o plano B a cada PR — mesma condição da correção.

11. **"Cadê a US-04 (consentimento) e a US-11 (expurgo)?"**
    Corte de escopo consciente, não esquecimento: a Sessão 6 (decisão 6) fixou que consentimento é **externo** — o sistema *reage* a eventos de uma plataforma simulada, não gere ciclo de vida. Priorizamos os padrões que a rubrica avalia; US-04/US-11 estão rastreadas como próximos incrementos nos properties de cada serviço, e a parte **difícil** delas já está pensada e escrita: a interação expurgo × memória de dedup (nota de 11/07 na ADR-004 — expurgo apaga a dedup junto, então US-11 exige US-04 antes; reenvio pós-expurgo é bloqueado na ingestão, não na idempotência).

## Distribuição sugerida da arguição

| Tema | Titular | Backup |
|---|---|---|
| Decomposição, ADRs, correlação | Leo | Rodrigo |
| Mensageria, idempotência, resiliência/DLQ | Sandy | Leo |
| Cache, base segregada, consulta | Marcos | Leo |
| Testes, PACT, planos A/B | Rodrigo | Sandy |

## Pós-ensaio (preencher no ensaio de 12/07)

- [ ] Tempo total medido: ____ min (alvo 20–22; teto 25)
- [ ] Checkpoints respeitados? min 10: ____ · min 18: ____
- [ ] Passo mais lento da demo: ____________
- [ ] Pergunta que travou alguém: ____________ → reforçar resposta
