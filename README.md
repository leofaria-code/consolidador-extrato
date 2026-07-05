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
mvn verify -Pplano-b-jvm   # perfil B — pura-JVM, sem Docker (perfil 