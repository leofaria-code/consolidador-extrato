# ADR-003 — Perfis de execução A/B: suíte de testes Docker-free + integração de alta fidelidade

- **Status:** aceita · 05/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Relaciona-se com:** ADR-001 (stack Quarkus — Dev Services e connector in-memory habilitam esta decisão), ADR-002 (fronteiras de mensageria são o que se troca por transporte fake)

## Contexto

A stack é assíncrona por natureza: Kafka (ingestão), RabbitMQ (reconsolidação) e Redis (cache). Toda dependência dessas puxa, por padrão, um broker de verdade — e o Quarkus Dev Services sobe esses containers automaticamente em dev/test quando há Docker.

Dois fatos empurram em direções opostas:

1. **A rubrica exige teste sem Docker.** O critério 6 (testabilidade, peso 13) e o contrato de entrega §3 pedem que `mvn verify` passe **sem daemon Docker**. A correção/banca precisa clonar o repositório e obter suíte verde numa máquina qualquer — inclusive máquina corporativa Caixa, frequentemente travada para Docker. Teste que depende de container não roda ali, é mais lento e é mais *flaky*, e — o pior conceitualmente — acopla o *resultado do teste* à disponibilidade de infra em vez de à correção do código.
2. **Integração de verdade só o broker prova.** Ordenação por partição, serialização sobre a rede, semântica de offset/rebalance de consumer group e o comportamento de DLQ no nível do broker não existem num transporte fake. Testar só em memória dá **falsa sensação de segurança**.

Desenvolvemos em máquinas pessoais com Docker disponível (perfil A viável localmente), mas o caminho de CI/correção tem de ser Docker-free.

## Alternativas consideradas

1. **Só testes com Docker (Dev Services/Testcontainers).** Máxima fidelidade — todo teste roda contra broker real. Custo: **viola o critério 6** (não passa sem daemon); suíte lenta e sujeita a flutuação de subida de container; inviável na máquina de correção sem Docker. Descartada como caminho único.
2. **Só testes sem Docker (in-memory).** Suíte rápida, determinística e sempre verde em qualquer lugar. Custo: **nunca** exercita broker real — particionamento, serialização de rede, offset/rebalance e DLQ de broker ficam sem cobertura. Passa a rubrica, mas mente sobre a robustez da integração. Descartada como caminho único.
3. **Dois perfis: A (Docker, alta fidelidade) + B (pura-JVM, Docker-free, gate obrigatório).** Cada perfil cobre uma camada da pirâmide: B prova a correção do domínio e da fiação rápido e em qualquer ambiente; A prova a integração real com o broker. Custo: configuração duplicada e disciplina para manter os dois caminhos. **Escolhida.**

## Decisão

Dois perfis Maven, mais o perfil conceitual (contrato de entrega §3):

- **`plano-a-docker`** (padrão, `activeByDefault`): brokers e cache reais via Quarkus Dev Services (Kafka/RabbitMQ/Redis sobem sozinhos quando há Docker). Perfil de integração de alta fidelidade.
- **`plano-b-jvm`** (**o gate**): `mvn verify -Pplano-b-jvm` **tem de passar** — é o que roda em CI e na correção. Desliga Dev Services (`quarkus.devservices.enabled=false`); a mensageria usa o connector in-memory do SmallRye, o cache usa Caffeine local e a persistência usa H2.
- **`plano-c`** (conceitual): não requer build — coberto por `docs/adr/` e `docs/arquitetura.md`.

**Mecanismo técnico que torna o plano B barato:** a lógica de domínio não conhece o transporte. Consumidor e publicador falam MicroProfile Reactive Messaging (`@Incoming`/`@Outgoing`) sobre canais nomeados, nunca Kafka diretamente. No teste, `RecursosEmMemoria` faz `InMemoryConnector.switchIncomingChannelsToInMemory(...)` — troca o Kafka por um fake **apenas via configuração, sem tocar no código de produção**. O mesmo `ConsumidorLancamentos` que roda contra Kafka real no plano A é exercitado contra transporte fake no plano B. É o desacoplamento da ADR-002 pagando dividendo em testabilidade.

## Consequências

- (+) **Critério 6 satisfeito por construção**: a suíte é Docker-free por padrão de projeto, não por acaso. Feedback rápido e determinístico a cada commit.
- (+) **Mesma lógica exercitada nos dois perfis** — só o transporte muda. O teste do domínio não precisa de broker, o que é evidência concreta do desacoplamento (critérios 1 e 7).
- (+) **Banca/correção roda verde sem montar infra.**
- (−) **O plano B não cobre** ordenação por partição real, serialização de rede, offset/rebalance de consumer group nem DLQ no nível do broker. *Mitigação:* o plano A existe exatamente para isso; padrões que só o broker prova (ex.: DLQ do Incremento 4) exigem teste também no plano A — não podem depender só de B.
- (−) **Custo de manter dois caminhos**: config duplicada e disciplina — toda feature de mensageria nova precisa do switch in-memory correspondente no teste. *Mitigação:* `RecursosEmMemoria` centraliza o switch por módulo.
- (−) **Risco de divergência A↔B**: um teste passar em B e quebrar em A (ou vice-versa). *Mitigação:* rodar o plano A pelo menos antes de fechar cada incremento, não só o B do dia a dia.

## Nota de rastreabilidade

Esta decisão não estava entre os cinco ADRs candidatos mapeados na Sessão 6 — ela emergiu do cruzamento entre o **critério 6 da rubrica** e a **stack da ADR-001** (Dev Services + connector in-memory). Registrada aqui porque a defesa na banca à pergunta "por que testam sem Docker / por que dois perfis?" precisa de um porquê escrito, não de "porque funcionou".
