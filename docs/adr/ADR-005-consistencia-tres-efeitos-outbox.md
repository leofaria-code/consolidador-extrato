# ADR-005 — Consistência dos três efeitos: transação local única + outbox transacional

- **Status:** aceita · 07/07/2026
- **Decisores:** Leo, Sandy, Marcos, Rodrigo
- **Origem:** ADR candidato #4 da Sessão 6 (dúvida da Sandy: "são três efeitos — se o serviço cair no meio, o que pode ficar pela metade?")
- **Relaciona-se com:** ADR-004 (a dedup na base é o que torna a reentrega segura), ADR-002 (evento é a fronteira consolidação→mundo)

## Contexto

Ao consumir um lançamento, a consolidação produz **três efeitos**: (a) gravar o lançamento incorporado, (b) atualizar a posição consolidada, (c) publicar o evento `posicao-atualizada` (US-05 + US-10). Há ainda um quarto ato implícito: (d) **marcar a mensagem como processada** (commit de offset no Kafka).

Tolerâncias fixadas pelo negócio na Sessão 6 (Fábio): o que **não pode** é lançamento incorporado sumir ou posição divergir dos lançamentos para sempre; o evento **pode atrasar e pode repetir** — consumidores dele (inclusive a invalidação de cache) devem ser idempotentes. *"Vocês não precisam de transação distribuída; precisam de uma ordem de efeitos bem pensada. Como garantir que o evento sai mesmo com queda — é ADR de vocês."*

## Alternativas consideradas

1. **Transação distribuída (XA/2PC) abrangendo banco + broker** — garantiria atomicidade total, mas o Fábio dispensou explicitamente, o custo operacional é alto e o cliente Kafka não participa de XA — na prática nem é implementável na stack. Descartada.
2. **Publicar após o commit da transação** — commit de (a)+(b), depois `emitter.send`. Simples; porém queda **entre o commit e o publish perde o evento**. O TTL do cache (US-06) corrige a divergência com atraso, mas "evento pode se perder" contraria o espírito do *"garantir que o evento sai mesmo com queda"* e enfraquece a defesa; além disso outros assinantes futuros do evento (BI, notificação — US-10) não têm TTL para socorrê-los.
3. **Outbox transacional** — (a), (b) e o **registro do evento pendente** (`evento_pendente`) entram na **mesma transação local**; um publicador assíncrono (`@Scheduled`) lê os pendentes, publica no tópico e marca como publicado **após o ack do broker**. Queda em qualquer ponto não perde evento — no máximo atrasa ou repete, exatamente a tolerância aceita. Custo: uma tabela e um poller a mais, e latência extra de até um intervalo do scheduler.

## Decisão

**Alternativa 3 — outbox transacional.** Ordem dos efeitos e comportamento em queda:

| # | Ato | Mecanismo |
|---|-----|-----------|
| 1 | Verificar dedup + gravar lançamento + atualizar posição + gravar evento pendente | **Uma transação local** no banco da consolidação (ADR-004 participa dela) |
| 2 | Marcar mensagem como processada | Retorno do método `@Incoming` → commit de offset (estratégia padrão do connector Kafka) |
| 3 | Publicar `posicao-atualizada` | Publicador `@Scheduled` varre `evento_pendente` não publicados, envia ao tópico e marca `publicado_em` **somente após o ack** do broker |

**Análise de queda ponto a ponto** (a defesa da banca):

- **Queda durante o passo 1** → rollback total: nada gravado, offset não commitado, mensagem reentregue e reprocessada do zero. Nenhum efeito parcial.
- **Queda entre 1 e 2** (transação commitada, offset não) → mensagem reentregue; a dedup da ADR-004 a reconhece como repetida e ignora. Sem duplicação.
- **Queda entre commit e publicação (antes do passo 3)** → o evento está **persistido** na outbox; ao religar, o scheduler publica. Evento atrasa, não se perde.
- **Queda entre o envio ao broker e a marcação de `publicado_em`** → na retomada o evento é reenviado: **repetição**, tolerada por premissa (Sessão 6, decisão 3 — consumidores idempotentes; a invalidação de cache é naturalmente idempotente).

Garantia resultante: efeitos (a) e (b) são **atômicos entre si**; o evento (c) tem entrega **"pelo menos uma vez"**; o conjunto converge sem transação distribuída.

## Consequências

- (+) Responde diretamente ao desafio da Sessão 6: o evento sai **mesmo com queda**, com prova ponto a ponto de cada janela de falha.
- (+) Assinantes futuros do evento (US-10: BI, notificação) herdam a garantia — não dependem do TTL do cache como socorro.
- (+) A outbox dá **observabilidade de graça**: eventos pendentes são consultáveis (fila represada = sintoma visível), e `publicado_em` é trilha de auditoria da publicação.
- (−) Latência extra de até um intervalo do scheduler (padrão 2s, configurável) entre a consolidação e a invalidação do cache. *Aceitável:* a meta de frescor é < 5 min (US-05).
- (−) Uma tabela e um componente a mais para manter. *Mitigação:* o publicador é ~1 classe; a tabela carrega só referência (sem dado pessoal além do id opaco do cliente — minimização da US-10 preservada).
- (−) Repetição de evento é possível por design. *Não é risco:* premissa aceita por negócio; consumidores obrigados a idempotência desde a Sessão 6.
