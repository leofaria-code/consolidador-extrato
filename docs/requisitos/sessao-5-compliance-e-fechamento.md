# Sessão 5 — Compliance e fechamento

- **Projeto:** Consolidador de Extrato — Open Finance
- **Data:** 29/06/2026 (segunda-feira), 14h00–14h45 *(estendida 15 min a pedido da Dra. Patrícia)*
- **Duração:** 45 min
- **Plataforma:** Microsoft Teams
- **Participantes:** Renata Fontes (PO, facilitadora), Dra. Patrícia Yano (Regulação/LGPD), Fábio Cardim (Arquiteto de Soluções), Igor Salles (Inteligência), Márcio Bandeira (Gerente de Produto), Juliana Prates (Experiência do Cliente)

---

**Renata:** Sessão 5, a última, com a presença que a gente segurou quatro semanas: Dra. Patrícia Yano, de Regulação e LGPD. Doutora, a senhora leu as atas; a pauta é sua. Depois fechamos o escopo geral.

**Patrícia:** Li as quatro atas, e começo elogiando: vocês adiaram as decisões certas. Agora vamos fechá-las com precisão, porque aqui não pode haver "aproximadamente". Primeiro pilar: **consentimento**. Tudo neste sistema existe porque o cliente autorizou. No Open Finance, o consentimento é um ato formal: o cliente escolhe **quais instituições** e **quais dados** compartilha, por **qual prazo** — e a regulamentação fixa a validade máxima em **12 meses**, renovável pelo cliente. Não é um checkbox genérico; é um contrato com escopo e data de vencimento.

**Renata:** Glossário: **consentimento** — autorização formal do cliente, por instituição, com escopo de dados e **validade máxima de 12 meses**, renovável e revogável a qualquer momento.

**Patrícia:** E dele decorrem três regras operacionais que quero ver como requisitos, não como boas intenções. **Primeira: só entra dado com consentimento vigente.** Se chegar lançamento cujo consentimento está vencido ou não consta, esse lançamento **não pode ser incorporado**. Segunda: o sistema da Sessão 2 já previu que cada lançamento carrega o identificador do consentimento — ótimo, é exatamente o vínculo de base legal que eu exigiria. Terceira, e a mais séria: **revogação**.

**Márcio:** O cliente desiste do compartilhamento.

**Patrícia:** E é direito dele, exercível a qualquer momento, sem justificativa. Quando revoga, duas obrigações: **cessar a ingestão imediatamente** — nenhum lançamento novo daquele consentimento entra — e **eliminar os dados já recebidos** por aquele consentimento. E eliminar é eliminar: sai do banco, sai do cache, sai de onde estiver. Pela nossa política interna, o expurgo deve estar concluído em **até 30 dias corridos** da revogação, com **registro comprovável** — a auditoria vai pedir evidência de que o dado existiu e deixou de existir.

**Fábio:** Do lado técnico, viável e — confesso — elegante de implementar com o que já desenhamos: a revogação chega como um **evento** (o mesmo padrão de tudo), dispara o bloqueio de ingestão na hora, e o expurgo vira um trabalho da **fila de reconsolidação** — recalcular as posições daquele cliente **sem** os dados da instituição revogada. O carimbo de consentimento em cada lançamento nos diz exatamente o que apagar.

**Patrícia:** Registre-se que o arquiteto usou a palavra "elegante" para uma obrigação legal; é raro e bem-vindo. Segundo pilar: **finalidade**. O dado veio para **exibir o extrato consolidado ao cliente**. Essa é a finalidade consentida. Usar o mesmo dado para outra coisa — modelos de crédito, ofertas, propensão — é **outra finalidade** e exige **outra base legal**. Igor, as atas dizem que você mesmo levantaria isso.

**Igor:** Levanto e me antecipo: a **categorização de gastos exibida ao próprio cliente** eu defendo como parte da finalidade original — é o extrato dele, enriquecido pra ele. Já **ofertas** eu concedo: é outra finalidade, precisa de consentimento específico, e por mim fica na fase 2 com o desenho jurídico correto.

**Patrícia:** Concordo com a sua partição, com uma condição: quando a categorização nascer (fase 2), o enriquecimento deve estar descrito nos termos que o cliente aceita. Para o MVP, que não tem nem um nem outro, basta que o **evento** publicado carregue referência e não dado — o que, pelas atas da Sessão 4, já foi decidido. Muito bem, aliás.

**Renata:** Terceiro pilar, doutora: auditoria?

**Patrícia:** **Trilha de acesso.** Extrato consolidado é dado pessoal — movimentação financeira, aliás, é dos mais sensíveis na prática. Preciso de resposta pronta para: **quem** acessou o extrato de **qual** cliente, **quando**, por **qual canal**. Cada consulta gera registro. E atenção ao detalhe que sempre escapa: **os registros técnicos — logs — não podem vazar dado pessoal.** Nada de valor, descrição de lançamento ou documento do cliente em log de sistema. Identificadores opacos, sim; conteúdo, jamais.

**Fábio:** Requisito anotado e com consequência técnica boa: log estruturado com identificadores e **correlação** — cada requisição carrega um identificador único que atravessa os serviços, então a auditoria consegue reconstruir o caminho completo de um acesso sem nunca logar o conteúdo.

**Patrícia:** Exatamente o que eu pediria. Quarto pilar, rápido: **retenção**. Aqui a regra é simples e diferente da que vocês talvez esperem: os dados vivem **enquanto o consentimento viver**. Consentimento vigente, dado disponível; vencido sem renovação, o dado de terceiros deve ser eliminado no mesmo regime da revogação — 30 dias, com evidência. Não há guarda de 10 anos aqui; guarda longa é para documentos de transação no sistema de origem, não para a cópia consolidada de exibição.

**Renata:** Anotado com destaque, porque é contraintuitivo: **vencimento de consentimento = revogação para efeitos de expurgo.** Doutora, a carga inicial: a Sessão 2 ficou de confirmar a janela de histórico.

**Patrícia:** Pela regulamentação vigente do arranjo, o cliente pode autorizar histórico de até **12 meses** retroativos na carga inicial. Usem 12 meses como referência de projeto.

**Renata:** Fechado. Últimos dez minutos: recap geral do MVP, pra virar a compilação das user stories. Vou falar e vocês me corrigem. O sistema: ingere lançamentos por **tópico**, de várias origens, **só com consentimento vigente**, com **idempotência** pela identidade (instituição + id na origem), aceitando atraso e desordem; **consolida continuamente** por conta × competência; serve o **extrato consolidado** com **cache** do mês corrente, **invalidação por evento** e **prazo de validade**; exibe o **carimbo "atualizado às"** com **atualizar sob demanda**; **não perde lançamento** — re-tentativa com backoff e **fila morta** nomeada; **reconsolida sob demanda** por fila de trabalho; publica **evento de posição atualizada** (referência, não dado); **revogação** cessa ingestão na hora e expurga em 30 dias com evidência; **vencimento = revogação**; **trilha de acesso** completa e **logs sem dado pessoal**; carga inicial de até 12 meses. Fora do MVP: categorização, ofertas (com base legal própria), busca, exportação, notificação ao cliente.

**Fábio:** Assino embaixo.

**Patrícia:** Com uma ressalva de processo: quero revisar a compilação final antes de ir ao time. Renata, sem pressa que gere imprecisão — prefiro um dia a mais e um documento exato.

**Renata:** Combinado, doutora — mando sexta. Pessoal, foram cinco sessões. Obrigada. Agora é com o time de desenvolvimento.

**Márcio:** E com a campanha de fim de ano me olhando. Vamos.

---

## Decisões da Sessão 5

1. **Consentimento:** formal, por instituição e escopo, **validade máxima de 12 meses**, renovável e **revogável a qualquer momento**. Cada lançamento mantém o vínculo com seu consentimento (já previsto na Sessão 2).
2. **Só entra dado com consentimento vigente.** Lançamento com consentimento vencido/ausente **não é incorporado** (segue o tratamento de mensagem rejeitada — inspecionável, não silencioso).
3. **Revogação:** cessar ingestão **imediatamente** + **expurgar** todos os dados daquele consentimento (banco e cache) em **até 30 dias corridos**, com **registro comprovável**. Implementação via evento + fila de reconsolidação.
4. **Vencimento sem renovação = revogação** para efeitos de expurgo (mesmo prazo, mesma evidência). **Não há retenção de 10 anos** — guarda longa é do sistema de origem, não da cópia de exibição.
5. **Finalidade:** o dado serve à **exibição do extrato ao cliente**. Categorização exibida ao próprio cliente: defensável na finalidade original (fase 2, com termos atualizados). **Ofertas: outra finalidade, outra base legal — fase 2 com desenho jurídico.**
6. **Trilha de auditoria de acesso:** toda consulta registra quem, qual cliente, quando, por qual canal.
7. **Logs sem dado pessoal:** identificadores opacos e **correlação de ponta a ponta** (um identificador único atravessa os serviços); nunca valor, descrição ou documento em log.
8. **Carga inicial:** até **12 meses** de histórico retroativo.
9. A Dra. Patrícia **revisa a compilação** das user stories antes da distribuição ao time.

## Action items

- **Renata:** compilar as user stories e enviar para revisão da Dra. Patrícia até sexta (03/07).
- **Fábio:** desenhar o fluxo técnico da revogação (evento → bloqueio de ingestão → expurgo via fila) como primeira decisão arquitetural documentada.
- **Igor:** iniciar com o jurídico o desenho da base legal para categorização e ofertas (fase 2).
- **Juliana:** revisar os textos do app referentes a consentimento e revogação com a ótica de transparência.

## Glossário (incremento da Sessão 5)

- **Consentimento:** autorização formal do cliente (instituição + escopo + prazo ≤ 12 meses), renovável e revogável.
- **Revogação:** retirada do consentimento; cessa ingestão imediatamente e obriga expurgo em até 30 dias com evidência.
- **Expurgo:** eliminação comprovável dos dados de um consentimento, em todos os repositórios (banco, cache).
- **Finalidade:** propósito autorizado do tratamento do dado; MVP = exibição do extrato ao próprio cliente.
- **Trilha de acesso:** registro de quem consultou o extrato de qual cliente, quando e por qual canal.
- **Correlação (correlation id):** identificador único que acompanha uma requisição por todos os serviços, permitindo reconstruir o caminho na auditoria sem logar conteúdo.
