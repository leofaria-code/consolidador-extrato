# Sessão 1 — Kickoff: contexto, problema e escopo

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 01/06/2026 (segunda-feira), 14h00–14h30
- **Duração:** 30 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO, facilitadora), Márcio Bandeira (Gerente de Produto), Juliana Prates (Experiência do Cliente), Fábio Cardim (Arquiteto de Soluções)
- **Ausente:** Dra. Patrícia Yano (regulação — entra na Sessão 5; enviou recado pedindo que **nada sobre consentimento, finalidade ou expurgo seja fechado sem ela**)

---

**Renata:** Boa tarde, pessoal. Duas em ponto, vamos aproveitar. Márcio, Juliana, Fábio... a Dra. Patrícia não vem hoje, está numa agenda da estrutura de governança do Open Finance, mas mandou recado por escrito: nada de consentimento, finalidade de uso ou expurgo de dados fechado sem ela. Anotei e reservei a última sessão pra isso.

**Márcio:** Justo. E eu tenho meia hora, então vamos direto: por que esse projeto existe. Hoje o nosso cliente típico tem conta em dois, três, quatro bancos. Salário cai num, cartão em outro, a conta digital gratuita num terceiro. E quando ele quer saber "quanto eu tenho, quanto eu gastei", ele abre quatro apps. Ou pior: abre um agregador de terceiro, que não é nosso, e a vida financeira dele passa a morar lá.

**Juliana:** E aí ele só abre o nosso app pra ver o salário cair. Uma vez por mês.

**Márcio:** Exato. A palavra que o board usa é **principalidade**: ser o banco principal, o app que o cliente abre todo dia. O Open Finance nos dá o instrumento: com o consentimento do cliente, as outras instituições são obrigadas a compartilhar os dados dele com a gente. Se a gente mostrar tudo num lugar só — um **extrato consolidado** — o cliente não precisa de mais nada.

**Renata:** Deixa eu já começar o glossário, porque esse termo vai aparecer mil vezes. **Extrato consolidado:** a visão única dos lançamentos de todas as contas do cliente — as da Caixa e as de outras instituições — num só lugar. Fábio, do teu lado, o que esse sistema é e o que ele não é?

**Fábio:** Ponto mais importante da reunião, na minha opinião. Esse sistema **lê e exibe. Ele não movimenta dinheiro.** Não faz PIX, não faz TED, não inicia pagamento — iniciação de pagamento é outro arranjo do Open Finance, outro projeto, outro time. O nosso sistema recebe **lançamentos** — cada crédito e débito das contas do cliente —, **consolida** e **exibe**. Só isso.

**Márcio:** Fundamental. Se o board ouvir "sistema novo que mexe com as contas do cliente", vira comitê de risco. Não é isso. É leitura.

**Renata:** Glossário: **Lançamento** — um crédito ou débito ocorrido numa conta do cliente, na Caixa ou em outra instituição. E em negrito pra mim: **fora de escopo — movimentar dinheiro, iniciar pagamento, alterar lançamento.** Fábio, e de onde vêm esses lançamentos?

**Fábio:** De duas origens. Os das contas Caixa, dos nossos próprios sistemas internos. E os das outras instituições — que no jargão do Open Finance são as **transmissoras**, enquanto a gente, recebendo, é a **receptora**. Em ambos os casos, para o nosso sistema, é a mesma coisa: chega um fluxo contínuo de lançamentos, de várias origens, e a gente precisa ingerir, consolidar e servir. O *como* chega — o formato desse fluxo — eu quero detalhar na Sessão 2, porque tem sutileza.

**Juliana:** Posso trazer a dor da ponta? Porque tem duas, e são diferentes. A primeira é a que o Márcio falou: o cliente quer ver tudo junto e hoje não vê. Essa é a dor de produto. Mas tem a segunda, que é a que me tira o sono: **confiança**. O cliente confia no número que está na tela. Se o extrato consolidado mostrar um lançamento **em dobro**, ele não pensa "bug de software", ele pensa "me cobraram duas vezes". Liga pro atendimento, abre reclamação, e a gente transformou um produto de encantamento numa fábrica de chamado.

**Fábio:** Juliana, guarda esse ponto com carinho porque ele é tecnicamente profundo. Adianto só o título: as transmissoras **podem reenviar o mesmo lançamento** — por reprocessamento, por falha de rede do lado delas, por retry. Isso é normal e esperado no mundo distribuído. O nosso sistema é que tem a obrigação de **reconhecer o repetido e não duplicar**. Tem nome técnico e vai ser um requisito central na Sessão 2.

**Renata:** Anotei destacado: **lançamento duplicado é inaceitável na tela do cliente** — tratamento técnico a detalhar na Sessão 2. Márcio, volume. Estamos falando de quê?

**Márcio:** Grande. A base elegível é de milhões de clientes, e cada cliente com duas, três contas fora. E tem o padrão Caixa: **dia de crédito do Bolsa Família, dia de salário, 13º, saque do FGTS** — nesses dias o volume de lançamentos multiplica. E dezembro tem tudo junto: 13º mais Black Friday mais compras de Natal.

**Fábio:** E pico aqui tem um detalhe: ele bate **duas vezes**. Bate na **ingestão** — todo mundo recebendo e gastando ao mesmo tempo gera uma enxurrada de lançamentos — e bate na **consulta** — que é exatamente quando todo mundo abre o app pra ver se o dinheiro caiu. O sistema não pode escolher um dos dois pra aguentar.

**Renata:** Glossário: **pico de volumetria** — datas de crédito de salário/benefício, 13º e sazonais (Black Friday, Natal), quando ingestão **e** consulta multiplicam simultaneamente. Requisito não-funcional: **aguentar o pico sem perder lançamento e sem derrubar a consulta.**

**Juliana:** E tem o cenário do "agorinha", que eu já aviso que existe: o cliente passa o cartão no mercado, e trinta segundos depois abre o app pra ver o gasto no consolidado. Se não estiver lá, ele estranha.

**Fábio:** Esse cenário é ouro e tem trade-off honesto: o dado vem de **outra instituição**, existe um caminho a percorrer, e "instantâneo" tem um custo que talvez não valha a pena. Tem um jeito de negócio de tratar isso elegantemente sem prometer o impossível. Sessão 3.

**Renata:** Anotado como **"caso do lançamento recém-feito"** — a tratar na Sessão 3, junto com consulta e desempenho. Márcio, pra fechar: MVP.

**Márcio:** MVP pra mim é: o cliente consente, os lançamentos das contas dele entram, e ele abre o app e vê o **extrato consolidado do mês**, com os saldos, rápido e confiável. Ponto. Categorização de gasto — "você gastou tanto de mercado" —, gráficos, ofertas personalizadas... isso é lindo, é o futuro do produto, mas é fase 2. Não trava o MVP.

**Fábio:** Concordo com uma ressalva que vou defender na Sessão 4: mesmo sem categorização no MVP, a gente deve deixar **pronta a tomada** pra essas áreas plugarem depois sem mexer no nosso núcleo. É barato agora e caro depois.

**Renata:** Catalogado. Então recapitulando: o sistema **ingere lançamentos das contas do cliente — Caixa e outras instituições via Open Finance —, consolida e exibe o extrato consolidado**; **não movimenta dinheiro**; a dor é o cliente multibanco sem visão única (produto) e a confiança no que está na tela (ponta); duplicidade é inaceitável; temos pico duplo, na ingestão e na consulta; e o caso do lançamento recém-feito fica pra Sessão 3. MVP: consentiu, ingeriu, consolidou, exibiu — rápido e confiável.

**Márcio:** Perfeito. Semana que vem eu volto. (*sai*)

**Juliana:** Só reforçando o meu lema pra ficar em ata: prefiro extrato **atrasado e certo** do que **na hora e errado**. Se tiver que escolher, escolham o certo.

**Fábio:** Anota essa frase, Renata. Ela vale por três requisitos.

**Renata:** Anotada, com aspas e autora. Sessão 2 é ingestão: como os lançamentos chegam, quais campos têm, o que é o consentimento na prática e o tal do reenvio duplicado. Fábio, prepara a explicação do fluxo contínuo pra gente de negócio.

**Fábio:** Trago analogia pronta. Vai envolver uma esteira de aeroporto.

---

## Decisões da Sessão 1

1. O sistema **ingere, consolida e exibe lançamentos** das contas do cliente (Caixa + outras instituições via Open Finance, mediante consentimento). **Não movimenta dinheiro, não inicia pagamento, não altera lançamento** — fora do escopo, sempre.
2. O **MVP** é: ingestão dos lançamentos + **extrato consolidado por cliente e mês**, com saldos, **rápido e confiável**.
3. **Lançamento duplicado na tela é inaceitável** — tratamento técnico do reenvio é requisito central (Sessão 2). Lema de ata (Juliana): *"prefiro extrato atrasado e certo do que na hora e errado."*
4. **Categorização de gastos, insights (PFM) e ofertas personalizadas** são catalogadas como **fase 2** — mas a arquitetura deve deixar "a tomada pronta" (Sessão 4).
5. Consentimento, finalidade e expurgo **não serão fechados sem a Dra. Patrícia** — reservado para a Sessão 5.

## Action items

- **Renata:** consolidar o glossário inicial (linguagem ubíqua) e circular.
- **Fábio:** trazer para a Sessão 2 a explicação de negócio do fluxo contínuo de ingestão e do reenvio/duplicidade.
- **Juliana:** detalhar na Sessão 3 o caso do "lançamento recém-feito que ainda não aparece".
- **Renata:** garantir presença da Dra. Patrícia na Sessão 5 e do Igor (Inteligência) na Sessão 4.

## Glossário inicial (linguagem ubíqua)

- **Extrato consolidado:** visão única dos lançamentos de todas as contas do cliente (Caixa + outras instituições), com saldos, num só lugar.
- **Lançamento:** um crédito ou débito ocorrido numa conta do cliente.
- **Transmissora:** instituição que envia os dados do cliente (via Open Finance). **Receptora:** instituição que os recebe — a Caixa, neste projeto.
- **Consentimento:** autorização dada pelo cliente para que suas contas em outras instituições sejam compartilhadas com a Caixa (detalhes na Sessão 5).
- **Ingestão:** recebimento do fluxo contínuo de lançamentos das origens (detalhes na Sessão 2).
- **Consolidação:** agregação dos lançamentos por cliente/conta/mês, produzindo o extrato consolidado.
- **Pico de volumetria:** datas de crédito de salário/benefício, 13º e sazonais — multiplica ingestão **e** consulta ao mesmo tempo.
- **Caso do lançamento recém-feito:** cliente consulta segundos após gastar e o lançamento pode ainda não estar no consolidado.
