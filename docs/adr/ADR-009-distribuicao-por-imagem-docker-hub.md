# ADR-009 — Distribuição por imagem (Docker Hub): segunda via, não substituição

- **Status:** aceita · 11/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** vontade de deixar o projeto executável por terceiros (professor/avaliador, ou qualquer curioso) **sem clonar o repo nem ter JDK/Maven** — só Docker
- **Relaciona-se com:** ADR-002 (decomposição: o distribuível de um sistema multi-serviço é *imagem por serviço* + compose, não um monólito empacotado), ADR-008 (observabilidade: as configs de Prometheus/Grafana passam a ter uma segunda encarnação, assada em imagem), ADR-001 (imagem JVM `eclipse-temurin:25-jre` — o runtime viaja dentro da imagem)

## Contexto

A via de execução atual (`docker-compose.yml` + `demo.ps1`) **builda do código-fonte**: exige clonar o repo, JDK 25 e Maven, e roda `mvn package` + `docker compose up --build`. É a via certa para a demo da banca e o CI (o artefato é o código, e queremos vê-lo compilar). Mas é uma barreira alta para quem só quer **executar** o projeto pronto.

O requisito novo: um terceiro roda a stack completa com **só Docker instalado**. Isso é natural com imagens publicadas — a imagem JVM já carrega o runtime (`eclipse-temurin:25-jre`) e o `.jar` compilado; o consumidor puxa e sobe. O requisito de JDK/Maven não some, **muda de endereço**: passa a ser pago uma vez, por quem publica.

## Decisão

**Adicionar uma segunda via, sem tocar na primeira.** Artefatos novos, todos aditivos:

1. **`docker-compose.hub.yml`** — espelha a stack completa (3 serviços + réplica + brokers + observabilidade), mas com `image:` do Docker Hub em vez de `build:`. Namespace parametrizado (`${HUB_NS}`, obrigatório) e tag (`${TAG:-latest}`).
2. **`publicar-hub.ps1` / `.sh`** — empacota (`mvn package`), builda e faz `push` das 5 imagens próprias. `docker login` é do mantenedor, nunca do script.
3. **Imagens de observabilidade auto-suficientes** (`infra/observabilidade/{prometheus,grafana}/Dockerfile`) — assam a config (`prometheus.yml`, datasource, dashboard) que a via principal monta como volume.

**Cinco imagens publicadas:** `extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta` (a réplica reusa esta), `extrato-prometheus`, `extrato-grafana`. Kafka/RabbitMQ/Postgres são imagens públicas oficiais — nada a republicar.

### Por que assar a config de observabilidade na imagem (e não montar volume)?

Para a via do Hub ser "um arquivo e roda", o `docker-compose.hub.yml` não pode depender de arquivos do repo. Os 3 serviços já assam tudo (o `.jar` está na imagem); Prometheus/Grafana eram a exceção que dependia de volume. Assar a config elimina a exceção — a alternativa (manter volumes) obrigaria o consumidor a ter a pasta `infra/` em disco, ou seja, clonar. Rejeitada: mata o objetivo.

**A fonte de verdade continua sendo os arquivos em `infra/observabilidade/`** — a via principal os monta como volume (edição ao vivo, sem rebuild) e os Dockerfiles os `COPY`am. A imagem é um **retrato** deles no momento da publicação.

## Consequências

- (+) Terceiro roda a stack completa com só Docker: `HUB_NS=<usuario> docker compose -f docker-compose.hub.yml up -d`. Sem clone, sem JDK, sem Maven.
- (+) A via principal (build-from-source) fica **intocada**: demo da banca, CI (`verify`/`e2e`) e o desenvolvimento diário seguem idênticos.
- (+) Coerência: a observabilidade passa a ser tão auto-suficiente quanto os serviços (config na imagem, como o `.jar`).
- (−) **As imagens do Hub são um retrato, não um espelho.** Mudança em dashboard/scrape só chega ao consumidor do Hub após republicar (`publicar-hub` com tag nova). Mitigação: o desenvolvimento acontece contra a via principal (arquivo em disco, ao vivo); a republicação é deliberada, ao cortar uma release — não a cada ajuste. Mudança de código de serviço exigiria republicar de qualquer forma (o `.jar` é assado).
- (−) O namespace do Docker Hub passa a hospedar duas imagens de observabilidade que são a imagem pública + um arquivo de config por cima. Aceito: é o preço do "um arquivo e roda".
- (−) Duas fontes de config de observabilidade (o arquivo e a imagem) podem divergir se alguém esquecer de republicar. Mitigação: a via principal é sempre a fonte de verdade; a imagem se regenera dela com um comando.

## Validação (11/07)

Publicação simulada com namespace `localtest` (`publicar-hub.ps1 -SkipPush`) e a stack subida **inteiramente pelo `docker-compose.hub.yml`**, sem nenhum volume do repo: 4/4 serviços saudáveis, **Newman 35/35**, Prometheus com os 4 alvos `up` e Grafana servindo o dashboard "visão da banca" + datasource — tudo assado nas imagens. Prova de que a via do Hub é self-contained antes de qualquer `push` real.
