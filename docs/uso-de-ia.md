# Uso de IA no projeto — log crítico (critério 8 + opcional bônus)

> Registro contínuo e honesto: o que pedimos à IA, o que ela entregou, **o que veio errado ou foi rejeitado**, e o que validamos manualmente. Não é marketing — é diário de bordo.

Ferramenta principal: Claude (Cowork/desktop), com delegação por complexidade (modelo topo para arquitetura/revisão; subagentes para implementação com spec fechada; verificação sempre por build/teste real, nunca por opinião do modelo).

---

## 05/07 — Estratégia e decisão de stack

- **Pedido:** definir estratégia do projeto, personas e delegação de tarefas IA.
- **IA propôs:** Spring Boot 3.5.x, argumentando alinhamento com o gabarito e com a rubrica (`@RetryableTopic` é Spring Kafka).
- **Grupo rejeitou e reorientou:** priorizamos Quarkus (próximo módulo do curso + movimento de adoção na Caixa). A IA então pesquisou versões (web) e confirmou Quarkus 3.33 LTS como primeira LTS com suporte pleno a Java 25 — fonte: Red Hat Developer/endoflife.date.
- **Validação manual:** a tabela de equivalências Spring→Quarkus da ADR-001 será validada padrão a padrão durante a implementação (cada equivalência só é "verdadeira" quando o teste correspondente passar).
- **Lição:** a IA otimiza para o caminho de menor risco; a decisão estratégica (contexto de carreira/empresa) é humana.

## 05/07 — Pacote de requisitos simulado

- **Pedido:** gerar pacote de elicitação (personas + sessões + user stories) no formato do exemplo do docente (Comprovantes PIX).
- **IA entregou:** 6 personas, 5 sessões de descoberta, user-stories.md com glossário.
- **Intervenção humana relevante:** o grupo questionou a ausência de devs nas reuniões. Discussão resultou na **Sessão 6 (refinamento)** — decisão consciente: manter descoberta sem devs (fiel ao exemplo e ao momento *inception*) e adicionar o refinamento onde o time se apropria da linguagem ubíqua (*hands-on modelers*, DDD). A Sessão 6 também produziu o erratum #1 e a tabela de ADRs candidatos.
- **Validação manual:** plausibilidade regulatória (consentimento 12 meses, expurgo, finalidade) revisada contra o conhecimento do grupo sobre Open Finance; datas e consistência entre sessões conferidas.
- **Risco assumido e mitigado:** conteúdo gerado por IA pode "alucinar" detalhes regulatórios — tratamos números específicos (30 dias de expurgo) como **política interna fictícia**, declarada como tal.

## 05/07 — Bootstrap do repositório

- **Pedido:** estrutura multi-módulo Quarkus 3.33.2 + Java 25, ADRs 001/002, esqueleto compilando.
- **IA entregou:** 4 módulos, contratos iniciais em `shared-contracts`, smoke tests, perfis Maven A/B.
- **Correção durante a revisão:** o contrato `LancamentoRecebido` inicialmente **não tinha `idCliente`** — a consolidação não saberia de quem é a conta. Detectado na revisão do próprio agente e corrigido antes do commit. Exemplo concreto de por que contrato gerado exige leitura humana.
- **Validação manual:** `mvn verify -Pplano-b-jvm` na máquina do Leo (Java 25, Windows): **BUILD SUCCESS**, 5 módulos, 3 testes verdes. O build revelou o que a IA não sabia: `quarkus-junit5` foi **relocado para `quarkus-junit`** na 3.31 (warning do Maven) — corrigido nos três poms. Reforça a regra do grupo: *conhecimento de versão da IA expira; o build é o árbitro*.

---

## 05/07 — Incremento 1: tópico Kafka + consumidor idempotente

- **Pedido:** ingestão publica no tópico `lancamentos-recebidos`; consolidação consome com guarda de idempotência; teste "reprocessa sem duplicar" (US-02) rodando sem Docker.
- **IA entregou:** `LancamentoResource` (202 aceite / 400 com campos faltantes), `PublicadorLancamentos` (chave Kafka = instituição+agência+conta para ordem por conta), `GuardaIdempotencia` (interface + impl. provisória em memória via `Set#add` atômico), `ConsumidorLancamentos`, testes com connector in-memory do SmallRye.
- **Autocorreção na revisão da própria IA (antes do build):** dependência Awaitility usada no teste da ingestão mas declarada só na consolidação. 
- **Decisões conscientes registradas:** validação manual da ficha (não Bean Validation) para o 400 listar exatamente "o campo que faltou" (US-01); armazenamentos em memória são PROVISÓRIOS e nomeados como tal até a base segregada do Inc-2; logs só com identificadores opacos (US-12 desde já).
- **Validação manual:** `mvn verify` na máquina do Leo (resultado registrado abaixo).

## 05/07 — Verificação geral do projeto

- **Pedido:** varredura geral (não só Inc-1): coerência entre ADRs/arquitetura/user-stories/Sessão 6 e o código, revisão do código do Inc-1, e confirmação por build real.
- **IA fez:** leu todo o pacote de docs e o código de `extrato-ingestao`/`extrato-consolidacao` linha a linha; rodou `mvn verify -Pplano-b-jvm` (não deu opinião sem rodar).
- **Resultado:** BUILD SUCCESS — 5 módulos, 7 testes (3 ingestão + 3 consolidação + 1 consulta), 0 falhas, ~2min12s, sem Docker. Idempotência confirmada nos logs do teste (3 reenvios do mesmo lançamento geram só 1 log de incorporação).
- **Achados:** nenhum bug de código. Docs desatualizados em relação ao que já foi entregue — `CLAUDE.md` (checklist do Inc-1) e `AVALIACAO.md` (critérios 3 e 6 ainda em TODO apesar de já terem evidência real) foram corrigidos nesta sessão.
- **Validação manual:** o próprio `mvn verify` é a validação — regra do grupo (verificação sempre por build real, nunca por opinião do modelo).

## 07/07 — Incremento 2: base segregada + três efeitos + outbox

- **Pedido:** consolidação com base própria (Panache), evento `posicao-atualizada`, e fechamento das ADRs 004 (idempotência) e 005 (consistência) que o bloqueavam. Fluxo Git cuidadoso: branch + PR, nada direto na main.
- **Decisões humanas antes do código:** Leo ratificou via perguntas explícitas da IA (a) outbox transacional em vez de publicar-após-commit, e (b) Postgres via Dev Services no plano A (H2 só nos testes). As duas alternativas rejeitadas estão documentadas nas ADRs com o porquê.
- **IA entregou:** ADR-004/005; entidades `LancamentoIncorporado` (UNIQUE = memória de dedup), `PosicaoConsolidada` (upsert reabre competência — US-03), `EventoPendente` (outbox só-referência); `ServicoConsolidacao` transacional; `PublicadorPosicaoAtualizada` (@Scheduled, marca `publicado_em` pós-ack, falha interrompe o lote para preservar ordem); remoção dos provisórios do Inc-1; 6 testes novos.
- **O que o build ensinou (o build é o árbitro):** `quarkus.hibernate-orm.database.generation` está **deprecated** na 3.33 — a IA usou a propriedade antiga; o warning do primeiro build levou à troca por `schema-management.strategy`. Segundo caso do projeto de conhecimento de versão expirado (o primeiro foi `quarkus-junit5`→`quarkus-junit`).
- **Mudança estrutural consciente:** a interface `GuardaIdempotencia` do Inc-1 foi **removida**, não reimplementada — a dedup precisa rodar dentro da transação dos três efeitos; mantê-la como componente separado não sustentaria a semântica (racional na ADR-004).
- **Validação manual:** `mvn verify -Pplano-b-jvm` no reator completo — BUILD SUCCESS, 11 testes (3 ingestão + 7 consolidação + 1 consulta), 0 falhas, sem Docker.

## 07/07 — Incremento 3: cache na consulta + ADR-006

- **Pedido:** aproveitar o dia para adiantar o cronograma — ADR-006 (cache miss) + Inc-3 completo, em branch empilhada sobre a do Inc-2 (PR separado, main intocada).
- **Argumento que fechou a ADR-006 (achado da análise, não estava na Sessão 6):** como o evento `posicao-atualizada` carrega só referência (minimização, US-10), uma réplica própria na consulta teria de chamar a consolidação a cada evento de qualquer forma — a "medição dos dois desenhos" pedida ao Marcos se reduz a análise estrutural: a réplica é estritamente mais complexa sem eliminar o acoplamento. TTL do cache = 5 min = meta de frescor da US-05 (invalidação perdida nunca viola a meta).
- **IA entregou:** ADR-006; `PosicaoDaConta` em shared-contracts + `GET /interno/posicoes` na consolidação (par do futuro PACT, provider e consumer já nomeados); consulta cache-first (`@CacheResult` Caffeine), invalidação por evento, carimbo do dado, `?atualizar=true` com limite por cliente (429); 9 testes novos.
- **Técnica de teste que vale registrar:** hit/miss/invalidação viram aceite *demonstrável* com um dublê da fonte (`@Mock`) que conta invocações — hit não incrementa, miss incrementa. Sem HTTP, sem Docker, sem inspecionar o cache por dentro.
- **Validação manual:** `mvn verify -Pplano-b-jvm` no reator — BUILD SUCCESS, 19 testes (3 ingestão + 10 consolidação + 6 consulta), 0 falhas. Ambos os módulos novos passaram no primeiro build.

## 07/07 — Incremento 4: retry/DLQ + fila de reconsolidação

- **Pedido:** implementar a ADR-007 (mesma sessão em que ela foi escrita — a spec virou código no mesmo dia): retry+DLQ no consumo Kafka, fila RabbitMQ do guichê, @Timeout na consulta.
- **Tradução `@RetryableTopic` → Quarkus (fecha pendência da ADR-001):** funcionou como previsto, com uma nuance que a tabela da ADR-001 não capturava — em Quarkus a política se divide em DUAS camadas: `@Retry`/`@ExponentialBackoff` (SmallRye FT, em processo) para as tentativas, e `failure-strategy=dead-letter-queue` (connector) para o encaminhamento final. No Spring, `@RetryableTopic` faz os dois numa anotação. A separação até ajuda na defesa: política de tentativa e destino da falha são decisões independentes.
- **Limite do plano B encontrado e documentado:** o connector in-memory não implementa DLQ (recurso do broker). Solução: `%test.failure-strategy=ignore` preserva o comportamento observável da US-08 (mensagem esgotada sai do caminho, fluxo segue) e o teste prova a política (contagem exata de tentativas via `@InjectSpy`); o encaminhamento físico fica para a demo do plano A — exatamente a divisão de responsabilidade que a ADR-003 previu.
- **Detalhe fino de CDI que a IA acertou por análise:** injetar falhas por subclasse de teste quebraria o `@Transactional` (override perde o interceptor binding; super-call bypassa o proxy). `@InjectSpy` preserva a cadeia de interceptação do Arc. Registrado porque é o tipo de bug silencioso que só aparece em produção.
- **Validação manual:** `mvn verify -Pplano-b-jvm` — BUILD SUCCESS, 24 testes (3+15+6), 0 falhas.
- **Pendência para o plano A (anotar no roteiro da demo):** validar serialização de `YearMonth` no connector RabbitMQ (o in-memory passa o objeto por referência e não exercita o Jackson do canal) e ver a DLQ física com os headers de causa.

## 07/07 — Incremento 6: observabilidade — e o dia em que o MDC mentiu

- **Pedido:** correlação ponta a ponta (US-12) + logs JSON, fechando o "opcional obrigatório".
- **O bug que virou aula:** o desenho inicial usava MDC (o mecanismo canônico) para carregar o correlation id dos consumidores de mensagem até a outbox. O teste ponta a ponta falhou. Diagnóstico por **probe empírico**, não por opinião: (1) as três APIs de MDC (slf4j, jboss-logging, jboss-logmanager) funcionam perfeitamente na thread de teste e nas threads HTTP (o header Kafka da ingestão saiu certo); (2) na thread de mensageria (`executor-thread` do SmallRye RM), `MDC.put` seguido de `MDC.get` **na mesma thread devolve null** — o VertxMDC do Quarkus com contextos duplicados do Vert.x não sustenta o roundtrip ali.
- **Decisão de engenharia:** parar de brigar com o framework. Nos caminhos de mensageria a correlação passou a ser **explícita** (parâmetro na cadeia de chamadas + campo no texto do log); MDC/`%X`/campo JSON ficam nas bordas HTTP, onde funcionam. De quebra, o desenho ficou mais honesto: dependa do explícito, não do mágico.
- **Segunda correção do build:** o scheduler da outbox disparava durante o startup e logava ERROR transitório de injeção do Emitter — resolvido com `delayed=2s`.
- **Validação manual:** `mvn verify -Pplano-b-jvm` — BUILD SUCCESS, 29 testes (5+16+8), 0 falhas. O teste `CorrelacaoFluxoTest` prova o trecho mais difícil: o id sobrevive à fronteira assíncrona porque a **outbox o persiste** (coluna `correlacao_id`).
- **Lição para a banca:** "usamos MDC" era a resposta de manual; a resposta real do nosso sistema é "MDC onde o framework sustenta, explícito onde não sustenta — e sabemos exatamente onde é cada um, porque testamos".

## 07/07 — Incremento 5: PACT consulta↔consolidação

- **Pedido:** fechar o contract test do par eleito na Sessão 6 (decisão 2) — o mesmo par HTTP que a ADR-006 tornou a fonte do cache miss.
- **Surpresa de versão (a IA errou, o build corrigiu):** a IA assumiu que o quarkus-pact era membro do BOM da plataforma (`io.quarkus.platform:quarkus-pact-bom`) — o artefato não existe. Consulta ao Maven Central: `io.quarkiverse.pact` 1.5.0 com versão própria. Regra do grupo confirmada de novo: conhecimento de versão da IA expira; o build (e o Central) é o árbitro.
- **Desenho que vale defender:** (1) o teste consumer deserializa no record real `PosicaoDaConta` — se o shape do contrato não hidratar o tipo compartilhado, quebra no consumer antes de produção; (2) o pact é **arquivo versionado** (`pacts/`), não broker — Docker-free, e o diff do contrato aparece em PR; (3) o provider semeia estados pelo caminho real (`ServicoConsolidacao.incorporar`), então a verificação exercita endpoint + serialização + banco de verdade.
- **Ordem no reator:** o provider (consolidação) builda ANTES do consumer (consulta) — a verificação usa o pact **commitado** da rodada anterior; quem muda o contrato regenera o arquivo e o PR carrega o diff. É o fluxo "pact como artefato".
- **Validação manual:** `mvn verify -Pplano-b-jvm` — BUILD SUCCESS, 32 testes (5+18+9), 0 falhas; log do provider mostra "Verifying a pact between extrato-consulta and extrato-consolidacao" nas 2 interações.

## 10/07 — README autossuficiente + proposta de níveis no AVALIACAO.md

- **Pedido:** reorganizar os comandos do README em blocos coerentes (pré-requisitos, instalar, compilar, testar, dev mode, perfis); depois, tornar o README autossuficiente para quem nunca viu o projeto conseguir rodá-lo sozinho; por fim, atualizar o AVALIACAO.md e propor níveis auto-atribuídos por critério.
- **IA entregou:** seções novas no README — "Arquitetura em 30 segundos" (diagrama textual dos 3 serviços/portas) e "Testando o fluxo ponta a ponta" (roteiro `curl`: health check → `POST /lancamentos` → `GET /extrato` → `?atualizar=true` → `POST /reconsolidacoes`), com payloads conferidos contra os records reais (`LancamentoRecebido`, `ReconsolidacaoResource.Solicitacao`) em vez de inventados.
- **Erro que a IA cometeu e corrigiu sozinha na mesma sessão:** o texto original citava "Redis" como parte da stack de cache em três lugares (Stack, Pré-requisitos, Perfis de execução) — não existe dependência de Redis em nenhum pom; o cache é só Caffeine (`quarkus-cache`). Corrigido antes do commit; ficou como lembrete de que texto herdado de versões antigas do README não é fonte confiável sobre o estado atual do código.
- **Decisão consciente sobre o pedido de níveis auto-atribuídos:** o AVALIACAO.md pedia "nível auto-atribuído" em todos os 9 critérios sem nunca ter sido preenchido, e **não há escala documentada em lugar nenhum do material do curso**. A IA não inventou notas silenciosamente — perguntou ao grupo antes de propor (a primeira pergunta ficou sem resposta; a segunda vez o Leo autorizou a proposta explicitamente "a gente ajusta depois"). A proposta usa uma escala 1–5 genérica, é marcada no cabeçalho do documento como rascunho de IA, e traz "gap conhecido" nos 3 critérios que não ficaram em 5/5 (cache: TTL local por instância; resiliência: DLQ física ainda não demonstrada no plano A; uso de IA: esta própria sessão não estava registrada aqui até este commit).
- **Validação manual:** `mvn verify -Pplano-b-jvm` reexecutado do zero para confirmar que a alegação "32 testes, 0 falhas" no critério 6 ainda era verdadeira antes de reafirmá-la no documento — BUILD SUCCESS, 5+18+9=32 testes, 0 falhas (nenhuma mudança de código desde o Inc-5, só documentação).
- **Correção pedida pelo Leo, mesmo dia:** perguntado se a escala 1–5 batia com a da rubrica do curso, a IA conferiu `CLAUDE.md`/ADRs/docs/código de novo e confirmou que **não existe escala oficial em lugar nenhum** — só os pesos por critério (que somam 100). A pedido do Leo, a escala 1–5 foi trocada por **nota 0–100 por critério + peso explícito ao lado**, com uma tabela-resumo que calcula peso×nota÷100: assim, se um peso da rubrica mudar, só a tabela recalcula, sem reavaliar a nota. Notas mantidas equivalentes às do 1–5 original (100/85/100... = mesma proporção).

## 10/07 — Validação do plano A: o dia em que o plano A pagou o aluguel

- **Pedido:** docker-compose da demo (issue #9) + validar as duas pendências anotadas desde o Inc-4 (DLQ física, `YearMonth` no Rabbit).
- **Entregue:** `docker-compose.yml` (Kafka KRaft + RabbitMQ + Postgres + os 3 serviços), `Dockerfile.jvm` por módulo, `demo.ps1`/`demo.sh` (demo do zero com um comando), seção de demo no README com o roteiro da DLQ ao vivo.
- **Dois bugs reais que o plano B NUNCA pegaria** (a divisão da ADR-003 provada na prática):
  1. **DLX não declarado**: `auto-bind-dlq=true` não declara o dead-letter exchange — o bind da `reconsolidacao-dlq` falhava com 404 (`no exchange 'DLX'`), o canal esgotava 100 reconexões e morria (health DOWN). Fix: `dlx.declare=true`. O connector in-memory não tem exchange nenhum — impossível detectar no plano B.
  2. **`Message<T>` não converte payload**: quem consome `Message<T>` no SmallRye não ganha conversão automática — o connector Rabbit entrega `JsonObject` cru e o cast implícito estourava **antes** de qualquer log do guichê (nack silencioso → DLQ). No Kafka não acontece porque o Quarkus gera deserializer tipado no connector; no in-memory também não, porque o teste envia o próprio record. Fix: conversão explícita no consumidor (`mapTo`), com o porquê em javadoc. A suspeita original ("validar serialização YearMonth") estava meio certa: a serialização de SAÍDA estava perfeita (`"competencia":"2026-07"` no corpo da mensagem da DLQ) — o problema era a ENTRADA, e nem era o YearMonth.
- **Validações que passaram limpo:** fluxo ponta a ponta com correlação (`banca-demo-*` visível do POST ao log da invalidação); invalidação por evento comprovada contra um vazio cacheado (bem dentro do TTL); guichê processando pelo Rabbit real com correlação AMQP; **DLQ do Kafka com o contrato exato da Sessão 4** — mensagem original + `dead-letter-reason` (a violação de not-null do veneno) + classe da exceção + tópico/partição/offset de origem — e o fluxo seguindo (lançamento válido atrás do veneno incorporado normalmente).
- **Método:** cada achado foi diagnosticado pela evidência do broker (health check, log JSON, corpo da mensagem na DLQ via management API), corrigido, rebuiltado e revalidado no ciclo. `mvn verify -Pplano-b-jvm` reconfirmado verde após os fixes (32 testes).

## 10/07 (tarde) — Coleção Postman da demo: e o 3º bug do plano A

- **Pedido:** coleção Postman com os requests da demo (versionada em `postman/`, importável pela banca; testes automáticos por request).
- **A coleção achou um bug na primeira execução:** 21/22 asserções verdes — a vermelha era o `/q/health` da consulta em 503, com o serviço funcionalmente perfeito. Causa: `ConsumidorPosicaoAtualizada` era o ÚNICO consumidor sem `@Blocking` — com Kafka real a entrega vem no event loop do Vert.x, o `@CacheInvalidate` bloqueia ("The current thread cannot be blocked") e a subscription do canal morre silenciosamente. O in-memory do plano B entrega em outra thread e nunca exercitou isso. Ou seja: **a invalidação por evento provavelmente nunca tinha funcionado no plano A até aqui** — o TTL mascarava. Pós-fix, prova definitiva: extrato cacheado vazio → evento → log "Cache invalidado por evento ... [corr=...]" → dado novo servido em segundos (TTL é 300s; foi o evento).
- **Bônus da reentrega:** o restart da consulta reconsumiu os eventos antigos do tópico (offsets nunca commitados pela subscription morta) e as invalidações repetidas foram inofensivas — "pelo menos uma vez" + consumidor idempotente demonstrados de graça.
- **Lição de teste de API registrada:** a 1ª versão da coleção assertava valores absolutos (entradas == 150) e quebrou quando a base descartável da demo foi recriada. Correção: asserções RELATIVAS (linha de base capturada no 1º GET; idempotência = "não mudou") — a coleção agora roda N vezes contra qualquer estado. Validação: `npx newman run` → 22/22.
- **Total do plano A: 3 bugs reais** que a suíte (32 testes verdes) não pegaria — dlx.declare, conversão de payload do Rabbit, @Blocking no consumidor de eventos. Nenhum é "bug de lógica"; todos são fronteira código↔infra — exatamente o que a ADR-003 disse que o plano B não cobre.

## 11/07 — Auditoria de aderência (aulas 5–8 + projeto-final.pdf) e o pact de mensagem

- **Pedido:** com as aulas 5–8 em mãos, verificar aderência do projeto ao curso e ao projeto-final.pdf, com atenção especial a testes/contratos. Banca adiada para 15/07 — prazo extra usado para fechar gaps em vez de só documentá-los.
- **Resultado da auditoria:** nenhum requisito formal descoberto; aderência forte em mensageria (a distinção fila×tópico do complemento da aula-06 é exatamente a nossa defesa), resiliência e cache. Três gaps em testes/contratos, todos fechados:
  1. **Message pact do tópico** (aula-08-contract): implementado — consolidação como consumer do shape mínimo (opcionais do erratum #1 fora do contrato de propósito), ingestão como provider contra a serialização real. Reator: 34 testes.
  2. **Pact em disco × Broker**: a escolha existia mas não estava escrita — agora é a nota de 11/07 na ADR-003 (monorepo + reator = o git é o broker; `can-i-deploy` vira evolução para deploys independentes).
  3. **Nomenclatura das interações** conferida contra o vocabulário da aula (expectsToReceive/provider states descritivos).
- **Surpresa técnica (a IA errou, o framework corrigiu):** o DSL clássico de message pact (`MessagePactBuilder`/`List<Message>`) não roda no default do pact-jvm 4.6 — o spec V4 exige outra assinatura (`V4Pact xxx(PactBuilder)`). O erro do runner diz exatamente isso; `pactVersion = V3` na anotação resolve. Registrado porque a aula usa o DSL clássico e o erro vai aparecer para qualquer colega que copiar o exemplo em versão nova.
- **Validação:** `mvn verify -Pplano-b-jvm` — 34 testes, 0 falhas; "Verifying a pact between extrato-consolidacao and extrato-ingestao" no log do build da ingestão.

## 11/07 (tarde) — Hardening pré-banca: e o 4º bug, o pior de todos

- **Pedido:** executar o pacote de melhorias da revisão de ADRs (CI real, índice de ADRs, notas 004/005, resposta de escopo US-04/US-11) — e testar o caso que a revisão apontou como nunca exercitado: **lixo de deserialização**.
- **O 4º bug (o único que TRAVAVA o sistema):** JSON inválido publicado direto no tópico → falha no deserializer do connector, ANTES do nosso retry/DLQ → o poll re-tenta para sempre → **partição travada**, health 503, lançamento válido atrás do lixo nunca processa. Os 3 bugs anteriores degradavam; este PARAVA a esteira — violação direta da US-08, invisível para os 34 testes verdes e para os 3 bugs já achados. O próprio erro do SmallRye aponta a solução ("configure a DeserializationFailureHandler").
- **Fix com o espírito da US-08:** handler encaminha os bytes crus à MESMA DLQ com a causa nos headers (sem descarte silencioso) e devolve payload nulo; o consumidor confirma e segue. Demo ao vivo: a partição que estava travada drenou o lixo para a DLQ e o lançamento represado processou em segundos após o deploy do fix.
- **Também nesta sessão:** CI GitHub Actions rodando `mvn verify -Pplano-b-jvm` a cada PR (a alegação "roda em CI" da ADR-003 virou selo verde); índice `docs/adr/README.md`; nota expurgo×dedup na ADR-004 (US-11 exige US-04 — a pergunta difícil da banca respondida por escrito); nota de retenção da outbox na ADR-005; pergunta 11 (escopo US-04/US-11) no roteiro.
- **Placar do plano A: 4 bugs reais** — todos de fronteira código↔infra, nenhum visível no plano B. A régua de veneno agora tem três camadas documentadas na ADR-007: ilegível → handler → DLQ; transitória → backoff; permanente → DLQ.

## 11/07 (noite) — Em busca do 100: fechar gaps com código, não com argumento

- **Pedido:** em vez de aceitar os descontos nomeados dos critérios 5 (85→95) e 4 (85), fechar os gaps de verdade.
- **Resiliência → 100:** `FonteResiliente` (`@Timeout` + `@CircuitBreaker` nomeado + `@Fallback` = **última resposta boa** — degradação com transparência, o carimbo expõe a idade; sem cópia → 503 com Retry-After) + `reprocessar-dlq.ps1/.sh` (reprocesso vira um comando com memória de offset). Validado ao vivo: consolidação derrubada → última-boa/503; religada → normal.
- **Cache → 100:** broadcast de invalidação — consumer group único por instância (`${quarkus.uuid}`); provado com **2 instâncias no compose** (`--profile escala`): um POST, as duas invalidam, as duas servem o dado novo. Escala horizontal deixou de ser gap de correção.
- **Flakiness achada e eliminada na primeira rodada:** o estado do disjuntor é global — a "cura" por sleep vazava circuito aberto entre classes de teste (4 falhas em cascata no ExtratoConsultaTest). Solução correta: `@CircuitBreakerName` + `CircuitBreakerMaintenance.reset()` — determinístico, sem sleep. Registrado porque é a diferença entre conhecer a anotação e conhecer o ciclo de vida dela.
- **Alarme falso instrutivo:** no teste de escala, o evento "sumiu" — diagnóstico por camadas (fonte vazia → lag do consumer group = 0 → mensagem no tópico) revelou que era só a latência de rejoin do consumer após stop/start; o experimento controlado seguinte confirmou o fluxo inteiro são. Lição: leia o lag antes de culpar o código.
- **Validação:** reator 38 testes, 0 falhas, sem Docker; CI verde.

## 10/07 (noite) — Validação das notas pelo grupo: 100,0 ratificado

- **O processo, que é o que importa registrar:** a IA propôs notas com gaps NOMEADOS (4 e 5 em 85); em vez de negociar número, o grupo mandou **fechar os gaps com código** — disjuntor + última resposta boa + DLQ scriptada (crit. 5) e broadcast de invalidação provado com 2 réplicas (crit. 4). Só então o total virou 100,0 — e o grupo validou a proposta final nesta noite. A autoavaliação não é a IA dando nota para o próprio trabalho: é o grupo cobrando evidência executável para cada ponto, com os trade-offs remanescentes escritos como decisão (hits locais por réplica; reprocesso de ilegível volta à DLQ).
- **Ressalva mantida no AVALIACAO:** sem escala oficial da rubrica no material, 100,0 é a leitura do grupo sobre a própria evidência — a banca calibra; a defesa é a evidência, não o número.
- *Nota de datação: os registros "11/07" desta sessão referem-se a esta mesma noite de trabalho (10/07, 20h–23h — os logs em UTC cruzaram a meia-noite e contaminaram a datação; fica o erratum em vez de reescrever histórico commitado).*

## 11/07 (madrugada) — Ciclo de vida das coleções: e o guarda que reprovou o próprio criador

- **Contexto:** com a fonte-da-verdade estabelecida (JSON canônico), o Leo pediu a substituição das coleções manuais/AI do workspace por algo que "fique em dia com o código". Decisão de desenho: **sincronia por mecanismo, não por disciplina** — coleções de referência **geradas do OpenAPI** (`postman/api/`, 1 por serviço, espelhando os bounded contexts) + guarda no CI que falha o PR se defasarem.
- **A estreia do guarda foi reprová-lo a si mesmo:** o primeiro CI vermelho do projeto em semanas — comparação byte a byte acusou "defasagem" no artefato gerado minutos antes. Diagnóstico: o `openapi-to-postmanv2` é **não-determinístico por natureza** (ids aleatórios em cada item, sorteio do valor de enum nos exemplos — `DEBITO` vs `CREDITO` —, contagem variável de chaves em exemplos de mapa), comprovado com duas execuções locais divergindo. Pinar a versão não bastava.
- **Correção pela raiz, não por remendo:** o invariante que importa nunca foi o byte — é a **superfície da API**. O guarda virou **semântico** (`verificar-api.sh`): compara conjuntos (método, path) do `/q/openapi` da stack que o próprio CI subiu contra as requests das coleções commitadas. Endpoint novo sem regenerar = PR vermelho; cosmética aleatória = irrelevante. Passou verde na re-estreia e os PRs #26/#27 entraram com o mecanismo provado.
- **Lição de arguição:** "verificar o que importa" > "verificar o que é fácil". O byte-diff era fácil e mentia; a comparação semântica exige parsear dois formatos, mas fiscaliza o contrato real. Mesma família da escolha PACT-em-disco e do teste com dublê contável: o teste certo mira o invariante, não a representação.
- **Fechamento da topologia Postman:** demo/banca (manual + 27 asserções Newman) · referência de API (gerada, guarda no CI) · Swagger (exploração viva) · workspace pessoal (gitignorado — estado de conta, "o .idea/ do Postman"). Coleções AI antigas deletadas pelo Leo; canônicas importadas.

## 11/07 — Inc-7: métricas + dashboard (sessão do Sandy)

- **Pedido:** "implementar observabilidade" — e a primeira coisa que a IA fez foi apontar que **metade já existia** (Inc-6: logs+correlação, mergeado). Escopo renegociado para a camada que faltava: métricas (Micrometer/Prometheus) + Grafana por profile. Lição de processo: pedir "implemente X" sem checar o estado atual teria produzido retrabalho do Inc-6.
- **Decisões (ADR-008):** built-in antes de contador custom (a consulta inteira não ganhou 1 linha de Java — hit/miss do Caffeine e `ft_*` do FT vêm prontos com o registry no classpath); 6 counters de negócio com tags só enumeradas (LGPD: identificador de cliente **nunca** vira label); OTel rejeitado a 4 dias da banca, documentado como troca-de-registry futura.
- **O que a IA previu certo:** o único risco real eram os **nomes das métricas built-in**. Confirmado no plano A: o dashboard nasceu com `ft_fallback_calls_total`, que **não existe** — o nome real no SmallRye FT atual é `ft_invocations_total{fallback="applied"}`. Conferido contra o `/q/metrics` real antes do commit da infra; o teste de presença do plano B (`MetricasConsultaTest`) fica de sentinela para o caso do cache.
- **Validado à mão (o build é o árbitro):** 41 testes verdes sem Docker (o gate provou que métricas não exigem broker — pull, o serviço não conhece o Prometheus); stack completa no plano A com tráfego real (válido 2×, inválido, veneno no tópico, miss+hit) e os 4 painéis conferidos ao vivo — DLQ vermelho com o envenenado, hit ratio no gauge, séries `incorporado`/`repetido` separadas; Newman 27/27 de regressão.
- **Peculiaridade de ambiente registrada:** a máquina do Sandy não tinha JDK 25 (instalado 25.0.3-tem via SDKMAN, sem virar default) e tinha OUTRA stack `obs-*` nas portas 9090/3000 — resolvido com portas parametrizadas no compose (`PROMETHEUS_PORT`/`GRAFANA_PORT`, default intacto para o time e a banca).

## 11/07 (tarde/noite) — Stack completa por padrão, pasta 7 do Postman e a asserção que pegou imagem velha

- **Contexto:** o Leo decidiu que `docker compose up` deve subir a stack COMPLETA (réplica 8084 + Prometheus + Grafana) para qualquer um que clonar — de olho numa eventual distribuição pública (publicação no Docker Hub avaliada e SUSPENSA para pós-banca; a IA apontou que "uma imagem com tudo" contradiria a própria decomposição da ADR-002 — o distribuível de um sistema multi-serviço é imagem por serviço + compose). A ADR-008 foi **revisada com data e histórico** (o opt-in por profile era decisão registrada; reverter ADR exige registro, não edição silenciosa).
- **Cobertura nova (pasta 7 da coleção, 27→35 asserções):** a prova do broadcast é desenhada para só passar se o broadcast existir — aquece o cache das DUAS instâncias, dispara um lançamento e exige o dado novo em ambas (sem broadcast, a réplica serviria o dado velho por até 5 min de TTL). Prometheus (4 alvos up) e Grafana (health) também viraram asserções.
- **A asserção nova pagou o aluguel NO MESMO DIA:** primeira rodada → Prometheus com 4 alvos DOWN (404 no `/q/metrics`). Diagnóstico: as imagens no ar tinham JARs de ANTES do Inc-7 — "rebuild" do compose copia o jar que estiver no `target/`, e o `mvn package` local estava defasado. Sem a asserção, a demo da banca subiria sem métricas e o dashboard nasceria vazio. Rebuild completo e 35/35.
- **Duas flakiness reais encontradas rodando N vezes seguidas (disciplina da coleção):** (1) o PRIMEIRO lançamento numa stack fria estourava a janela de 3s (JIT + primeira transação) — espera da pasta 1 ampliada para 5s e revalidada com down/up + Newman na primeira tentativa; (2) a releitura forçada (`?atualizar=true`) devolvia 429 se outra rodada tivesse consumido o direito dentro do intervalo mínimo — a asserção agora aceita 200 **ou** 429, que é a regra de negócio correta (o limitador não distingue "rodada nova" de "cliente ansioso" — e não deve).
- **Cenários de terminal revalidados ao vivo (pós-mudança):** veneno direto no tópico → DLQ com `dead-letter-reason` nos headers e fluxo seguindo (lançamento normal incorporado em seguida); consolidação PARADA → hit servido do cache (200) e miss respondendo 503 + `Retry-After: 30` — disjuntor e régua de veneno intactos com a stack completa.
- **Varredura de coerência (pedida pelo Leo):** 4 textos afirmavam Redis como parte da stack (ADR-001, ADR-003 ×2, comentário do pom raiz) — herança do bootstrap, corrigidos; menções legítimas (rejeições na ADR-004/006, upgrade documentado, equivalência Spring→Quarkus) mantidas. Contagens defasadas: "38 testes" → 41 (confirmado somando os Surefire XML — e descontando um XML órfão de teste-sonda removido em 07/07), "27 asserções" → 35, "7 ADRs" → 8, "2 contratos" → 3 (2 HTTP + 1 mensagem, conferido nos arquivos de pact). E "sessão da Sandy" → **do** Sandy (a correção de 11/07 pegou as ADRs mas escapou no uso-de-ia e no CLAUDE.md).
- **Registro de infra (fora do repo):** o encaminhamento de portas do Docker Desktop no Windows degradou com a stack no ar (containers Up, health interno verde, TODAS as portas do host em reset) — remédio: reiniciar o Docker Desktop; se o engine travar em 500, `wsl --shutdown` e subir de novo. Vale ensaiar: se a máquina suspender antes da banca, o sintoma é esse.

## 12/07 — Debug "o Grafana não mexia com um 400" (achado do PR #34, preservado aqui)

- **Investigação:** um `POST` que retorna `400` parecia "não mexer" o dashboard. Conclusão em três camadas: (1) o `400` **gera métrica sim** (`extrato_ingestao_lancamentos_total{resultado="rejeitado"}` sobe), mas o painel principal não plotava a série `rejeitado` — o dashboard ganhou a série `rate(...{resultado="rejeitado"}[1m])`; (2) um único 400 em janela de 1 min vira `~0.018 ops/s`, visualmente imperceptível ao lado das outras séries; (3) **a maior parte da confusão era instância errada de Grafana**: havia um Grafana 11.2.0 de OUTRA stack na `:3000`, enquanto o do projeto respondia na `:3001` (12.1.0) — mesma classe do choque de portas `obs-*` já registrado. Lição: ao "o gráfico não muda", confirme PRIMEIRO que você está no Grafana certo (versão/porta), depois a janela de scrape/refresh, depois a query.
- **Higiene:** o PR #34 deixou este debug como arquivo solto na raiz (`debug-grafana-400-metric.md`, título `[OPEN]`) e um snippet de veneno (`Envenena DLQ.md`, nome com espaço) que também vazou colado no fim do README. Limpo na branch `chore/limpeza-pos-pr34`: lição preservada aqui, arquivos de rascunho removidos, README consertado (snippet fora, 27→35 restaurado).

## 13/07 — Prints-chave da demo gerados pela IA (Plano D)

- **Pedido:** o Leo, indo dormir, pediu que a sessão fizesse autonomamente o que desse — prints, e vídeo se possível.
- **Escopo honesto:** vídeo **não** dá (exige captura de tela contínua da sessão — capability inexistente); dito isso ao Leo, sem fingir. Prints **dão**, e foi feito.
- **Método:** subir a stack real, exercitá-la com tráfego de verdade (`simular-cenario-real.sh` 150s + veneno no tópico + `stop consolidacao`) e capturar headless (`capture-website-cli`) o dashboard em 3 estados (fluxo saudável → DLQ vermelha → disjuntor/última-boa), o Swagger, a página de Actions (runs verdes) e o repo público. Os outputs de terminal reais (202+corr, headers de causa da DLQ, correlação nos 3 serviços, 503+Retry-After, `mvn verify` 41 testes) viraram uma folha de evidências estilizada. Tudo em `docs/prints/` (índice lá).
- **Limitação de ferramenta registrada:** GIF antes/depois não saiu — o `convert` do PATH no Windows é o utilitário de disco (shadowing do ImageMagick) e não há ffmpeg; os 3 PNGs de estado cobrem a evolução melhor que um GIF em slide. UI do RabbitMQ caiu na tela de login (SPA não aceita credencial inline na URL) — as filas foram lidas pela API e citadas no ato 8.

## Backlog de registros (preencher a cada incremento)

- [x] Resultado do mvn verify do Inc-1 + surpresas: `mvn verify -Pplano-b-jvm` — BUILD SUCCESS, 5 módulos, 7 testes, 0 falhas, ~2min12s, sem Docker. Sem surpresas nesta rodada (a pendência do plugin Quarkus/propriedades do tópico, deixada truncada numa sessão anterior, já tinha sido completada antes deste build).
- [x] Tradução `@RetryableTopic` → failure-strategy: funcionou, em duas camadas (ver registro de 07/07 do Inc-4).
- [x] PACT no Quarkus (Quarkiverse): documentado (registro de 07/07 do Inc-5 — não é membro do BOM da plataforma; pact como artefato versionado).
- [x] Plano A: DLQ física com headers de causa + serialização YearMonth no Rabbit — validado em 10/07, com dois bugs reais encontrados e corrigidos (ver registro "o dia em que o plano A pagou o aluguel").
