# Sessão 4 — Confiabilidade e integrações

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 22/06/2026 (segunda-feira), 14h00–14h30
- **Duração:** 30 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO, facilitadora), Fábio Cardim (Arquiteto de Soluções), Igor Salles (Inteligência e Personalização), Márcio Bandeira (Gerente de Produto), Juliana Prates (Experiência do Cliente)

---

**Renata:** Sessão 4, e temos cara nova: Igor Salles, da Inteligência e Personalização. Igor, bem-vindo — te dou a palavra no ponto certo. Hoje o tema é o que acontece quando as coisas dão errado, e como outras áreas se plugam no que a gente constrói. Fábio.

**Fábio:** Regra número um, inegociável, pra abrir: **a gente não perde lançamento.** O lançamento entrou na esteira, ele **vai** parar no consolidado — talvez atrasado, nunca perdido. Um lançamento perdido é um extrato mentiroso pra sempre, e é o tipo de erro que ninguém percebe até o cliente somar na mão e o número não bater.

**Juliana:** Que é a pior descoberta possível, porque destrói a confiança retroativamente — o cliente passa a duvidar de tudo que já viu.

**Fábio:** Então vamos aos cenários de falha, um por um. **Cenário um: falha temporária.** O consumidor pega um lançamento da esteira pra consolidar e o banco de dados está fora, ou lento, ou deu soluço de rede. Não é culpa do lançamento — é o mundo sendo mundo. Nesse caso: **re-tenta**. O lançamento não é descartado; espera-se um pouco e tenta de novo, e de novo, com intervalos crescentes — pra não metralhar um banco que já está sofrendo. Em algum momento o banco volta, a consolidação acontece, nada se perdeu.

**Renata:** Falha temporária → re-tenta com intervalos crescentes, não descarta. Anotado.

**Fábio:** **Cenário dois: a mensagem envenenada.** Lembra do lançamento inválido da Sessão 2 — sem valor, sem data, corrompido? Esse falha **sempre**. Não importa quantas vezes eu tente, vai falhar de novo. E se eu só fico re-tentando, esse lançamento vira uma **rolha**: tenta, falha, tenta, falha — e segura os lançamentos bons que estão atrás dele. Rolha no dia de pico é catástrofe.

**Márcio:** E não pode jogar fora, porque regra número um.

**Fábio:** Exato, é o dilema: não pode travar, não pode sumir. A solução: depois de algumas tentativas, o sistema **move o lançamento problemático pra um canal separado** — o nome técnico é **fila morta, DLQ**. Ali ele fica **parado, guardado, visível**, esperando um humano investigar: por que veio corrompido? Bug do conector? Problema na transmissora? Corrige-se a causa, **reprocessa-se** o lançamento, e ele finalmente entra no consolidado. A fila morta não é lixeira — é a **sala de espera dos casos que precisam de gente**.

**Renata:** Glossário: **fila morta (DLQ)** — canal separado pra onde vai a mensagem que falha repetidamente; fica guardada para inspeção e reprocessamento; nunca descartada. E cravo a sutileza: **a fila morta é um requisito nomeado**, não um detalhe de implementação — quero ela numa user story própria.

**Fábio:** Obrigado por cravar, porque é exatamente o que se perde em compilação apressada. **Cenário três**, e esse é novo: **a reconsolidação.** Às vezes a posição consolidada precisa ser **recalculada do zero**. Exemplos reais: corrigimos um bug que consolidava errado; um lote da fila morta foi reprocessado e o mês precisa refletir; a Juliana recebe uma contestação — "meu saldo está errado" — e o atendimento quer forçar o recálculo daquela conta pra garantir.

**Juliana:** Essa é boa. Hoje, em outros produtos, "recalcular" é abrir chamado pra TI e esperar três dias.

**Fábio:** Aqui vira um botão. A proposta: um **pedido de reconsolidação** — "recalcule a conta X na competência Y" — que entra numa **fila de trabalho**. Um trabalhador consome pedido a pedido: reapura os lançamentos daquela conta/mês a partir do que está guardado, refaz os totais, atualiza a posição, invalida o cache. Por que fila e não na hora? Porque reconsolidar é pesado, e se cinquenta pedidos chegarem juntos, eles **fazem fila e são atendidos um a um**, sem derrubar o sistema que está servindo consulta pra milhões.

**Márcio:** É o guichê: o pedido é registrado na hora, o atendimento acontece na ordem, e ninguém derruba a agência.

**Fábio:** Perfeita, a analogia é essa. E note a diferença dos dois canais que temos agora: a **esteira** (tópico) é publica-assina — quem publica não sabe quem consome, e pode haver vários interessados. A **fila de reconsolidação** é de trabalho — cada pedido é de quem pegou, é executado **uma vez só**. Instrumentos diferentes pra necessidades diferentes.

**Renata:** Glossário: **pedido de reconsolidação** — solicitação de recálculo de uma conta/competência, processado **um a um** via fila de trabalho. E com isso, Igor, tua deixa.

**Igor:** Finalmente! Pessoal, eu toco a categorização de gastos, a visão financeira e — futuramente — ofertas personalizadas. Pro meu mundo funcionar, eu preciso saber **quando o extrato consolidado de um cliente muda**. Chegou lançamento novo, mês reaberto, reconsolidação — qualquer mudança, eu quero saber. E deixa eu dizer logo o que eu **não** quero: eu não quero que vocês chamem a minha API. Se o meu sistema estiver fora, isso não pode nem atrasar nem quebrar a consolidação de vocês. E eu não quero ser o único: depois de mim vem BI, vem notificação, vem gente que nem existe ainda.

**Fábio:** Música. Porque é a mesma resposta da ingestão, só que na outra ponta: **evento**. Quando a consolidação atualiza uma posição, ela **publica um evento** — "posição da conta X, competência Y, do cliente Z foi atualizada" — num **tópico**. Quem quiser, assina: o Igor recalcula categorização, o BI conta métricas, o app manda push. A consolidação **não conhece nenhum deles**. Ela anuncia e segue a vida.

**Igor:** E o evento carrega o quê? Eu preciso dos lançamentos dentro dele?

**Fábio:** Proposta: o evento carrega a **referência** — cliente, conta, competência, hora da atualização — e não o extrato inteiro. Quem precisar do detalhe consulta na hora. Evento gordo espalha dado pessoal por todo canto, e eu já estou ouvindo a Dra. Patrícia na minha cabeça dizendo que cada cópia é um passivo de LGPD.

**Igor:** Funciona. Referência me basta — eu busco o que precisar. E o aproveitamento interno: esse mesmo evento pode ser o gatilho da **invalidação do cache** da Sessão 3, não pode?

**Fábio:** Pode e vai ser — bem observado. A invalidação imediata que prometemos é tecnicamente isso: a consulta **assina o mesmo tópico de eventos** e derruba a cópia da posição que mudou. O mesmo mecanismo serve casa e visitas.

**Márcio:** Deixa eu fazer o papel do chato do escopo: publicar esse evento é MVP? Porque na Sessão 1 categorização e ofertas ficaram pra fase 2.

**Fábio:** A distinção fina: **publicar o evento é MVP** — é barato, é a "tomada pronta" que eu defendi na Sessão 1, e a própria invalidação do cache já precisa dele. **Consumir** o evento pra categorização, BI, push — cada um é projeto de quem consome, no tempo de quem consome. A gente entrega a tomada; cada área pluga quando estiver pronta.

**Márcio:** Comprado. Tomada agora, eletrodomésticos depois.

**Igor:** Só registro o meu asterisco: pra **ofertas**, além do evento eu vou precisar de conversa séria de base legal — o dado veio pro extrato, usar pra oferta é outra finalidade. Eu sei que a Dra. Patrícia vai falar disso, eu mesmo levanto na Sessão 5.

**Renata:** Maturidade regulatória do Igor anotada em ata, a Dra. Patrícia vai se emocionar. Recapitulando a Sessão 4: **não perder lançamento** é a regra um; **falha temporária → re-tenta com intervalos crescentes**; **mensagem envenenada → fila morta** pra inspeção e reprocessamento, sem travar e sem sumir — requisito nomeado; **reconsolidação sob demanda** via fila de trabalho, um a um; **evento "posição atualizada"** publicado em tópico no MVP, carregando **referência** e não o dado inteiro; consumidores plugam depois; a **invalidação do cache** usa esse mesmo evento; ofertas dependem de base legal — Sessão 5.

**Fábio:** É isso. Essa foi a sessão que faz o sistema sobreviver à vida real.

**Juliana:** E a próxima é a que faz ele sobreviver ao Bacen.

---

## Decisões da Sessão 4

1. **Regra número um:** nenhum lançamento é perdido, em hipótese alguma. Atrasar pode; sumir não.
2. **Falha temporária** (banco fora, lentidão, soluço de rede): **re-tentar com intervalos crescentes** (backoff), sem descartar.
3. **Mensagem envenenada** (falha sempre — ex.: lançamento inválido/corrompido): após N tentativas, mover para a **fila morta (DLQ)** — guardada, inspecionável, reprocessável. **Requisito nomeado**, com user story própria. Não trava a esteira, não descarta.
4. **Reconsolidação sob demanda:** pedido de recálculo (conta × competência) via **fila de trabalho**, consumido **um a um** (guichê). Gatilhos: contestação do cliente/atendimento, reprocessamento pós-DLQ, correção de bug.
5. **Evento "posição consolidada atualizada"** publicado num **tópico** a cada atualização — **MVP** ("tomada pronta"). O evento carrega **referência** (cliente, conta, competência, hora), não o extrato.
6. Consumidores do evento (categorização/PFM, BI, notificação): **fase 2, projeto de cada área** — sem impacto no núcleo. A **invalidação do cache** (Sessão 3) consome esse mesmo evento — consumidor interno do MVP.
7. Uso dos dados para **ofertas** exige discussão de finalidade/base legal — pauta da Sessão 5.

## Action items

- **Fábio:** consolidar os parâmetros de re-tentativa (quantas, intervalos) como decisão técnica documentada do time.
- **Renata:** garantir user story própria para a fila morta e para a reconsolidação na compilação.
- **Igor:** levantar na Sessão 5 a questão da finalidade para categorização e ofertas.
- **Juliana:** mapear com o atendimento o fluxo de contestação que dispara reconsolidação.

## Glossário (incremento da Sessão 4)

- **Falha temporária:** indisponibilidade passageira de dependência; tratada com **re-tentativa com intervalos crescentes (backoff)**.
- **Mensagem envenenada:** mensagem que falha em todas as tentativas (ex.: corrompida); vai para a fila morta.
- **Fila morta (DLQ):** canal separado onde mensagens que falham repetidamente ficam guardadas para inspeção e reprocessamento. Não é lixeira.
- **Reconsolidação:** recálculo completo da posição de uma conta numa competência, a pedido, via fila de trabalho.
- **Fila de trabalho:** canal onde cada pedido é consumido por um único trabalhador, uma única vez (≠ tópico publica-assina).
- **Evento "posição atualizada":** aviso publicado em tópico quando uma posição consolidada muda; carrega referência, não dado.
