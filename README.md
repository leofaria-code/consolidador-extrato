# Consolidador de Extrato — Open Finance

Projeto final em grupo do módulo **BE-JV-010 — Arquitetura de Software Ágil II** (Escalação Tech · Dev. Back-End Java Especialista · turma Caixa).

**Tema:** Consolidador de extrato / Open Finance — ingestão por tópicos → agregação → cache de consulta.

**Grupo:** Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato)

## Estado atual

🚧 **Incrementos 1–6 concluídos** — falta consolidar a avaliação e ensaiar a banca (antecipada para 13–14/07/2026).

| Entregável | Status |
|---|---|
| Pacote de requisitos (personas, 6 sessões de elicitação, user stories) | ✅ `docs/requisitos/` |
| ADR-001 (stack) · ADR-002 (decomposição) · `docs/arquitetura.md` | ✅ |
| Esqueleto multi-módulo (4 módulos, REST + health + smoke tests) | ✅ |
| Incremento 1 — tópico de ingestão + consumidor idempotente (Kafka) | ✅ |
| Incremento 2 — consolidação + base segregada + evento `posicao-atualizada` (ADR-004, ADR-005) | ✅ |
| Incremento 3 — cache na consulta + invalidação + carimbo (ADR-006) | ✅ |
| Incremento 4 — fila de reconsolidação (RabbitMQ) + retry/DLQ (ADR-007) | ✅ |
| Incremento 6 — observabilidade: correlação ponta a ponta + logs JSON | ✅ |
| Incremento 5 — contract test PACT consulta↔consolidação | ✅ |
| AVALIACAO.md preenchido + docker-compose/demo + ensaio da banca | ⏳ 12/07 |

## Pré-requisitos

- **Java 25** (LTS) e **Maven 3.9+** na `PATH`.
- **Docker** — só necessário para o perfil A (`plano-a-docker`), que sobe Kafka/RabbitMQ/Redis via Quarkus Dev Services. O perfil B (`plano-b-jvm`) roda 100% sem Docker.

## Instalar dependências

O projeto é um reactor multi-módulo (`shared-contracts` → `extrato-ingestao` / `extrato-consolidacao` / `extrato-consulta`); o Maven resolve a ordem de build automaticamente. Não há passo de "install" separado — o primeiro `mvn` abaixo já baixa tudo.

```bash
mvn -q dependency:go-offline   # opcional: baixa todas as dependências antes do primeiro build
```

## Compilar

```bash
mvn clean compile          # compila todos os módulos, sem rodar testes
mvn clean package          # compila e gera os artefatos (JARs) em cada módulo/target
```

## Testar

```bash
mvn clean test              # testes unitários de todos os módulos
mvn clean verify            # testes unitários + integração (perfil A por padrão — usa Docker quando necessário)
mvn clean verify -Pplano-b-jvm   # mesma suíte, perfil B — pura-JVM, sem Docker (critério 6: tem que passar assim)
mvn -pl extrato-ingestao verify -Pplano-b-jvm   # só um módulo, perfil B
```

## Rodar em modo dev

Cada serviço sobe individualmente com `quarkus:dev` (live reload). Portas fixas por módulo:

| Módulo | Porta | Comando |
|---|---|---|
| `extrato-ingestao` | 8081 | `mvn -pl extrato-ingestao quarkus:dev` |
| `extrato-consolidacao` | 8082 | `mvn -pl extrato-consolidacao quarkus:dev` |
| `extrato-consulta` | 8083 | `mvn -pl extrato-consulta quarkus:dev` |

Em modo dev o perfil A é o padrão: o Quarkus Dev Services sobe automaticamente os brokers/cache via Docker quando o serviço precisa deles.

## Perfis de execução

| Perfil | Quando usar | Comportamento |
|---|---|---|
| `plano-a-docker` (padrão, `-Pplano-a-docker` implícito) | Demo completa, dev local com Docker disponível | Dev Services sobe Kafka/RabbitMQ/Redis reais |
| `plano-b-jvm` (`-Pplano-b-jvm`) | CI, ambientes sem Docker, banca | Dev Services desligado; conectores in-memory + Caffeine local — `mvn verify -Pplano-b-jvm` **tem que passar** (critério 6) |

## Stack (ADR-001)

Java 25 (LTS) · Quarkus 3.33.2 (LTS, BOM `io.quarkus.platform`) · Maven multi-módulo · SmallRye Reactive Messaging (Kafka/RabbitMQ) · SmallRye Fault Tolerance · quarkus-cache/redis-cache · Panache · PACT (Quarkiverse).

## Como começar

Leia `docs/requisitos/README.md` — o pacote de requisitos é a entrada de todo o desenvolvimento. As user stories são a primeira fonte de verdade; em divergência, as transcrições prevalecem (a Sessão 6 corrige e precisa as anteriores).
