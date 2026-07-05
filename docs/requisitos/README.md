# Requisitos — Consolidador de Extrato / Open Finance

Pacote de **elicitação de requisitos** do projeto final em grupo do módulo **BE-JV-010** (turma Caixa Econômica Federal, nível especialista). Simula a coleta de requisitos de um sistema real e serve de **entrada** para o desenvolvimento — no mesmo formato do pacote de referência do projeto-guia (API de Comprovantes de PIX).

## O sistema, em uma frase

Um consolidador que **ingere os lançamentos das contas do cliente — na Caixa e em outras instituições, via Open Finance — e serve o extrato consolidado no app**. Ele **não movimenta dinheiro** — só lê, consolida e exibe (fluxo: ingestão por tópicos → agregação → cache de consulta).

## O que tem neste pacote

| Arquivo | O que é |
|---|---|
| `personas.md` | Ficha de cada participante das reuniões (papel, objetivos, jeito de falar, vieses). |
| `sessao-1-kickoff.md` | Transcrição — contexto Open Finance, dor do cliente multibanco, escopo. |
| `sessao-2-ingestao.md` | Transcrição — ingestão por tópico, ficha do lançamento, idempotência, fora de ordem. |
| `sessao-3-consolidacao-e-consulta.md` | Transcrição — consolidação contínua, cache, invalidação, carimbo de atualização. |
| `sessao-4-confiabilidade-e-integracoes.md` | Transcrição — não perder lançamento, DLQ, reconsolidação por fila, eventos. |
| `sessao-5-compliance-e-fechamento.md` | Transcrição — consentimento, revogação e expurgo, finalidade, auditoria, fechamento. |
| `user-stories.md` | Documento compilado pela PO — **primeira fonte de verdade**, com glossário de linguagem ubíqua e rastreabilidade por sessão. |
| `sessao-6-refinamento-time-dev.md` | Transcrição — **refinamento com o time de desenvolvimento**: leitura crítica do pacote, dúvidas fechadas com PO/arquiteto, erratum e decisões técnicas encaminhadas para ADR. |

## Como usar

1. **Comece pelas `user-stories.md`** — é o documento que a PO entregou ao time como ponto de partida, já com o glossário da linguagem ubíqua.
2. **Recorra às transcrições** para entender o contexto de cada decisão (por que existe, qual dor resolve, quem pediu). Cada user story indica a sessão-fonte na tabela de rastreabilidade.
3. **Em caso de divergência entre user story e transcrição, a transcrição prevalece** — ela é o registro do que os stakeholders efetivamente pediram. Fábio Cardim (arquiteto) é a voz canônica dos fatos técnicos; a Dra. Patrícia Yano, dos regulatórios.
4. A **Sessão 6 (refinamento)** é posterior à compilação e **corrige/precisa** o que veio antes (contém o erratum #1). As Sessões 1–5 são de **descoberta** (negócio + arquiteto); os devs entram no refinamento — desenho intencional, alinhado ao momento de cada fase (*inception* × *refinement*) e ao princípio *hands-on modelers* do DDD: é nela que o time se apropria da linguagem ubíqua e converte dúvidas em ADRs.

## Relação com o projeto

Estes requisitos alimentam o projeto final do grupo e mapeiam, de forma natural, os padrões trabalhados no módulo:

- DDD e contextos delimitados (ingestão · consolidação · consulta);
- ingestão por **tópicos** (publica-assina) e consumidor **idempotente**;
- **cache** com invalidação por evento + TTL;
- **fila** de trabalho (reconsolidação) — producer/consumer;
- **resiliência** (re-tentativa com backoff, **DLQ**);
- **eventos** para integração entre áreas sem acoplamento;
- **observabilidade** (logs estruturados sem dado pessoal + correlação de id — exigência da Sessão 5);
- e **contratos** entre serviços.

Os detalhes de implementação (stack, perfis de execução A/B/C, ADRs) estão em `../adr/` e `../arquitetura.md`. **Este pacote é sobre o quê e o porquê — o domínio e os requisitos —, não sobre o como.**

## Nota de método (uso de IA)

Este pacote foi **gerado com apoio de IA** a partir do formato do pacote de referência do docente, e **revisado criticamente pelo grupo** (coerência das decisões entre sessões, plausibilidade regulatória do Open Finance, consistência das vozes). O registro dessa colaboração faz parte da evidência do critério 8 (uso crítico de IA) — ver `../uso-de-ia.md`.
