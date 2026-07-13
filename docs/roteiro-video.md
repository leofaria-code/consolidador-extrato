# Roteiro do vídeo — demo gravada (Plano C de fallback)

> Companheiro do [`roteiro-banca.md`](roteiro-banca.md). Este vídeo é a **rede de segurança da demo ao vivo**: se a máquina corporativa da Caixa travar Docker, portas ou algum recurso, você projeta o vídeo em vez de depurar política de rede na frente da banca.

## Onde ele entra na escada de fallback

1. **Plano A** — demo ao vivo (`./demo.ps1`, stack completa). Mais impressionante; continua sendo o alvo.
2. **Plano B** — `mvn verify -Pplano-b-jvm` ao vivo (41 testes, **sem Docker, sem portas**, ~1 min). O fallback ao vivo mais difícil de bloquear.
3. **Plano C** — **este vídeo**: a demo completa já gravada, rodando de verdade.
4. **Plano D** — prints-chave nos slides (nunca falham em exibir).

## Alvo do vídeo

- **Duração:** ~5 min (teto 6). Cobre só os atos que precisam do stack rodando (1–8); a arguição é Q&A ao vivo e fica de fora.
- **Legendado (burned-in):** funciona mudo — a sala pode estar sem áudio, ou você narra ao vivo por cima.
- **Gravado ato por ato** em clipes separados e depois costurado: um erro re-grava só aquele ato.

## Pré-produção (antes de gravar)

- [ ] `./demo.ps1` rodado e **pré-aquecido** (dispare um lançamento antes de gravar — esquenta o JIT e evita o timeout de stack fria). Cortar os ~2 min de subida.
- [ ] 4/4 serviços saudáveis; Postman com a coleção importada; UI do RabbitMQ logada (`localhost:15672`); Grafana aberto (`localhost:3000`); aba Actions do GitHub com os badges verdes.
- [ ] **Comandos prontos para COLAR** (não digitar ao vivo — mais limpo e rápido).
- [ ] Terminal com **fonte grande**, 1080p, tema de alto contraste; notificações e abas irrelevantes fechadas.
- [ ] Ferramenta de captura: **OBS** (melhor), Win+G (Game Bar, nativo), ou a gravação de tela do PowerPoint.

## Shot-list

| # | Cena / tela | Ação | Legenda na tela | ~s |
|---|---|---|---|---|
| 0 | **Card de abertura** = o pôster (`docs/stack-poster.html`) | estático | "Consolidador de Extrato · Open Finance — demo" | 8 |
| 1 | Postman/terminal | POST `/lancamentos` com `X-Correlation-Id: banca-01` → **202** + eco do id | "Aceite assíncrono — 202 na hora, processa depois" | 30 |
| 1b | Postman pasta 6 | POST lote julho + **junho** → GET junho e julho lado a lado | "US-03: item de junho reabre a competência antiga" | 25 |
| 2 | Terminal/Postman | `GET /extrato` ×2 (miss → fonte, hit → memória) | "miss → fonte (PACT) · hit → cache · carimbo = idade do dado" | 25 |
| 3 | Terminal/Postman | repetir o **MESMO** POST → GET de novo | "Mesmo lançamento 2× — saldo NÃO muda. Dedup na base (ADR-004)" | 25 |
| 4 | Terminal/Postman | `GET ?atualizar=true` ×2 → 200, depois **429** | "Releitura forçada; 2ª imediata → 429 (limite por cliente)" | 15 |
| 5 | Terminal | POST `/reconsolidacoes` → 202 → log do guichê | "Fila RabbitMQ — aceite imediato, processa um a um (o guichê)" | 20 |
| **6** | **split: terminal + dashboard Grafana** | veneno no tópico → lançamento normal ENTRA → DLQ com a causa nos headers + `reconsolidacao-dlq` na UI do Rabbit | "Falha permanente → DLQ com a causa. A partição NÃO trava" + **painel DLQ fica vermelho ao vivo** | 50 |
| 6b | Terminal | `./reprocessar-dlq.ps1` | "Reprocesso em 1 comando — seguro pela idempotência" | 20 |
| 7 | Terminal | `docker compose logs \| grep banca-01` | "O mesmo id atravessa HTTP → Kafka → outbox → invalidação" | 25 |
| **7b** | **split: terminal + dashboard** | `stop consolidacao` → GET força (última-boa, carimbo antigo) → GET cliente novo (**503 + Retry-After**) → `start` normaliza | "Fonte cai: serve a última boa com a idade exposta; sem cópia, 503 honesto — nunca 500 opaco" + **painel disjuntor mexe** | 50 |
| 8 | Terminal + aba Actions | `mvn verify -Pplano-b-jvm` (acelerado) + `pacts/*.json` + os **badges verdes** | "41 testes sem Docker · 3 contratos PACT · CI duplo verde a cada PR" | 30 |
| 9 | **Card final** = o pôster + URLs | estático | "O repo se auto-fiscaliza. Público · imagens no Docker Hub · pôster no Pages" | 15 |

**Total ≈ 5 min.**

## Notas de produção

- **Os atos 6 e 7b são o clímax** — dê tempo (deixe ~2s no resultado antes de cortar) e mostre a **reação do dashboard** em split-screen. É onde o assíncrono (peso 15) e a resiliência (peso 12) se defendem sozinhos: "a idempotência não é uma linha de log — é uma série temporal".
- **Legendas queimadas** garantem que o vídeo se explique sem áudio.
- **Export:** MP4 **H.264 1080p** (o codec mais compatível — toca até em máquina travada); arquivo **< ~100 MB** para mover fácil.
- **Não digite comandos ao vivo** — cole. Menos erro, ritmo melhor.

## Entrega — três vetores (a máquina que bloqueia Docker pode bloquear YouTube/USB também)

1. No **seu próprio notebook** (se puderem usar na sala).
2. Subido num **Drive / YouTube não listado**.
3. Como **arquivo MP4 local** (pendrive + já copiado para a máquina, se permitido).

## Prints-chave (Plano D — o fallback do fallback)

Capturar os estados finais dos momentos que mais importam, para embutir nos slides:
- Ato 1: o `202` com o correlation id ecoado.
- Ato 6: a DLQ com a causa nos headers **e** o painel DLQ vermelho no Grafana.
- Ato 7: o mesmo `banca-01` nos logs JSON dos 3 serviços.
- Ato 7b: o `503 + Retry-After` **e** o painel do disjuntor.
- Ato 8: os badges verdes (verify + e2e) e a saída de `mvn verify` com 41 testes.
