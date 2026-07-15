# Simulacao Remota do Cenario Real

Este guia mostra como rodar o script `simular-cenario-real.sh` apontando para a stack publicada no servidor remoto.

## Quando usar

Use este modo quando a aplicacao estiver rodando fora da sua maquina, por exemplo no servidor:

- `134.122.116.117`

O script vai disparar:

- novos lancamentos
- reenvio idempotente
- consultas de extrato
- refresh forcado
- reconsolidacao
- um cenario `400`
- uma mensagem envenenada para observar DLQ

## Comando correto

O comando abaixo esta pronto para copiar e colar no terminal:

```bash
SIMULATION_REMOTE_HOST=134.122.116.117 \
SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090 \
./simular-cenario-real.sh
```

## Importante

No shell, nao use crase em volta da URL.

Errado:

```bash
SIMULATION_PROMETHEUS_URL=`http://134.122.116.117:9090`
```

Certo:

```bash
SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090
```

## O que esse comando faz

- `SIMULATION_REMOTE_HOST=134.122.116.117`
  aponta os endpoints da aplicacao para:
  - `http://134.122.116.117:8081`
  - `http://134.122.116.117:8082`
  - `http://134.122.116.117:8083`

- `SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090`
  habilita a consulta final de metricas no Prometheus remoto

- `./simular-cenario-real.sh`
  executa a simulacao de uso real

## Variacoes uteis

Rodar por 2 minutos:

```bash
SIMULATION_REMOTE_HOST=134.122.116.117 \
SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090 \
SIMULATION_DURATION_SECONDS=120 \
./simular-cenario-real.sh
```

Pausar mais entre as requisicoes:

```bash
SIMULATION_REMOTE_HOST=134.122.116.117 \
SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090 \
SIMULATION_PAUSE_SECONDS=3 \
./simular-cenario-real.sh
```

Usar o modo remoto por argumento:

```bash
SIMULATION_PROMETHEUS_URL=http://134.122.116.117:9090 \
./simular-cenario-real.sh --remote 134.122.116.117
```

## Saida esperada

Ao final, o script imprime um resumo parecido com este:

```text
Resumo:
  Lancamentos novos: 4
  Reenvios idempotentes: 2
  Consultas: 3
  Atualizacoes forcadas: 1
  Reconsolidacoes: 2
  Erros inesperados: 0
  Clientes simulados: 4
```

Se `SIMULATION_PROMETHEUS_URL` estiver definido, ele tambem imprime consultas de metricas ao final.

## Dicas

- confira antes se o servidor responde em:
  - `http://134.122.116.117:8081/q/health`
  - `http://134.122.116.117:8082/q/health`
  - `http://134.122.116.117:8083/q/health`
- se o script nao executar, garanta permissao:

```bash
chmod +x ./simular-cenario-real.sh
```
