# Consolidador de Extrato — Open Finance

Projeto final em grupo do módulo **BE-JV-010 — Arquitetura de Software Ágil II** (Escalação Tech · Dev. Back-End Java Especialista · turma Caixa).

**Tema:** Consolidador de extrato / Open Finance — ingestão por tópicos → agregação → cache de consulta.

**Grupo:** Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato)

## Estado atual

🚧 **Bootstrap concluído** — incrementos de mensageria/cache/testes em andamento.

| Entregável | Status |
|---|---|
| Pacote de requisitos (personas, 6 sessões de elicitação, user stories) | ✅ `docs/requisitos/` |
| ADR-001 (stack) · ADR-002 (decomposição) · `docs/arquitetura.md` | ✅ |
| Esqueleto multi-módulo (4 módulos, REST + health + smoke tests) | ✅ |
| AVALIACAO.md (esqueleto, preenchido por incremento) · `docs/uso-de-ia.md` | ✅ em construção |
| Incrementos: tópico ingestão → consolidação/evento → cache → fila/DLQ → PACT → observabilidade | ⏳ |

## Como rodar (esqueleto)

```bash
mvn verify                 # perfil A (padrão) — Dev Services usa Docker quando necessário
mvn verify -Pplano-b-jvm   # perfil B — pura-JVM, sem Docker (perfil dos testes/CI)
mvn -pl extrato-consulta quarkus:dev   # um serviço em dev mode (portas: 8081/8082/8083)
```

## Stack (decisão do grupo — ver futuro ADR-001)

Java 25 (LTS) · Quarkus 3.33 (LTS) · Maven multi-módulo · Docker Compose (perfil A) / pura-JVM (perfil B para testes).

## Como começar

Leia `docs/requisitos/README.md` — o pacote de requisitos é a entrada de todo o desenvolvimento. As user stories são a primeira fonte de verdade; em divergência, as transcrições prevalecem (a Sessão 6 corrige e precisa as anteriores).
