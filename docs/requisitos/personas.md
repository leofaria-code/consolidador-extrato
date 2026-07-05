# Personas — Consolidador de Extrato / Open Finance

Fichas das pessoas que participam das reuniões de elicitação de requisitos. Use-as para entender de onde vem cada fala nas transcrições e por que cada uma defende o que defende. Mantêm voz consistente ao longo das cinco sessões.

> Contexto institucional: todos são colaboradores (ou prestadores) da Caixa Econômica Federal, lotados na Superintendência de Canais Digitais e áreas correlatas. As reuniões ocorrem em junho de 2026, semanais, via Microsoft Teams. O pano de fundo é o **Open Finance Brasil**: a Caixa, como **instituição receptora de dados**, recebe — mediante **consentimento** do cliente — os lançamentos das contas que ele mantém em outras instituições, para exibir um **extrato consolidado** no app.

---

## Renata Fontes — Product Owner, Open Finance / Canais Digitais

- **Papel:** facilita as reuniões, conduz a pauta, traduz negócio ↔ técnico e, ao final, **compila as user stories** que viram a primeira fonte de verdade para o time de desenvolvimento. Constrói o glossário de linguagem ubíqua durante as próprias reuniões.
- **Objetivos:** entregar um MVP de extrato consolidado que faça o cliente abrir o app da Caixa todo dia, sem estourar o prazo da janela de release; sair de cada reunião com decisões e *action items* claros.
- **Jeito de falar:** organizada, recapitula muito, faz perguntas de fechamento ("então, deixa eu cravar..."). Usa "anotei", "glossário ganhou mais uma", "isso é MVP ou fase 2?". Não deixa termo ambíguo passar sem definição.
- **Vieses (importante):** sob pressão de prazo, **simplifica ao transcrever** — tende a arredondar números, colapsar exceções e perder nuances de fase. Profissional competente; os deslizes vêm da pressa, não de incompetência. **Se houver discrepância entre as user stories e as transcrições, a origem provável é a compilação dela.**

## Márcio Bandeira — Gerente de Produto, Principalidade e Engajamento (dono do negócio)

- **Papel:** patrocinador. Responde pelo resultado de negócio: engajamento no app e **principalidade** (fazer da Caixa o banco "principal" do cliente que tem conta em vários bancos).
- **Objetivos:** aumentar frequência de acesso ao app, reduzir evasão para agregadores de terceiros, dar visibilidade executiva ("o cliente vê a vida financeira inteira dele com a gente"). Quer o MVP no ar antes da campanha de fim de ano.
- **Jeito de falar:** direto, orientado a métrica e narrativa executiva. "Isso vira principalidade", "quantos MAU isso traz?", "o concorrente já tem". Corta digressão técnica: "isso vocês resolvem entre vocês, o que o cliente sente?".
- **Vieses:** otimiza o visível ao cliente e ao board; subestima trabalho de confiabilidade "que o cliente não vê". Empurra para fase 2 tudo que não é essencial ao MVP — às vezes rápido demais.

## Dra. Patrícia Yano — Especialista em Regulação (Open Finance / Bacen) e LGPD

- **Papel:** guardiã regulatória. Valida aderência à regulamentação do Open Finance (Resolução Conjunta nº 1/2020 e normativos do Bacen), à LGPD e às políticas internas de auditoria.
- **Objetivos:** garantir que **nenhum dado seja ingerido sem consentimento vigente**, que a **revogação** tenha efeito imediato e expurgo rastreável, que a **finalidade** do tratamento seja respeitada e que exista trilha de auditoria. Evitar exposição da Caixa a sanção.
- **Jeito de falar:** precisa, cita norma e prazo com exatidão. "Consentimento tem validade máxima de 12 meses", "isso é dado pessoal, então...", "quero isso registrado em ata". Prefere travar agora a remediar depois.
- **Vieses:** conservadora; na dúvida, exige o controle mais rígido. É freio necessário para Márcio e para o apetite de dados do Igor. Insiste em separar **finalidade de exibição** de **finalidade de oferta**.

## Fábio Cardim — Arquiteto de Soluções

- **Papel:** desenha a solução técnica. Introduz na conversa os conceitos de **ingestão por tópico (publica-assina)**, **idempotência**, **consolidação assíncrona**, **cache de consulta**, **fila de reprocessamento**, **fila morta (DLQ)** e **eventos** — sempre em linguagem de conceito, **sem citar marcas de fornecedor**.
- **Objetivos:** uma solução que aguente **alta volumetria com picos** (dia de salário, benefícios, 13º, Black Friday), **não perca lançamento**, **jamais duplique lançamento** e sirva o extrato com baixa latência, integrando outras áreas sem acoplamento.
- **Jeito de falar:** pensa em voz alta sobre cenários de falha ("e se a transmissora reenviar o mesmo lançamento?", "e se isso chegar fora de ordem?"). Usa analogias para explicar conceito técnico a quem é de negócio. Pondera trade-offs.
- **Vieses:** otimiza para robustez e desacoplamento; entra em detalhe que Márcio corta. É a **voz canônica dos fatos técnicos** — quando a user story diverge do que Fábio disse, geralmente o erro está na story.

## Juliana Prates — Coordenadora de Experiência do Cliente, Canais Digitais

- **Papel:** representa a ponta — o cliente no app e o atendimento que recebe a ligação quando algo parece errado. Traz a dor concreta do dia a dia.
- **Objetivos:** que o cliente veja **todas as contas num lugar só**, com informação **confiável** — para ela, um lançamento **duplicado** ou um **saldo errado** é pior do que um atraso. Reduzir a ligação "meu extrato está errado".
- **Jeito de falar:** concreta, anedótica, fala pelo cliente. "Semana passada um cliente...", "se aparecer em dobro, ele acha que foi cobrado duas vezes", "o cliente confia no número que está na tela". Tradutora da realidade de ponta.
- **Vieses:** foca no caso de uso imediato; pode não enxergar o custo técnico do "tudo em tempo real". É quem **levanta o caso crítico** do lançamento recém-feito que ainda não aparece no consolidado — e quem aceita a solução de negócio (carimbo de "atualizado às...").

## Igor Salles — Cientista de Dados, Inteligência e Personalização *(entra nas Sessões 4 e 5)*

- **Papel:** representa o time que constrói categorização de gastos, visão financeira (PFM) e motor de ofertas personalizadas.
- **Objetivos:** ser **avisado quando o extrato consolidado de um cliente muda**, para recalcular categorização e insights — sem que o time de consolidação precise conhecê-lo. Sonha em usar os dados para ofertas.
- **Jeito de falar:** entusiasmado com dados, pragmático sobre desacoplamento. "Eu não quero travar o fluxo de vocês, só quero o evento", "com isso eu consigo dizer pro cliente quanto ele gastou de mercado no mês". 
- **Vieses:** quer dados "para ontem" e para o máximo de usos possível — o que colide com a disciplina de **finalidade** da Dra. Patrícia. Bom defensor do modelo de eventos/tópico; aceita (a contragosto) que ofertas fiquem para fase 2 com base legal própria.
