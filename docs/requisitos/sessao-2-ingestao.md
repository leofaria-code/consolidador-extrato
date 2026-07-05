# Sessão 2 — Ingestão: como os lançamentos chegam

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 08/06/2026 (segunda-feira), 14h00–14h30
- **Duração:** 30 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO, facilitadora), Fábio Cardim (Arquiteto de Soluções), Juliana Prates (Experiência do Cliente), Márcio Bandeira (Gerente de Produto)

---

**Renata:** Sessão 2, pessoal. Tema: **ingestão** — de onde vêm os lançamentos, como chegam, quais campos têm, e o famoso reenvio duplicado que a Juliana levantou. Fábio, você prometeu uma esteira de aeroporto.

**Fábio:** Prometi e trouxe. Imagina a esteira de bagagem: malas chegam o tempo todo, de vários voos, misturadas, sem hora marcada. Você não liga pro aeroporto pedindo "me manda a mala agora" — você fica na esteira e pega o que vai chegando. A nossa ingestão é isso: um **fluxo contínuo** de lançamentos, de várias origens — os conectores que falam com cada transmissora e os sistemas internos da Caixa —, publicados num canal do tipo **publica-assina, um tópico**, e o nosso sistema fica assinado nesse canal consumindo o que chega.

**Márcio:** Por que assim e não o contrário — a gente pergunta pra cada banco "o que tem de novo"?

**Fábio:** Três razões. Primeira: **desacoplamento** — quem publica não precisa conhecer quem consome; amanhã outra área da Caixa quiser esses mesmos lançamentos, assina o mesmo canal, e ninguém renegocia nada. Segunda: **pico** — o canal funciona como a esteira: se chegarem dez mil malas de uma vez, elas se acumulam na esteira e a gente vai pegando no nosso ritmo; ninguém derruba ninguém. Terceira: **é o formato natural do dado** — lançamento é um fato que aconteceu, um evento; ele nasce como notícia, não como resposta a pergunta.

**Renata:** Glossário: **ingestão por tópico** — os lançamentos chegam publicados num canal publica-assina, de forma contínua, e o sistema consome no seu ritmo. Agora vamos ao conteúdo: o que vem em cada lançamento?

**Fábio:** Proponho a ficha mínima. Cada lançamento traz: o **identificador do lançamento na origem** — cada transmissora dá um código único pro lançamento dela; a **instituição de origem** — o código da instituição, o ISPB; a **conta** — agência e número, do jeito que a origem identifica; o **tipo** — crédito ou débito; o **valor** e a **moeda**; a **data e hora** em que o lançamento ocorreu; uma **descrição** — "COMPRA CARTAO MERCADO X", "PIX RECEBIDO FULANO"; e o **identificador do consentimento** que autorizou aquele dado a chegar até nós.

**Juliana:** Por que o consentimento vem em cada lançamento? Não bastava saber que o cliente consentiu uma vez?

**Fábio:** Pela rastreabilidade que a Dra. Patrícia vai cobrar na Sessão 5: cada dado pessoal dentro de casa precisa apontar pra **base legal** que o trouxe. Se o cliente revogar o consentimento X, eu preciso saber exatamente quais dados entraram por ele. Vem no lançamento e a gente guarda.

**Renata:** Anotei a ficha. Alguma coisa opcional?

**Fábio:** A **categoria da origem** — algumas transmissoras já mandam o lançamento classificado, "alimentação", "transporte". Quando vier, a gente guarda; não vem sempre, então é opcional. E a **descrição** pode vir vazia de instituição pequena — a gente exibe um genérico. O resto da ficha é obrigatório: sem identificador, sem conta, sem valor ou sem data, o lançamento é inválido.

**Márcio:** E o que acontece com um lançamento inválido? Devolve pra quem mandou?

**Fábio:** Não tem "devolver" na esteira — quem publicou já foi embora. Lançamento inválido a gente **separa para inspeção, sem descartar** — vou detalhar o mecanismo na Sessão 4, tem nome técnico. O que não pode é: nem travar a esteira por causa dele, nem fingir que ele não existiu.

**Renata:** Guardado pra Sessão 4. Agora o prato principal: Juliana, repete a tua dor pra ficar em ata.

**Juliana:** Lançamento **em dobro** na tela. O cliente vê "COMPRA MERCADO 250 reais" duas vezes, entende que foi cobrado duas vezes, e liga. A confiança no produto morre ali. Pra mim esse é o defeito número um, acima de atraso, acima de tudo.

**Fábio:** E agora eu explico por que isso **vai** acontecer se a gente não fizer nada. No mundo distribuído, quem envia uma mensagem e não tem certeza de que ela chegou **reenvia**. A transmissora teve um soluço de rede? Reenvia. O conector reprocessou um lote? Reenvia. Isso é **comportamento correto** do lado deles — a alternativa, não reenviar na dúvida, é como a gente **perde** lançamento. Então a regra do jogo é: **o mesmo lançamento pode chegar mais de uma vez, e o dever de não duplicar é nosso, do consumidor.**

**Márcio:** E como a gente reconhece que é repetido?

**Fábio:** Pela identidade do lançamento: **instituição de origem + identificador do lançamento na origem**. Esse par é único no mundo. Quando chega um lançamento, a gente verifica: já processei esse par? Se sim, **ignora silenciosamente** — não é erro, não é alerta, é o funcionamento normal. Se não, processa e registra o par. O nome técnico dessa propriedade é **idempotência**: processar a mesma mensagem uma ou dez vezes tem que dar o mesmo resultado.

**Renata:** Glossário, com destaque: **idempotência** — processar o mesmo lançamento repetido não altera o resultado; a identidade do lançamento é **(instituição de origem + identificador na origem)**; repetido é ignorado sem erro. Juliana, isso resolve a tua dor?

**Juliana:** Se funcionar, resolve a de duplicidade. Deixa eu puxar a outra ponta: **ordem**. Os lançamentos chegam na ordem em que aconteceram?

**Fábio:** Boa pergunta, e a resposta é **não garantido**. Origens diferentes, caminhos diferentes, reprocessamentos — um lançamento de ontem pode chegar depois de um de hoje. O sistema tem que aceitar **lançamento atrasado e fora de ordem** com naturalidade: cada lançamento carrega a **data em que ocorreu**, e é ela que manda — o lançamento entra no dia e no mês a que pertence, não no dia em que chegou. Se chegar um lançamento de um mês que eu já consolidei, eu **reabro e atualizo** aquele mês.

**Márcio:** Tradução de negócio: o extrato de maio pode mudar em junho?

**Fábio:** Pode — e deve, se chegar um lançamento de maio atrasado. É raro, mas acontece. O consolidado converge pra verdade; ele não congela no primeiro rascunho.

**Renata:** Anotado: **fora de ordem e atrasado são normais**; o lançamento pertence à data em que **ocorreu**; consolidado de mês anterior **pode ser atualizado** por lançamento atrasado. Volumetria, pra fechar: números pra dimensionar?

**Fábio:** Ordem de grandeza pra projetar: **milhões de lançamentos por dia** no regime normal, com picos de **cinco a dez vezes** nos dias críticos. E a regra qualitativa que importa mais que o número: no pico, a esteira **acumula** e a gente escoa; o que não pode é **derrubar o consumo** nem **perder** o que está na esteira.

**Márcio:** E o cliente novo? Consentiu agora, as contas dele têm histórico. Vem tudo?

**Fábio:** Vem a **carga inicial**: quando o consentimento é dado, as transmissoras enviam o histórico recente — a janela exata é regulatória, a Dra. Patrícia confirma na Sessão 5, mas a ordem de grandeza é de **meses de histórico**. Pro nosso sistema, é o mesmo fluxo: uma rajada de lançamentos na esteira, com datas antigas. Se a idempotência e o fora-de-ordem estiverem certos, a carga inicial não é caso especial — é só volume.

**Renata:** Elegante. Recapitulando a Sessão 2: lançamentos chegam por **tópico publica-assina**, fluxo contínuo, várias origens; ficha do lançamento com identificador da origem, instituição, conta, tipo, valor, moeda, data/hora, descrição e consentimento; **duplicidade é resolvida por idempotência** com a chave (instituição + identificador na origem); **fora de ordem e atraso são normais** — vale a data de ocorrência; inválido separa pra inspeção sem travar nem sumir (Sessão 4); e a carga inicial do cliente novo é o mesmo fluxo. Ficou faltando alguma coisa, Fábio?

**Fábio:** Só deixar semeado: tudo que discutimos foi *entrada*. Na próxima a gente discute a *saída* — como esses lançamentos viram o extrato que o cliente vê, e por que a consulta precisa de um tratamento especial de desempenho.

**Juliana:** Com o caso do "agorinha" incluso. Eu cobro.

---

## Decisões da Sessão 2

1. A ingestão é por **tópico (publica-assina)**: fluxo contínuo de lançamentos, várias origens (conectores das transmissoras + sistemas internos Caixa), consumo no ritmo do sistema.
2. **Ficha do lançamento** (obrigatórios): identificador na origem, instituição de origem (ISPB), conta (agência+número), tipo (crédito/débito), valor, moeda, data/hora de ocorrência, identificador do consentimento. **Opcionais:** descrição (exibe genérico se vazia), categoria da origem.
3. **Idempotência é requisito central:** a identidade do lançamento é **(instituição de origem + identificador na origem)**; lançamento repetido é **ignorado sem erro**. Reenvio é comportamento normal das origens.
4. **Fora de ordem e atraso são normais.** O lançamento pertence à **data em que ocorreu**; consolidado de mês anterior **pode ser atualizado** por lançamento atrasado.
5. **Lançamento inválido** (sem campo obrigatório): separado para inspeção, **sem descarte e sem travar o fluxo** — mecanismo na Sessão 4.
6. **Carga inicial** (cliente que acabou de consentir): mesmo fluxo, rajada com datas antigas — não é caso especial.
7. Volumetria de projeto: **milhões de lançamentos/dia**, picos de **5–10×**; no pico a esteira acumula e o sistema escoa, sem perda e sem queda.

## Action items

- **Fábio:** trazer para a Sessão 3 o desenho da consolidação e a proposta de desempenho da consulta.
- **Juliana:** trazer o caso do "lançamento recém-feito" com exemplos reais de expectativa do cliente.
- **Renata:** confirmar com a Dra. Patrícia a janela regulatória da carga inicial de histórico (Sessão 5).

## Glossário (incremento da Sessão 2)

- **Ingestão por tópico:** recebimento contínuo de lançamentos via canal publica-assina; quem publica não conhece quem consome.
- **Idempotência:** processar o mesmo lançamento uma ou N vezes produz o mesmo resultado; repetidos são ignorados sem erro.
- **Identidade do lançamento:** par (instituição de origem + identificador do lançamento na origem).
- **Lançamento atrasado / fora de ordem:** lançamento que chega depois de outros mais recentes; entra na data em que **ocorreu**.
- **Carga inicial:** histórico recente enviado pelas transmissoras quando o consentimento é dado.
- **Lançamento inválido:** sem campo obrigatório; separado para inspeção, nunca descartado silenciosamente.
