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

## Backlog de registros (preencher a cada incremento)

- [x] Resultado do mvn verify do Inc-1 + surpresas: `mvn verify -Pplano-b-jvm` — BUILD SUCCESS, 5 módulos, 7 testes, 0 falhas, ~2min12s, sem Docker. Sem surpresas nesta rodada (a pendência do plugin Quarkus/propriedades do tópico, deixada truncada numa sessão anterior, já tinha sido completada antes deste build).
- [x] Tradução `@RetryableTopic` → failure-strategy: funcionou, em duas camadas (ver registro de 07/07 do Inc-4).
- [ ] PACT no Quarkus (Quarkiverse): documentar surpresas.
- [ ] Plano A: DLQ física com headers de causa + serialização YearMonth no Rabbit (ver registro do Inc-4).
