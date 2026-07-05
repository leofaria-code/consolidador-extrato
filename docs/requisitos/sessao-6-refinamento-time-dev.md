# Sessão 6 — Refinamento com o time de desenvolvimento

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 03/07/2026 (sexta-feira), 15h00–15h45
- **Duração:** 45 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO), Fábio Cardim (Arquiteto de Soluções) e o **time de desenvolvimento**: Leo (arquiteto do time), Sandy (dev mensageria), Marcos (dev cache/dados), Rodrigo (dev testes/contrato)

> **Natureza desta sessão:** diferente das Sessões 1–5 (descoberta, negócio + arquiteto), esta é a sessão de **refinamento** — o time que vai construir lê criticamente o pacote (princípio *hands-on modelers* do DDD: a linguagem ubíqua pertence a quem implementa, não só a quem desenha) e fecha as dúvidas antes da primeira linha de código. As dúvidas sem resposta de negócio viram **decisões do time, documentadas em ADR**.

---

**Renata:** Pessoal, vocês receberam a compilação hoje de manhã, com a revisão da Dra. Patrícia. Essa sessão é de vocês: perguntem tudo. O que eu e o Fábio não respondermos, a gente marca como decisão de vocês — mas documentada, por favor.

**Leo:** Começamos por uma confirmação de leitura. A gente mapeou três contextos no pacote: **ingestão** (Sessão 2), **consolidação** (Sessões 3 e 4) e **consulta** (Sessão 3). Cada um com vocabulário e ritmo próprios — ingestão fala de esteira e idempotência, consolidação fala de posição e competência, consulta fala de cache e carimbo. Nossa intenção é um serviço por contexto, **cada um com sua base**. Bate com a visão de vocês?

**Fábio:** Bate e é o corte que eu faria. Só cravem a regra que importa: **nenhum serviço lê a base do outro**. Se a consulta precisa de algo da consolidação, pede pela porta da frente.

**Marcos:** Essa era exatamente a minha primeira dúvida. A consulta serve a posição consolidada — mas a posição **mora** na base da consolidação. A consulta tem base própria com uma cópia, ou busca na consolidação quando o cache falha?

**Fábio:** Decisão de vocês — os requisitos só exigem a latência e a regra de não-acoplamento por dados. Os dois desenhos são defensáveis: réplica própria alimentada por evento é mais desacoplada e mais complexa; cache + chamada interna à consolidação é mais simples e cria uma dependência síncrona no *cache miss*. Escolham, meçam o trade-off e escrevam o porquê.

**Leo:** Vira ADR. *(anotado: ADR — origem do dado na consulta em cache miss)*

**Rodrigo:** Se for chamada interna, essa API consolidação→consulta é um **contrato entre serviços** — candidata natural pro nosso contract test. Prefiro isso a testar contrato de tela.

**Fábio:** Concordo, é o par HTTP mais estável do sistema. O contrato de mensagem do tópico de ingestão é o segundo candidato, se quiserem ir além.

**Sandy:** Minhas dúvidas são da esteira. Primeira: idempotência. A US-02 manda ignorar o repetido, mas não diz **como lembrar** do que já foi processado. Guardamos toda identidade já vista, pra sempre? Isso cresce sem parar.

**Fábio:** O requisito de negócio é "nunca duplicar na tela". O mecanismo é de vocês. Só noto que vocês têm uma vantagem: o lançamento incorporado **já fica guardado** com a identidade dele — a própria base, com unicidade na identidade, é uma memória de deduplicação que não expira. Se preferirem uma janela separada de chaves recentes por desempenho, ok — mas documentem o que acontece com o repetido que chega **depois** da janela.

**Sandy:** Justo. *(anotado: ADR — mecanismo de idempotência: unicidade na base × janela de deduplicação)*. Segunda dúvida, a mais cabeluda: a US-05 diz "incorporou lançamento, atualizou posição" e a US-10 diz "atualizou posição, publicou evento". São **três efeitos** — gravar lançamento, atualizar posição, publicar evento. Se o serviço cair no meio, o que pode ficar pela metade?

**Fábio:** Pergunta de gente grande. Resposta de negócio, pra calibrar o rigor: o que **não pode** é lançamento incorporado sumir ou posição divergir dos lançamentos pra sempre. O evento pode **atrasar** e pode **repetir** — os consumidores dele (inclusive a invalidação do cache de vocês) que sejam idempotentes. Com essa tolerância, vocês não precisam de transação distribuída; precisam de uma ordem de efeitos bem pensada. O desenho exato — o que vai junto na transação, como garantir que o evento sai mesmo com queda — é ADR de vocês.

**Leo:** *(anotado: ADR — consistência dos três efeitos e garantia de publicação do evento; premissa aceita: evento com entrega "pelo menos uma vez", consumidores idempotentes)*

**Sandy:** Última da esteira: a Sessão 4 fala "re-tenta com intervalos crescentes" e "depois de algumas tentativas, fila morta". **Quantas** tentativas, **quais** intervalos?

**Renata:** Negócio não tem número pra isso — a decisão da Sessão 4 registrou "parâmetros são decisão técnica documentada do time".

**Sandy:** Então proponho já deixar em ata do refinamento: **3 re-tentativas com backoff exponencial** como ponto de partida, ajustável por configuração, e racional documentado no ADR de resiliência. *(anotado)*

**Marcos:** Voltando ao cache, duas miúdas. O **carimbo** "atualizado às HH:MM" é a hora da última atualização **da posição**, certo? Não a hora em que o cache foi populado?

**Renata:** Da posição. O cliente quer saber o quão fresco é o **dado**, não a infraestrutura. Boa pegadinha, aliás — escreve isso no critério de aceite de vocês.

**Marcos:** E o **atualizar sob demanda**: ele ignora o cache e vai ao dado. Um cliente ansioso apertando dez vezes seguidas vira dez leituras caras. Posso limitar?

**Fábio:** Deve. Um limite por cliente — na casa de segundos entre atualizações forçadas — protege o sistema sem ferir a transparência. Parâmetro de vocês, documentado.

**Rodrigo:** Bloco de testes e compliance. Primeiro: pra testar a US-11, o **expurgo**, eu preciso saber quem é o dono da verdade sobre consentimentos. Existe um "serviço de consentimento" no nosso escopo?

**Fábio:** Não no MVP — o arranjo do Open Finance tem plataformas próprias pra isso. Pro nosso sistema, consentimento é **informação que chega**: o lançamento traz o `id_consentimento`, e a revogação/vencimento chega como **evento** que vocês consomem. Vocês precisam **reagir** (bloquear ingestão, expurgar), não **gerir** o ciclo de vida. No projeto, simulem a fonte de consentimentos como simulam as transmissoras.

**Rodrigo:** Perfeito, escopo fechado. Segundo: a trilha de auditoria da US-12 — "quem consultou" — no MVP a consulta vem do app. A gente vai ter autenticação de verdade?

**Renata:** Fora de escopo a autenticação em si — assumam que o canal entrega a identidade de quem consulta. A trilha registra o que o canal informar. É o registro que é requisito, não o mecanismo de login.

**Rodrigo:** Terceiro, uma **ambiguidade** que achamos na leitura — exercício de erratum. Na Sessão 2, a ficha mínima do Fábio lista a `descricao` entre os campos, mas logo depois ele diz que ela "pode vir vazia de instituição pequena". A US-01 marcou descrição como **opcional**. Confirmam que a US está certa e a fala do meio da ficha é que foi imprecisa?

**Fábio:** Confirmo — descrição é opcional, exibe-se um genérico. Bem caçado; é exatamente o tipo de divergência que vocês devem registrar.

**Renata:** E fica registrado **aqui**, que é o que importa: transcrição da Sessão 6 corrige a ambiguidade da Sessão 2.

**Leo:** Fechamento nosso, então. Saímos com: três serviços — nossos nomes de trabalho: `extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta` —, bases segregadas, regra "ninguém lê a base do outro"; contract test no par consulta↔consolidação; e cinco decisões técnicas que viram ADR: origem do dado no cache miss, mecanismo de idempotência, consistência dos três efeitos, parâmetros de resiliência, e o corte de domínio em si. Premissas aceitas por negócio: evento pode atrasar e repetir; carimbo é do dado; atualizar sob demanda com limite; consentimento é externo — a gente reage.

**Renata:** Assino a ata. Time, é de vocês. Qualquer conflito entre o que eu compilei e o que está nas transcrições — as transcrições mandam, e agora esta aqui inclusive corrige uma.

**Fábio:** Boa sorte, e lembrem: quando a banca — quer dizer, o comitê de arquitetura — perguntar "por que assim?", a resposta certa nunca é "porque funcionou". É "porque consideramos X e Y, e escolhemos Y por isso". Escrevam os porquês.

---

## Decisões da Sessão 6

1. **Corte de domínio confirmado:** três contextos/serviços — `extrato-ingestao`, `extrato-consolidacao`, `extrato-consulta` — com **bases segregadas** e a regra "nenhum serviço lê a base do outro" (acesso só por API ou evento).
2. **Contract test:** par prioritário é **consulta ↔ consolidação** (API interna de posição); contrato de mensagem do tópico de ingestão é candidato a segundo par.
3. **Premissas de consistência aceitas por negócio:** o evento "posição atualizada" pode **atrasar** e **repetir** (entrega "pelo menos uma vez"); todos os consumidores — inclusive a invalidação de cache — devem ser idempotentes. Lançamento incorporado não some; posição converge para os lançamentos.
4. **Carimbo "atualizado às"** = hora da última atualização **da posição** (do dado, não do cache).
5. **Atualizar sob demanda** terá limite de frequência por cliente (parâmetro do time, documentado).
6. **Consentimento é externo ao escopo:** o sistema **reage** (bloqueio de ingestão + expurgo ao consumir evento de revogação/vencimento); não gere ciclo de vida. Fonte de consentimentos será simulada, como as transmissoras.
7. **Autenticação fora de escopo:** o canal entrega a identidade; a trilha de auditoria registra o informado.
8. **Parâmetros de resiliência** (ponto de partida): 3 re-tentativas com backoff exponencial, configurável; racional em ADR.
9. **Erratum #1:** `descricao` do lançamento é **opcional** (US-01 correta); a ficha falada na Sessão 2 foi imprecisa. Corrigido nesta ata.

## Dúvidas do time → ADRs candidatos

| # | Decisão em aberto | ADR candidato |
|---|---|---|
| 1 | Corte de domínio e bases segregadas (registro do porquê) | ADR-002 — decomposição por contexto |
| 2 | Origem do dado na consulta em *cache miss*: réplica própria × chamada interna | ADR-00X — desenho da consulta |
| 3 | Mecanismo de idempotência: unicidade na base × janela de deduplicação | ADR-00X — idempotência |
| 4 | Consistência dos três efeitos (gravar, consolidar, publicar) e garantia de publicação | ADR-00X — consistência e publicação de eventos |
| 5 | Parâmetros e mecanismo de re-tentativa/DLQ | ADR-00X — resiliência |

*(ADR-001 já reservado para a escolha da stack.)*

## Action items

- **Leo:** abrir o repositório com a estrutura de entrega e os ADRs 001 e 002.
- **Sandy:** prototipar o consumo do tópico de ingestão com idempotência (spike curto antes do ADR #3).
- **Marcos:** medir os dois desenhos da consulta (ADR #2) e trazer números.
- **Rodrigo:** montar o esqueleto do contract test consulta↔consolidação e do teste "reprocessa sem duplicar" (US-02).
