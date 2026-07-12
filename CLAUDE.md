# CLAUDE.md — Contexto do projeto para sessões de IA

> Leia isto antes de qualquer tarefa. Evita reprocessar o material do curso inteiro.

## O que é

Projeto final do módulo BE-JV-010 (Arquitetura de Software Ágil II — curso Java Especialista, turma Caixa). Tema: **Consolidador de Extrato / Open Finance** — ingestão por tópicos → agregação → cache de consulta. **Banca: 15/07/2026 (voltou à data original — a antecipação para 13–14 foi desfeita em 11/07; folga extra usada para fechar gaps de contrato pós-aula-08).** Avaliação por rubrica de 9 critérios (pesos: decomposição 15, assíncrono 15, idempotência 12, resiliência 12, testabilidade 13, ADRs 13, cache 10, IA 5, execução 5) — o `AVALIACAO.md` mapeia critério → evidência e é obrigatório.

## Grupo e papéis

Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato).

## Stack (ADR-001 — não rediscutir sem motivo)

Java 25 LTS · Quarkus **3.33.2** (LTS, BOM `io.quarkus.platform`) · Maven multi-módulo · SmallRye Reactive Messaging (Kafka/RabbitMQ) · SmallRye Fault Tolerance · quarkus-cache (Caffeine — sem Redis, nenhum pom depende dele) · Panache · PACT (Quarkiverse). Equivalências Spring→Quarkus: tabela na ADR-001.

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
Cronograma (entradas datadas = história como aconteceu):

- [x] 05–06/07 — Incremento 1: tópico de ingestão + consumidor idempotente (Kafka) — concluído (commit 88503a9), issue #1 fechada, `mvn verify -Pplano-b-jvm` verde (7 testes, 0 falhas)
- [x] 07/07 — Incremento 2: consolidação + base segregada + evento posicao-atualizada (branch `feat/inc-2-consolidacao` → PR; ADR-004 idempotência na base, ADR-005 outbox; 11 testes verdes no plano B)
- [x] 07/07 — Incremento 3: cache na consulta + invalidação + carimbo (branch `feat/inc-3-cache-consulta`, empilhada sobre o Inc-2; ADR-006 cache miss; 19 testes verdes no plano B)
- [x] 07/07 — Incremento 4: fila de reconsolidação (RabbitMQ) + retry/DLQ (branch `feat/inc-4-resiliencia`; retry 3× backoff + DLQ Kafka/Rabbit conforme ADR-007; 24 testes verdes no plano B; DLQ física = validar no plano A)
- [x] 07/07 — Incremento 6: observabilidade (branch `feat/inc-6-observabilidade`; correlação ponta a ponta HTTP→Kafka→outbox→evento→invalidação + fila AMQP; logs JSON no plano A; 29 testes verdes. **Achado importante: MDC do Quarkus não funciona em thread de mensageria — correlação vai explícita nos consumidores; ver uso-de-ia.md**)
- [x] 07/07 — Incremento 5: contract test PACT consulta↔consolidação (branch `feat/inc-5-pact`; pact em disco versionado em `pacts/`; Quarkiverse quarkus-pact 1.5.0 — versão própria, fora do BOM da plataforma; 32 testes verdes no plano B)
- [x] 10/07 — README.md reescrito para ser autossuficiente (branch `feat/inc-5-pact`; seções "Arquitetura em 30 segundos" + "Testando o fluxo ponta a ponta" com roteiro `curl` completo dos 3 serviços; corrigidas menções indevidas a Redis — o cache é só Caffeine) e AVALIACAO.md reestruturado: nota 0–100 por critério + peso explícito ao lado, tabela-resumo que calcula peso×nota÷100 (total proposto: 96,7/100). **Notas são rascunho de IA** — não há escala oficial da rubrica documentada no material do curso, só os pesos (linha acima); grupo ainda precisa validar/ajustar antes do fechamento.
- [x] 10/07 — Demo docker-compose + validação plano A (PR #17: `./demo.ps1` um comando; **2 bugs reais achados e corrigidos** — `dlx.declare` e conversão de payload `Message<T>` no Rabbit; DLQ Kafka com causa nos headers demonstrada; ver uso-de-ia.md "o dia em que o plano A pagou o aluguel") + roteiro da banca (PR #18: demo em 8 passos, 10 perguntas de arguição com ADR, titular/backup)
- [x] 11/07 — Auditoria de aderência às aulas 5–8 + projeto-final.pdf (nenhum requisito descoberto): message pact do tópico implementado (34 testes; branch `feat/pact-mensagem`), nota pact-em-disco×Broker na ADR-003, nomenclatura conferida
- [x] 11/07 (noite) — Rumo ao 100 (PR #23): disjuntor + última resposta boa no cache miss, reprocessar-dlq.ps1/.sh, broadcast de invalidação (2 réplicas ao vivo, --profile escala); AVALIACAO total proposto 100,0; 38 testes; CI verde
- [x] 10/07 (noite) — Notas do AVALIACAO.md validadas pelo grupo (100,0 ratificado; registro no uso-de-ia — inclui erratum de datação: registros "11/07" = noite de 10/07)
- [x] 11/07 — Demo enriquecida (PR #24): Swagger UI nos 3 serviços (modo prod), coleção com lote+US-03 (Newman 27/27), roteiro com atos bônus, workspace Postman pessoal no .gitignore (decisão em postman/README.md) e **workflow e2e no CI** (compose+Newman a cada PR — plano A com selo contínuo igual ao plano B)
- [x] 11/07 — Incremento 7: métricas + dashboard (branch `feat/inc-7-metricas`, sessão do Sandy): Micrometer/Prometheus nos 3 serviços (`/q/metrics`; 6 counters de negócio, tags só enumeradas — LGPD; consulta 100% built-in: Caffeine + `ft_*`), ADR-008, Prometheus+Grafana no compose sob `--profile observabilidade` (dashboard "visão da banca" provisionado; portas parametrizáveis `PROMETHEUS_PORT`/`GRAFANA_PORT`); **41 testes verdes no plano B**; validado no plano A com tráfego real (DLQ vermelho ao vivo; achado: fallback do FT é `ft_invocations_total{fallback="applied"}`); Newman 27/27; ato bônus no roteiro-banca
- [x] 11/07 (tarde) — Stack completa vira o padrão do compose (decisão do Leo, de olho em distribuição futura): `profiles:` escala/observabilidade removidos — `docker compose up` puro sobe réplica (8084) + Prometheus + Grafana; ADR-008 revisada com histórico, e2e passa a esperar 4 saúdes, README/observabilidade/roteiro sem `--profile`. Coleção Postman ganhou a pasta 7 (27→35 asserções: prova do broadcast nas 2 instâncias + Prometheus/Grafana) — a asserção nova pegou imagem com JAR velho no mesmo dia; 2 flakiness corrigidas (cold start 5s; 429 rerun-estável); veneno/DLQ e disjuntor revalidados ao vivo. Varredura: 4 textos afirmando Redis na stack corrigidos (ADR-001/003, pom), 38→41 testes, 2→3 contratos, 7→8 ADRs, "da Sandy"→"do Sandy". PR #30 mergeado, CI verde.
- [x] 11/07 (noite) — Distribuição por Docker Hub como SEGUNDA via (branch `feat/publicacao-docker-hub`, decisão do Leo de retomar a ideia antes suspensa): `docker-compose.hub.yml` (imagens em vez de build, `${HUB_NS}` obrigatório) + `publicar-hub.ps1`/`.sh` (builda+push das 5 imagens; `docker login` é do Leo) + Dockerfiles de observabilidade que assam a config (opção "b": um arquivo e roda, sem clonar). ADR-009 registra o porquê (2 vias, não substituição; observabilidade assada). A via principal (demo/CI) fica intocada. **Validado com namespace `localtest -SkipPush`: stack inteira pelo hub-compose, Newman 35/35, 4 alvos Prometheus up, dashboard Grafana assado.** Push real ao Docker Hub fica com o Leo (credencial). ADR-009 vira a 9ª ADR (índice atualizado)
- [x] 12/07 (madrugada) — PUBLICADO no Docker Hub sob `leofariacode` (5 imagens, tags 1.0.0+latest; o Leo fez `docker login`, a IA rodou o `publicar-hub`). PR #32: comando cross-platform — namespace default `leofariacode` no compose, roda **sem env var** em Windows/Linux/macOS (bug do `HUB_NS=valor` bash achado pelo Leo rodando no PowerShell). PR #33: varredura de contagens (9 ADRs/7 incrementos/41 testes/35 asserções/3 contratos). Provado puxando do Hub público: Newman 35/35. Comando do usuário final: `docker compose -f docker-compose.hub.yml up -d`
- [ ] pós-banca (opcional) — considerar tornar o repo GitHub público junto com as imagens; para republicar após mudança: `docker login` + `./publicar-hub.ps1 -Namespace leofariacode -Tag 1.0.x`
- [ ] até 14/07 — ensaio cronometrado (preencher pós-ensaio do roteiro-banca.md)
- [ ] 15/07 — banca. Lembrete: rodar `./demo.ps1` ~10 min antes de entrar na sala
