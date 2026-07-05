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

## Backlog de registros (preencher a cada incremento)

- [ ] Resultado do mvn verify do Inc-1 + surpresas.
- [ ] Tradução `@RetryableTopic` → failure-strategy: funcionou como a ADR-001 previu?
- [ ] PACT no Quarkus (Quarkiverse): documentar surpresas.
