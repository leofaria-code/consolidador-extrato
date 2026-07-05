# User Stories — Consolidador de Extrato / Open Finance

> **Documento compilado por:** Renata Fontes (Product Owner, Open Finance / Canais Digitais)
> **Data:** 03/07/2026 · **Revisão regulatória:** Dra. Patrícia Yano (pendências apontadas incorporadas)
> **Base:** cinco sessões de elicitação (kickoff, ingestão, consolidação e consulta, confiabilidade e integrações, compliance)
> **Status:** primeira fonte de verdade para o time de desenvolvimento.

Este documento é o ponto de partida do desenvolvimento. As transcrições das cinco reuniões estão no mesmo diretório e devem ser consultadas para entender o contexto e dirimir dúvidas. **Em caso de divergência, recorram às transcrições — elas prevalecem.**

## Glossário (linguagem ubíqua)

- **Extrato consolidado:** visão única dos lançamentos e saldos de todas as contas do cliente (Caixa + outras instituições), por competência.
- **Lançamento:** um crédito ou débito ocorrido numa conta do cliente.
- **Transmissora / Receptora:** instituição que envia / recebe os dados via Open Finance (a Caixa é a receptora).
- **Consentimento:** autorização formal do cliente (instituição + escopo + prazo ≤ 12 meses), renovável e revogável a qualquer momento.
- **Ingestão por tópico:** recebimento contínuo de lançamentos via canal publica-assina.
- **Identidade do lançamento:** par (instituição de origem + identificador do lançamento na origem) — chave de idempotência.
- **Idempotência:** processar o mesmo lançamento N vezes produz o mesmo resultado; repetidos são ignorados sem erro.
- **Competência:** mês de referência, definido pela data de **ocorrência** do lançamento.
- **Posição consolidada:** totais (entradas, saídas, saldo) de uma conta ou do cliente numa competência, mantidos prontos.
- **Cache / invalidação / TTL:** cópia rápida das posições mais consultadas; removida imediatamente quando a posição muda; com prazo máximo de vida.
- **Carimbo de atualização:** "atualizado às HH:MM" exibido em todo extrato consolidado.
- **Fila morta (DLQ):** canal onde mensagens que falham repetidamente ficam guardadas para inspeção e reprocessamento.
- **Reconsolidação:** recálculo da posição de uma conta × competência, sob demanda, via fila de trabalho.
- **Evento "posição atualizada":** aviso publicado em tópico quando uma posição muda; carrega referência, não dado.
- **Expurgo:** eliminação comprovável dos dados de um consentimento revogado/vencido, em todos os repositórios.

---

## Épico 1 — Ingestão de lançamentos

### US-01 — Consumir lançamentos publicados no tópico de ingestão

**Como** plataforma de consolidação,
**quero** consumir continuamente os lançamentos publicados pelas origens (conectores das transmissoras e sistemas Caixa) num tópico,
**para** incorporá-los ao extrato consolidado sem acoplamento com as origens.

**Notas de domínio** *(Sessões 1 e 2)*
- O sistema **lê e exibe; não movimenta dinheiro** nem inicia pagamento.
- Ingestão é **publica-assina**: quem publica não conhece quem consome. No pico, o tópico acumula e o sistema escoa no seu ritmo.

**Campos do lançamento**

| Campo | Obrigatório | Formato |
|---|---|---|
| id_lancamento_origem | Sim | texto — único na transmissora |
| instituicao_origem | Sim | código da instituição (ISPB) |
| agencia / conta | Sim | texto, como a origem identifica |
| tipo | Sim | crédito ou débito |
| valor | Sim | monetário |
| moeda | Sim | BRL |
| data_hora_ocorrencia | Sim | data/hora em que o lançamento ocorreu |
| id_consentimento | Sim | vínculo com a base legal |
| descricao | Não | texto (exibe genérico se vazio) |
| categoria_origem | Não | classificação enviada pela transmissora |

**Critérios de aceite**

- **Dado** um lançamento válido publicado no tópico,
  **Quando** o consumidor o processa,
  **Então** o lançamento é incorporado à conta e à **competência da data de ocorrência**, e a posição consolidada é atualizada.

- **Dado** um lançamento sem campo obrigatório,
  **Quando** o consumidor o processa,
  **Então** o lançamento **não** é incorporado e é **separado para inspeção** (ver US-08), sem travar o fluxo e sem descarte silencioso.

### US-02 — Ignorar lançamento repetido (consumidor idempotente)

**Como** cliente,
**quero** que um lançamento reenviado pela origem nunca apareça em dobro no meu extrato,
**para** confiar nos números da tela.

**Notas de domínio** *(Sessão 2)*
- Reenvio é comportamento **normal** das origens (retry, reprocessamento). O dever de não duplicar é do consumidor.
- Lema de ata (Juliana): *"prefiro extrato atrasado e certo do que na hora e errado."*

**Critérios de aceite**

- **Dado** um lançamento cuja identidade **(instituicao_origem + id_lancamento_origem)** já foi processada,
  **Quando** ele chega novamente,
  **Então** é **ignorado silenciosamente** — sem erro, sem duplicar lançamento, sem alterar a posição consolidada.

- **Dado** o reprocessamento de um lote inteiro já processado,
  **Quando** o consumidor o processa,
  **Então** o resultado final (lançamentos e totais) é **idêntico** ao de um processamento único.

### US-03 — Aceitar lançamento atrasado e fora de ordem

**Como** plataforma de consolidação,
**quero** incorporar lançamentos que chegam atrasados ou fora de ordem na competência a que pertencem,
**para** que o consolidado convirja para a verdade.

**Critérios de aceite**

- **Dado** um lançamento de competência anterior já consolidada,
  **Quando** ele chega,
  **Então** a competência é **reaberta e atualizada** (lançamento e totais), e o evento de posição atualizada é publicado (US-10).

- **Dado** a carga inicial de um cliente recém-consentido (até **12 meses** de histórico),
  **Quando** a rajada de lançamentos com datas antigas chega,
  **Então** é processada pelo fluxo normal (idempotência + competência por data de ocorrência), sem tratamento especial.

### US-04 — Só ingerir com consentimento vigente

**Como** área de regulação,
**quero** que nenhum lançamento seja incorporado sem consentimento vigente,
**para** garantir base legal de todo dado dentro de casa.

**Critérios de aceite**

- **Dado** um lançamento cujo `id_consentimento` está **vencido ou não consta**,
  **Quando** o consumidor o processa,
  **Então** o lançamento **não é incorporado** e é separado para inspeção (mesmo regime da US-08).

---

## Épico 2 — Consolidação e consulta

### US-05 — Manter a posição consolidada pronta (consolidação contínua)

**Como** plataforma,
**quero** atualizar a posição consolidada (conta × competência: entradas, saídas, saldo) na chegada de cada lançamento,
**para** que a consulta apenas leia, sem calcular.

**Critérios de aceite**

- **Dado** um lançamento incorporado,
  **Quando** a consolidação conclui,
  **Então** a posição da conta na competência reflete o lançamento (lista + totais), e a visão do cliente no mês (todas as contas) é consistente com as posições por conta.

- **Dado** o regime normal de operação,
  **Quando** um lançamento é publicado no tópico,
  **Então** ele está consultável no consolidado em **menos de 5 minutos** (meta interna de frescor; no pico degrada com transparência via carimbo).

### US-06 — Consultar o extrato consolidado (com cache)

**Como** cliente (via app),
**quero** consultar meu extrato consolidado do mês — visão geral e por conta —,
**para** ver minha vida financeira num lugar só, rápido.

**Critérios de aceite**

- **Dado** uma consulta de extrato consolidado,
  **Quando** ela é feita,
  **Então** o sistema busca **primeiro no cache** e, em caso de ausência, **no banco**, **populando o cache** ao encontrar, e responde em fração de segundo no caso típico.

- **Dado** que uma posição consolidada foi atualizada,
  **Quando** a atualização ocorre,
  **Então** a cópia daquela posição no cache é **invalidada imediatamente** (via evento — US-10), e toda entrada de cache tem **TTL** como salvaguarda.

- **Dado** um cliente sem contas conectadas ou competência sem lançamentos,
  **Quando** a consulta é feita,
  **Então** o sistema responde com extrato vazio bem definido (não erro).

### US-07 — Carimbo de atualização e atualizar sob demanda

**Como** cliente,
**quero** ver quando meu extrato foi atualizado pela última vez e poder forçar uma atualização,
**para** entender por que um gasto de agora ainda não aparece.

**Notas de domínio** *(Sessão 3 — "caso do lançamento recém-feito")*
- Não há promessa de tempo real: o dado atravessa o ecossistema Open Finance (limite físico). A resposta é **transparência**, não promessa.

**Critérios de aceite**

- **Dado** qualquer exibição de extrato consolidado,
  **Quando** ela é renderizada,
  **Então** exibe o **carimbo "atualizado às HH:MM"** da posição.

- **Dado** que o cliente aciona "atualizar",
  **Quando** a consulta é refeita,
  **Então** o sistema **ignora o cache**, lê do banco e repovoa o cache.

---

## Épico 3 — Confiabilidade

### US-08 — Não perder lançamento: re-tentativa e fila morta

**Como** áreas de produto e regulação,
**quero** que nenhum lançamento seja perdido no fluxo assíncrono,
**para** que o extrato nunca minta por omissão.

**Critérios de aceite**

- **Dado** uma falha **temporária** na consolidação (ex.: banco indisponível),
  **Quando** ela ocorre,
  **Então** o sistema **re-tenta com intervalos crescentes (backoff)**, sem descartar a mensagem, até concluir.

- **Dado** uma mensagem que falha em **todas** as tentativas (mensagem envenenada — ex.: lançamento corrompido/inválido),
  **Quando** o limite de tentativas é atingido,
  **Então** a mensagem é movida para a **fila morta (DLQ)** — guardada e inspecionável —, e o fluxo principal **continua** processando as demais.

- **Dado** uma mensagem na fila morta cuja causa foi corrigida,
  **Quando** ela é reprocessada,
  **Então** entra pelo fluxo normal (idempotência garante segurança do reprocesso).

### US-09 — Reconsolidar sob demanda

**Como** atendimento (ou operação da plataforma),
**quero** solicitar o recálculo do extrato de uma conta numa competência,
**para** resolver contestação do cliente ou refletir correções, sem chamado de TI.

**Critérios de aceite**

- **Dado** um pedido de reconsolidação (conta × competência),
  **Quando** ele é submetido,
  **Então** é registrado numa **fila de trabalho** e o solicitante recebe o aceite imediato.

- **Dado** pedidos acumulados na fila,
  **Quando** o trabalhador os processa,
  **Então** são executados **um a um** (guichê): reapura os lançamentos, refaz os totais, atualiza a posição e dispara a invalidação do cache — sem impactar a consulta em produção.

---

## Épico 4 — Integrações por evento

### US-10 — Publicar evento "posição consolidada atualizada"

**Como** áreas consumidoras (categorização/PFM, BI, notificação) e o próprio cache,
**quero** um evento publicado em tópico a cada atualização de posição,
**para** reagir sem que a consolidação precise nos conhecer.

**Notas de domínio** *(Sessão 4)*
- **Publicar o evento é MVP** ("tomada pronta"); consumir para categorização/BI/notificação é fase 2, projeto de cada área.
- O evento carrega **referência** (cliente, conta, competência, data/hora) — **não** o extrato nem dados de lançamentos (minimização, LGPD).
- A **invalidação do cache** (US-06) é o consumidor interno deste evento no MVP.

**Critérios de aceite**

- **Dado** que uma posição consolidada foi atualizada (lançamento novo, competência reaberta ou reconsolidação),
  **Quando** a atualização conclui,
  **Então** o evento é publicado no tópico com a referência, e a consolidação **não conhece** os assinantes.

---

## Épico 5 — Compliance

### US-11 — Revogação e vencimento de consentimento: cessar e expurgar

**Como** cliente (e área de regulação),
**quero** que revogar meu consentimento interrompa a ingestão na hora e elimine meus dados de forma comprovável,
**para** exercer meu direito com efeito real.

**Critérios de aceite**

- **Dado** a revogação de um consentimento,
  **Quando** ela é recebida,
  **Então** a ingestão de lançamentos daquele consentimento **cessa imediatamente** (novos lançamentos não são incorporados).

- **Dado** a revogação (ou o **vencimento sem renovação**, que tem o mesmo efeito),
  **Quando** o expurgo é executado,
  **Então** todos os dados daquele consentimento são eliminados **do banco e do cache** em **até 30 dias corridos**, as posições do cliente são **reconsolidadas** sem aqueles dados, e há **registro comprovável** do expurgo.

### US-12 — Trilha de auditoria e logs sem dado pessoal

**Como** áreas de regulação e segurança,
**quero** registro de todo acesso a extrato e logs técnicos sem dado pessoal, com correlação de ponta a ponta,
**para** responder a auditorias sem criar novo passivo de LGPD.

**Critérios de aceite**

- **Dado** uma consulta a extrato consolidado,
  **Quando** ela é feita,
  **Então** o sistema registra **quem** consultou, **qual** cliente, **quando** e **por qual canal**.

- **Dado** qualquer registro técnico (log) do sistema,
  **Quando** ele é emitido,
  **Então** contém **identificadores opacos** e um **identificador de correlação** que atravessa todos os serviços da requisição — e **nunca** valor, descrição de lançamento ou documento do cliente.

---

## Fora do escopo do MVP (catálogo / fase 2)

- Movimentar dinheiro, iniciar pagamento, alterar lançamento (**fora do escopo, sempre** — outro arranjo/sistema).
- Categorização de gastos exibida ao cliente (fase 2; finalidade original defensável, termos atualizados).
- Ofertas personalizadas e modelos (fase 2; **outra finalidade — exige base legal própria**).
- Notificação ao cliente ("seu extrato foi atualizado") — o evento da US-10 já a viabiliza.
- Busca textual, filtros por valor, exportação de PDF.
- Relatórios de BI (consomem o evento da US-10, projeto da área de dados).

## Rastreabilidade

| US | Sessão-fonte |
|---|---|
| US-01, US-02, US-03 | Sessão 2 (contexto na 1) |
| US-04 | Sessões 2 e 5 |
| US-05, US-06, US-07 | Sessão 3 |
| US-08, US-09 | Sessão 4 |
| US-10 | Sessão 4 (minimização: Sessão 5) |
| US-11, US-12 | Sessão 5 |
