# Consolidador de Extrato — Open Finance

Projeto final em grupo do módulo **BE-JV-010 — Arquitetura de Software Ágil II** (Escalação Tech · Dev. Back-End Java Especialista · turma Caixa).

**Tema:** Consolidador de extrato / Open Finance — ingestão por tópicos → agregação → cache de consulta.

**Grupo:** Leo (arquiteto) · Sandy (dev mensageria) · Marcos (dev cache/dados) · Rodrigo (dev testes/contrato)

## Estado atual

🚧 **Fase de requisitos concluída** — desenvolvimento em bootstrap.

| Entregável | Status |
|---|---|
| Pacote de requisitos (personas, 6 sessões de elicitação, user stories) | ✅ `docs/requisitos/` |
| ADRs e arquitetura | ⏳ próximo passo |
| Serviços (`extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta`) | ⏳ |
| AVALIACAO.md | ⏳ |

## Stack (decisão do grupo — ver futuro ADR-001)

Java 25 (LTS) · Quarkus 3.33 (LTS) · Maven multi-módulo · Docker Compose (perfil A) / pura-JVM (perfil B para testes).

## Como começar

Leia `docs/requisitos/README.md` — o pacote de requisitos é a entrada de todo o desenvolvimento. As user stories são a primeira fonte de verdade; em divergência, as transcrições prevalecem (a Sessão 6 corrige e precisa as anteriores).
