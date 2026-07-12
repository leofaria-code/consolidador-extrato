# ADR-001 — Stack: Quarkus 3.33 LTS + Java 25 LTS (em vez de Spring Boot)

- **Status:** aceita · 05/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo

## Contexto

O módulo BE-JV-010 é ministrado sobre Spring Boot (o projeto-guia de referência usa Spring Boot 3.3 + Java 21, com Spring AMQP, Spring Kafka/`@RetryableTopic`, Caffeine/Redis e PACT). A rubrica, porém, avalia **domínio dos padrões** (mensageria, idempotência, cache, resiliência, contratos), não a marca do framework. O grupo trabalha na Caixa, onde há movimento de adoção de Quarkus, e o próximo módulo do curso é "Introdução ao Quarkus". Desenvolvemos em máquinas pessoais (perfil A/Docker disponível), mantendo perfil B (pura-JVM) para testes.

## Alternativas consideradas

1. **Spring Boot 3.5.x** — alinhamento máximo com o material do curso e o gabarito; reaproveitamento direto do código das aulas; `@RetryableTopic` pronto. Custo: zero ganho estratégico para o grupo; conhecimento repetido do que as aulas já cobrem.
2. **Quarkus 3.33 LTS** — alinhamento com a direção tecnológica da Caixa e com o próximo módulo; Dev Services simplifica o perfil A (sobe Kafka/RabbitMQ sem docker-compose manual); mesma família Jakarta/MicroProfile. Custo: os padrões precisam ser **traduzidos** (não copiados) do gabarito; risco de arguição "por que não Spring?".
3. **Híbrido (Spring + 1 serviço Quarkus)** — demonstraria poliglotismo, mas duplicaria toolchain, complexidade de build e superfície de defesa na banca, sem aprofundar nenhum dos dois.

## Decisão

**Quarkus 3.33 LTS (BOM `io.quarkus.platform:quarkus-bom:3.33.2`) com Java 25 LTS**, Maven multi-módulo.

A tradução dos padrões, que passa a ser parte do aprendizado avaliável:

| Padrão do módulo (Spring, gabarito) | Equivalente adotado (Quarkus) |
|---|---|
| `@RabbitListener` / `RabbitTemplate` | SmallRye Reactive Messaging — connector RabbitMQ (`@Incoming`/`@Outgoing`) |
| Spring Kafka producer/consumer | SmallRye Reactive Messaging — connector Kafka |
| `@RetryableTopic` (retry + DLQ) | Estratégia de falha do connector Kafka (`failure-strategy=dead-letter-queue`) + SmallRye Fault Tolerance (`@Retry` com backoff) |
| Circuit breaker (Resilience4j) | SmallRye Fault Tolerance (`@CircuitBreaker`, `@Timeout`, `@Fallback`) |
| Spring Cache + Caffeine / Spring Data Redis | `quarkus-cache` (Caffeine) / `quarkus-redis-cache` |
| Spring Data JPA | Hibernate ORM com Panache |
| PACT (consumer/provider JUnit5) | PACT JVM via Quarkiverse (`quarkus-pact`) — pact em disco, Docker-free |
| Perfis Spring (`plano-a-docker`/`plano-b-jvm`) | Perfis Maven + `quarkus.profile` (`plano-a`/`plano-b`); Dev Services desligados no plano B |

## Consequências

- (+) Projeto vira ponte para o próximo módulo e para o contexto real da Caixa; evidência forte para o critério 7 (decisão com trade-off real) e critério 8 (a tradução foi feita com apoio crítico de IA — ver `docs/uso-de-ia.md`).
- (+) Dev Services reduz atrito do perfil A; connector in-memory do SmallRye dá perfil B de testes limpo (critério 6 exige testes sem Docker).
- (−) O gabarito do professor não é copiável — cada incremento exige tradução consciente (mitigação: a tabela acima + gabarito como referência conceitual).
- (−) `@RetryableTopic` citado na rubrica não existe em Quarkus — a defesa na banca deve apresentar a equivalência explicitamente (esta ADR é a evidência).
- (−) Risco de instabilidade Java 25 + Quarkus: mitigado pela escolha da LTS 3.33, primeira com suporte pleno a Java 25.
