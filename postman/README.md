# Coleções Postman

Duas coleções, dois papéis — cada uma com um mecanismo de sincronia próprio:

| Coleção | Papel | Sincronia |
|---|---|---|
| `consolidador-extrato.postman_collection.json` | **Demo/banca**: roteiro ensaiado com 35 asserções (inclui broadcast entre réplicas e observabilidade — pasta 7) | Manual disciplinada (editar → Newman validar → PR) |
| `api/*.postman_collection.json` (1 por serviço) | **Referência de API**: toda a superfície HTTP | **Gerada do OpenAPI** (`gerar-api.ps1`/`.sh`) — nunca editar à mão; o guarda **semântico** do CI (`verificar-api.sh`: compara método+path do OpenAPI × coleção) falha o PR se defasarem. Byte-diff não serve: o conversor gera ids/exemplos aleatórios a cada execução |

Mudou/adicionou endpoint? Suba a demo e rode `./postman/gerar-api.ps1`, commite o diff. O guarda no workflow `e2e` garante que ninguém esqueça.

# Coleção Postman da demo

**Fonte de verdade:** `consolidador-extrato.postman_collection.json` — roda **sem conta Postman** e é o que o CI e a banca executam:

```bash
npx newman run postman/consolidador-extrato.postman_collection.json   # 35 asserções
```

Pré-requisito: a stack de demo de pé (`./demo.ps1`).

## Por que JSON, e não o sync de workspace do Postman?

O repo-sync do Postman (`.postman/`, `postman/collections/**.yaml`) amarra ao workspace **de uma conta** — quem clona não consegue executá-lo — e `environments/globals` são a categoria de arquivo onde segredos acabam parando. Por isso ficam no `.gitignore`: são o "`.idea/` do Postman" — úteis localmente, ruído no git. O JSON é executável por qualquer um, diffável e automatizável (Newman/CI).

## Fluxo de edição

1. **Editar visualmente:** importe o JSON no seu Postman (File → Import) e trabalhe à vontade.
2. **Promover ao time:** exporte a coleção (Collection v2.1) por cima do JSON canônico e abra PR — o diff mostra a mudança request a request, e o workflow `e2e` valida as asserções contra a stack real.
3. Asserções novas: prefira **relativas ao estado** (ver pastas 2 e 6) — a coleção precisa passar N vezes seguidas contra a base descartável da demo.

## Coleções de observabilidade

As coleções abaixo ficam versionadas à mão no mesmo modelo da coleção da demo, porque descrevem cenários operacionais da banca e do ensaio:

| Coleção | Papel | Como rodar |
|---|---|---|
| `observabilidade-carga-continua.postman_collection.json` | Gera tráfego leve e observável (POST → GET → Prometheus) com ids dinâmicos por iteração | Collection Runner com `5-20` iterações e `delay` curto |
| `observabilidade-metricas-stack.postman_collection.json` | Verifica health, `/q/metrics`, Prometheus e Grafana | Rodar request a request ou coleção inteira |
| `observabilidade-dlq.postman_collection.json` | Faz a prova do contador de DLQ antes/depois da injeção de veneno | Rodar até o passo manual, injetar no Kafka pelo terminal e continuar |

Pré-requisito: stack da demo com observabilidade (`docker compose --profile observabilidade up -d --build`). Se você subiu Prometheus/Grafana em portas customizadas, ajuste as variáveis `urlPrometheus` e `urlGrafana` na aba de variáveis da coleção.

### Carga contínua

Use a coleção `observabilidade-carga-continua.postman_collection.json` no Runner para simular tráfego repetido sem esbarrar no `429` do `?atualizar=true`: cada iteração cria um `idCliente`, `idLancamentoOrigem` e `X-Correlation-Id` próprios. O fluxo termina consultando o Prometheus para verificar que os sinais principais continuam presentes.

### DLQ

A coleção `observabilidade-dlq.postman_collection.json` **não tenta publicar no Kafka via Postman**, porque a prova da DLQ deste projeto depende do broker real. O passo de injeção continua manual e fica documentado dentro da própria request auxiliar:

```bash
echo 'isto-nao-e-json' | docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos"
```

Se a stack estiver rodando no servidor remoto `134.122.116.117`, use este comando:

```bash
ssh root@134.122.116.117 "cd /opt/consolidador-extrato && printf '%s\n' 'isto-nao-e-json' | docker compose exec -T kafka sh -c 'exec /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos'"
```

Depois disso, a coleção valida pelo Prometheus que `extrato_consolidacao_dlq_enviados_total` aumentou e que a consolidação permaneceu `UP`.
