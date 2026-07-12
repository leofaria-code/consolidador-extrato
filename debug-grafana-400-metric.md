# Debug Session: grafana-400-metric [OPEN]

## Sintoma
- Uma chamada retornou `400`.
- O Grafana nao mostrou alteracao visivel no grafico.

## Escopo inicial
- Coletar evidencias em runtime sem alterar logica de negocio.
- Verificar se o `400` deveria ou nao alimentar os paineis atuais.
- Confirmar quais metricas sobem no Prometheus e quais consultas o dashboard usa.

## Hipoteses
1. O request `400` foi rejeitado na borda e, por desenho, nao alimenta os graficos principais do dashboard, que priorizam fluxo aceito/incorporado/idempotencia.
2. O dashboard esta consultando metricas que nao incluem o evento de rejeicao, entao houve alteracao em outra serie nao visivel no painel esperado.
3. O request `400` nao era do tipo de chamada que movimenta as series de negocio do dashboard, apenas erros HTTP genéricos.
4. O Prometheus recebeu a metrica correta, mas a janela temporal / refresh do Grafana nao permitiu ver a mudanca no momento da observacao.
5. A chamada `400` aconteceu antes de chegar ao ponto de instrumentacao de negocio esperado, entao so `http_server_requests_*` foi incrementada.

## Plano de evidencia
- Reproduzir um `400`.
- Consultar `/q/metrics` e Prometheus antes/depois.
- Conferir as queries do dashboard versionado.
- Fechar com causa raiz e, se necessario, ajustar dashboard/documentacao.

## Evidencias coletadas
- O dashboard versionado `infra/observabilidade/grafana/dashboards/consolidador-extrato.json` plota apenas:
  - `extrato_ingestao_lancamentos_total{resultado="aceito"}`
  - `extrato_consolidacao_lancamentos_total{resultado="incorporado"}`
  - `extrato_consolidacao_lancamentos_total{resultado="repetido"}`
- A documentacao confirma que existe a metrica de borda com `resultado=aceito|rejeitado`.
- O endpoint da ingestao incrementa `contar("rejeitado")` quando faltam campos e retorna `400`.
- Reproducao do `400` com payload invalido:
  - resposta: `{"camposFaltantes":["valor","idConsentimento"],"erro":"ficha do lançamento inválida"}`
  - metrica em `/q/metrics` da ingestao mudou de `ANTES=28.0` para `DEPOIS=29.0` em `extrato_ingestao_lancamentos_total{resultado="rejeitado"}`
- O Prometheus tambem expôs a serie `resultado="rejeitado"` apos o scrape.

## Conclusao atual
- Causa raiz confirmada: o `400` gera metrica, mas o dashboard principal nao plota a serie `rejeitado`.
- Efeito observado: o Grafana parecer "nao mexer" e esperado se voce estiver olhando apenas o painel principal atual.

## Evidencia adicional - tempo real
- O Prometheus usa `scrape_interval: 5s`.
- O dashboard usa `refresh: 5s`.
- A serie nova de rejeitados foi adicionada como `sum(rate(extrato_ingestao_lancamentos_total{resultado="rejeitado"}[1m]))`.
- Reproducao:
  - `HTTP 400` aconteceu imediatamente.
  - `CHECK-1` logo apos o request ainda mostrou `0` na query `rate(...)`.
  - `CHECK-2` e `CHECK-3`, apos o scrape, mostraram `0.0181818... ops/s`.
- Interpretacao:
  - existe atraso natural de ate um ciclo de scrape/refresh para o ponto aparecer;
  - um unico `400` em janela de 1 minuto vira um valor muito pequeno (`~0.018 ops/s`), facil de passar despercebido no mesmo grafico de outras series.

## Hipotese confirmada
- A serie existe e sobe, mas um evento isolado em `rate(...[1m])` nao fica visualmente obvio em tempo real no painel atual.

## Evidencia adicional - porta/instancia
- `docker compose ps grafana` mostra a stack atual em `0.0.0.0:3001->3000/tcp`.
- `http://localhost:3000/api/health` respondeu com Grafana `11.2.0`.
- `http://localhost:3001/api/health` respondeu com Grafana `12.1.0` (container atual do projeto).
- A API do dashboard em `3001` contem a serie nova `rejeitados no ultimo minuto (ingestão 400)`.

## Nova conclusao
- Ha alta probabilidade de observacao na instancia errada (`3000`) em vez da stack atual (`3001`).
