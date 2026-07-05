# Sessão 3 — Consolidação e consulta: do lançamento ao extrato na tela

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 15/06/2026 (segunda-feira), 14h00–14h30
- **Duração:** 30 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO, facilitadora), Fábio Cardim (Arquiteto de Soluções), Juliana Prates (Experiência do Cliente), Márcio Bandeira (Gerente de Produto)

---

**Renata:** Sessão 3. Semana passada fechamos a entrada; hoje é a saída: como os lançamentos viram o **extrato consolidado**, e como a consulta fica rápida. Juliana já avisou que cobra o caso do "agorinha". Fábio, teu palco.

**Fábio:** Começo pela consolidação. Os lançamentos chegam soltos, de várias contas e instituições. Consolidar é organizá-los em cima de dois eixos: **a conta** — cada conta do cliente em cada instituição — e a **competência** — o mês de referência. Pra cada conta e mês, o sistema mantém a lista de lançamentos e os totais: **entradas, saídas e saldo do período**. E por cima disso, a visão que dá nome ao produto: o **extrato consolidado do cliente no mês** — todas as contas juntas, com o total geral.

**Renata:** Glossário: **competência** — o mês de referência de um lançamento, definido pela data em que ele **ocorreu** (amarra com a decisão da Sessão 2 sobre atrasados). **Posição consolidada** — os totais (entradas, saídas, saldo) de uma conta ou do cliente numa competência.

**Márcio:** Pergunta de negócio: esse consolidado é calculado quando o cliente abre o app, ou já fica pronto?

**Fábio:** Já fica pronto — e esse é o ponto arquitetural da sessão. A consolidação acontece **na chegada do lançamento**, de forma contínua: chegou lançamento, atualiza a posição da conta naquela competência. Quando o cliente abre o app, o sistema **não calcula nada** — só **lê** uma posição que já estava pronta. Calcular na consulta seria fazer a conta mais cara do sistema no momento mais sensível, com o cliente esperando, multiplicado por milhões de clientes no dia de pico.

**Juliana:** E é aí que entra a minha pergunta de sempre: quão rápido é "rápido"?

**Fábio:** A meta que proponho: a consulta do extrato consolidado responde em **fração de segundo**, mesmo no pico. E pra sustentar isso tem um segundo mecanismo além do "já fica pronto": **cache**. Deixa eu explicar o conceito no vocabulário de todo mundo: o banco de dados é o **arquivo morto** — completo, confiável, mas no fundo do corredor. O cache é a **mesa de trabalho** — pequena, rápida, com as coisas que você está usando agora. A regra: o que é consultado toda hora fica na mesa; o que ninguém pede há tempos volta pro arquivo.

**Márcio:** E o que fica na mesa, no nosso caso?

**Fábio:** O padrão de acesso manda. E o nosso padrão é agudo: a esmagadora maioria das consultas é do **mês corrente** — o cliente quer ver o agora; olhar março de dois anos atrás é raridade. Então: **a posição consolidada do mês corrente dos clientes ativos fica em cache**; meses antigos ficam no banco e, se pedidos, sobem pra mesa temporariamente. Consulta típica: acha na mesa, responde em milissegundos, nem toca no arquivo.

**Renata:** E quando não acha na mesa?

**Fábio:** Busca no arquivo — no banco —, responde um pouco mais devagar, e **deixa uma cópia na mesa** pras próximas. A primeira consulta paga o preço; as seguintes voam. E tem a pergunta espelho, que é a mais importante: o que acontece **quando a posição muda** — chegou lançamento novo — e tem uma cópia velha na mesa?

**Juliana:** A mesa fica mentindo.

**Fábio:** Exato, e mentira na mesa é o teu lançamento em dobro com outra roupa: **número errado na tela**. Então regra de ferro: **quando a consolidação atualiza uma posição, a cópia daquela posição no cache é invalidada na hora** — some da mesa. A próxima consulta busca a versão nova no banco e repovoa a mesa. Além disso, toda entrada na mesa tem um **prazo de validade** — um tempo máximo de vida — como cinto de segurança: se a invalidação falhar por qualquer motivo, a cópia velha morre sozinha em minutos, não em dias.

**Renata:** Glossário, dois termos: **invalidação** — remoção imediata da cópia em cache quando a posição é atualizada; **prazo de validade (TTL)** — tempo máximo que uma cópia vive no cache mesmo sem invalidação. Juliana, tua deixa: o caso do "agorinha".

**Juliana:** Cenário real, aconteceu comigo: passei o cartão no almoço, abri o app no elevador. O gasto tem que aparecer? A expectativa do cliente é que sim — "é tudo digital, não é?". Mas eu sei que o dado vem de outro banco. O que a gente promete?

**Fábio:** Aqui eu quero ser muito honesto, porque é um limite **físico**, não preguiça nossa: o lançamento nasce na transmissora, atravessa o ecossistema Open Finance, chega no nosso tópico, é consolidado. Esse caminho leva **minutos** — poucos, tipicamente, mas minutos. Prometer "tempo real" seria mentir. A minha proposta é transformar limitação em transparência.

**Juliana:** O carimbo.

**Fábio:** O carimbo. Cada extrato consolidado exibe **"atualizado às HH:MM"** — a hora da última atualização daquela posição. O cliente do elevador vê "atualizado às 12:47", entende que o almoço de 12:52 ainda não entrou, e ganha um botão de **atualizar** que força a consulta a pular a mesa e olhar direto o arquivo. A experiência vira "o app é transparente comigo" em vez de "o app está errado".

**Juliana:** Aprovo. E digo mais: isso casa com o meu lema da ata — atrasado e certo, com carimbo dizendo o quanto atrasado, é um produto honesto. Sem carimbo é um produto que parece quebrado.

**Márcio:** Compro. Custa pouco e desarma a reclamação. E pro board eu vendo como "transparência de dado", que inclusive é discurso de Open Finance.

**Renata:** Anotado com destaque: **carimbo "atualizado às HH:MM" obrigatório no extrato consolidado** + **atualizar sob demanda** que ignora o cache. E a promessa de prazo, alguma? SLA?

**Fábio:** Proposta de meta interna, não de promessa ao cliente: **do lançamento publicado no tópico ao consolidado consultável em menos de 5 minutos** no regime normal. No pico pode esticar — a esteira acumula —, e é exatamente pra isso que o carimbo existe: a experiência degrada com elegância, informando, em vez de quebrar.

**Márcio:** Fecho com uma de produto: o cliente vê o extrato consolidado por mês. Consegue filtrar por conta? "Só me mostra a conta do banco X"?

**Fábio:** A consolidação já guarda por conta, então a consulta filtrada por conta é natural: visão geral (todas as contas) e visão por conta, ambas no MVP. O que eu sugiro **não** prometer no MVP: busca textual ("cadê o pix pro João?"), filtros por valor, exportar PDF. Tudo fase 2 — não porque é difícil, mas porque não é o núcleo.

**Renata:** Recapitulando a Sessão 3: consolidação **contínua na chegada** (consulta só lê, não calcula); posição por **conta × competência** com totais, e visão do cliente no mês; **cache** da posição do mês corrente, com **invalidação imediata** na atualização + **prazo de validade** de segurança; caso do recém-feito tratado com **carimbo "atualizado às"** + **atualizar sob demanda**; meta interna de **frescor < 5 min** no regime normal; consulta geral e por conta no MVP; busca e exportação fase 2.

**Fábio:** Perfeito. Semana que vem: o que acontece quando as coisas dão errado — e elas vão dar. É a sessão que separa protótipo de produto.

**Juliana:** E vem o Igor, né? Ele já me pediu três vezes pra avisar quando o extrato muda.

**Renata:** Vem. Sessão 4: confiabilidade e integrações, com Igor Salles da Inteligência.

---

## Decisões da Sessão 3

1. **Consolidação contínua, na chegada do lançamento.** A consulta **lê posição pronta**, não calcula. Eixos: **conta × competência** (mês da data de ocorrência), com totais (entradas, saídas, saldo) e visão consolidada do cliente no mês.
2. **Cache da posição do mês corrente** (o padrão de acesso é agudo no "agora"). Consulta: cache primeiro; ausência → banco → repovoa o cache.
3. **Regra de ferro da invalidação:** posição atualizada ⇒ cópia no cache invalidada **imediatamente**. Complemento: **prazo de validade (TTL)** como cinto de segurança contra invalidação falha.
4. **Caso do lançamento recém-feito:** sem promessa de tempo real (limite físico do ecossistema). Tratamento: **carimbo "atualizado às HH:MM"** em todo extrato + **atualizar sob demanda** (ignora o cache). 
5. **Meta interna de frescor:** lançamento publicado → consultável em **< 5 minutos** no regime normal; no pico degrada com elegância (carimbo informa).
6. **Meta de latência da consulta:** fração de segundo, mesmo no pico (sustentada por posição pronta + cache).
7. MVP de consulta: extrato consolidado do mês, **visão geral e por conta**. Fase 2: busca textual, filtros avançados, exportação.

## Action items

- **Fábio:** preparar a Sessão 4 — cenários de falha (perda, mensagem inválida, reprocessamento) e o mecanismo de aviso a outras áreas.
- **Renata:** convidar Igor Salles (Inteligência) para a Sessão 4.
- **Juliana:** validar com o time de app a viabilidade do carimbo + botão atualizar na tela do extrato.

## Glossário (incremento da Sessão 3)

- **Competência:** mês de referência de um lançamento, definido pela **data de ocorrência**.
- **Posição consolidada:** totais (entradas, saídas, saldo) de uma conta — ou do cliente — numa competência, mantidos prontos pela consolidação contínua.
- **Cache:** cópia rápida das posições mais consultadas (mês corrente), na frente do banco.
- **Invalidação:** remoção imediata da cópia em cache quando a posição muda.
- **Prazo de validade (TTL):** vida máxima de uma entrada no cache, mesmo sem invalidação.
- **Carimbo de atualização:** "atualizado às HH:MM" exibido em todo extrato consolidado.
- **Atualizar sob demanda:** consulta que ignora o cache e lê direto do banco, a pedido do cliente.
- **Frescor:** tempo entre o lançamento publicado no tópico e ele estar consultável no consolidado.
