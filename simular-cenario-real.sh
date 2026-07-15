#!/usr/bin/env bash
# Simula 1 minuto de uso "realista" da aplicacao:
# - novos lancamentos
# - consultas de extrato
# - reenvio do mesmo lancamento (idempotencia)
# - reconsolidacao sob demanda
# - atualizacao forcada sem disparar 429 no mesmo cliente
#
# Uso:
#   ./simular-cenario-real.sh
#   SIMULATION_DURATION_SECONDS=90 ./simular-cenario-real.sh
#   SIMULATION_INGESTAO_URL=http://localhost:8081 \
#   SIMULATION_CONSOLIDACAO_URL=http://localhost:8082 \
#   SIMULATION_CONSULTA_URL=http://localhost:8083 \
#   ./simular-cenario-real.sh
#   ./simular-cenario-real.sh --remote 134.122.116.117
#   SIMULATION_REMOTE_HOST=134.122.116.117 ./simular-cenario-real.sh

set -euo pipefail

TARGET_MODE="${SIMULATION_TARGET:-}"
REMOTE_HOST="${SIMULATION_REMOTE_HOST:-}"
REMOTE_SCHEME="${SIMULATION_REMOTE_SCHEME:-http}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --local)
      TARGET_MODE="local"
      shift
      ;;
    --remote)
      if [ "$#" -lt 2 ]; then
        printf 'Uso incorreto: informe o host apos --remote\n' >&2
        exit 1
      fi
      TARGET_MODE="remote"
      REMOTE_HOST="$2"
      shift 2
      ;;
    *)
      printf 'Argumento desconhecido: %s\n' "$1" >&2
      exit 1
      ;;
  esac
done

if [ -z "$TARGET_MODE" ] && [ -n "$REMOTE_HOST" ]; then
  TARGET_MODE="remote"
fi

if [ -z "$TARGET_MODE" ]; then
  TARGET_MODE="local"
fi

if [ "$TARGET_MODE" = "remote" ] && [ -z "$REMOTE_HOST" ]; then
  printf 'Modo remoto exige SIMULATION_REMOTE_HOST ou --remote <host>\n' >&2
  exit 1
fi

if [ "$TARGET_MODE" = "remote" ]; then
  URL_INGESTAO_DEFAULT="${REMOTE_SCHEME}://${REMOTE_HOST}:8081"
  URL_CONSOLIDACAO_DEFAULT="${REMOTE_SCHEME}://${REMOTE_HOST}:8082"
  URL_CONSULTA_DEFAULT="${REMOTE_SCHEME}://${REMOTE_HOST}:8083"
  URL_PROMETHEUS_DEFAULT="${REMOTE_SCHEME}://${REMOTE_HOST}:9090"
fi

if [ "$TARGET_MODE" = "local" ]; then
  URL_INGESTAO_DEFAULT="http://localhost:8081"
  URL_CONSOLIDACAO_DEFAULT="http://localhost:8082"
  URL_CONSULTA_DEFAULT="http://localhost:8083"
  URL_PROMETHEUS_DEFAULT=""
fi

readonly TARGET_MODE
readonly REMOTE_HOST
readonly URL_INGESTAO="${SIMULATION_INGESTAO_URL:-$URL_INGESTAO_DEFAULT}"
readonly URL_CONSOLIDACAO="${SIMULATION_CONSOLIDACAO_URL:-$URL_CONSOLIDACAO_DEFAULT}"
readonly URL_CONSULTA="${SIMULATION_CONSULTA_URL:-$URL_CONSULTA_DEFAULT}"
readonly URL_PROMETHEUS="${SIMULATION_PROMETHEUS_URL:-$URL_PROMETHEUS_DEFAULT}"

DURATION_SECONDS="${SIMULATION_DURATION_SECONDS:-60}"
PAUSE_SECONDS="${SIMULATION_PAUSE_SECONDS:-2}"
COMPETENCIA="${SIMULATION_COMPETENCIA:-$(date +%Y-%m)}"

readonly EXECUCAO_ID="$(date +%Y%m%d%H%M%S)"
readonly DESCRICOES_CREDITO=("salario" "pix recebido" "transferencia recebida")
readonly DESCRICOES_DEBITO=("pix enviado" "pagamento boleto" "compra cartao")
readonly INSTITUICOES=("banco-a" "banco-b" "banco-c")
readonly AGENCIAS=("0001" "0002" "0101")

clientes=()
instituicoes_por_cliente=()
agencias_por_cliente=()
contas_por_cliente=()
clientes_com_refresh="|"

ultimo_cliente=""
ultima_instituicao=""
ultima_agencia=""
ultima_conta=""
ultimo_lancamento=""
ultimo_tipo=""
ultimo_valor=""
cliente_do_ultimo_lancamento=""
instituicao_do_ultimo_lancamento=""
agencia_do_ultimo_lancamento=""
conta_do_ultimo_lancamento=""
tipo_do_ultimo_lancamento=""
valor_do_ultimo_lancamento=""

total_lancamentos_novos=0
total_reenvios_idempotentes=0
total_consultas=0
total_refresh_forcado=0
total_reconsolidacoes=0
total_erros=0

RESPONSE_BODY=""
RESPONSE_CODE=""

agora() {
  date +"%H:%M:%S"
}

log() {
  printf '[%s] %s\n' "$(agora)" "$*"
}

descrever_destino() {
  if [ "$TARGET_MODE" = "remote" ]; then
    printf 'remoto (%s)' "$REMOTE_HOST"
    return 0
  fi

  printf 'local'
}

escolher_item() {
  eval "local itens=(\"\${$1[@]}\")"
  local indice=$((RANDOM % ${#itens[@]}))
  printf '%s' "${itens[$indice]}"
}

novo_cliente() {
  local indice=$(( ${#clientes[@]} + 1 ))
  local cliente="cliente-simulacao-${EXECUCAO_ID}-${indice}"
  local instituicao
  local agencia
  local conta

  instituicao="$(escolher_item INSTITUICOES)"
  agencia="$(escolher_item AGENCIAS)"
  conta="$(printf '%05d-%d' $((10000 + indice)) $((indice % 9)))"

  clientes+=("$cliente")
  instituicoes_por_cliente+=("$instituicao")
  agencias_por_cliente+=("$agencia")
  contas_por_cliente+=("$conta")
}

possui_clientes() {
  [ "${#clientes[@]}" -gt 0 ]
}

carregar_cliente_por_indice() {
  local indice="$1"
  ultimo_cliente="${clientes[$indice]}"
  ultima_instituicao="${instituicoes_por_cliente[$indice]}"
  ultima_agencia="${agencias_por_cliente[$indice]}"
  ultima_conta="${contas_por_cliente[$indice]}"
}

selecionar_cliente_existente() {
  if ! possui_clientes; then
    novo_cliente
  fi

  local indice=$((RANDOM % ${#clientes[@]}))
  carregar_cliente_por_indice "$indice"
}

cliente_ja_recebeu_refresh() {
  case "$clientes_com_refresh" in
    *"|$1|"*) return 0 ;;
  esac
  return 1
}

marcar_refresh() {
  clientes_com_refresh="${clientes_com_refresh}$1|"
}

request_json() {
  local metodo="$1"
  local url="$2"
  local body="${3:-}"
  local correlation_id="${4:-}"
  local arquivo_resposta
  arquivo_resposta="$(mktemp)"

  local args=(-sS -o "$arquivo_resposta" -w "%{http_code}" -X "$metodo")

  if [ -n "$correlation_id" ]; then
    args+=(-H "X-Correlation-Id: $correlation_id")
  fi

  if [ -n "$body" ]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi

  RESPONSE_CODE="$(curl "${args[@]}" "$url")"
  RESPONSE_BODY="$(cat "$arquivo_resposta")"
  rm -f "$arquivo_resposta"
}

registrar_erro_se_necessario() {
  local descricao="$1"

  case "$RESPONSE_CODE" in
    200|202|204|400|429)
      return 0
      ;;
  esac

  total_erros=$((total_erros + 1))
  log "ERRO em ${descricao}: HTTP ${RESPONSE_CODE} :: ${RESPONSE_BODY}"
}

criar_payload_lancamento() {
  local cliente="$1"
  local lancamento="$2"
  local instituicao="$3"
  local agencia="$4"
  local conta="$5"
  local tipo="$6"
  local valor="$7"
  local descricao="$8"
  local data_hora="$9"

  cat <<EOF
{
  "idCliente": "${cliente}",
  "idLancamentoOrigem": "${lancamento}",
  "instituicaoOrigem": "${instituicao}",
  "agencia": "${agencia}",
  "conta": "${conta}",
  "tipo": "${tipo}",
  "valor": ${valor},
  "moeda": "BRL",
  "dataHoraOcorrencia": "${data_hora}",
  "idConsentimento": "consentimento-${EXECUCAO_ID}",
  "descricao": "${descricao}",
  "categoriaOrigem": "transferencia"
}
EOF
}

simular_lancamento_novo() {
  novo_cliente
  carregar_cliente_por_indice $(( ${#clientes[@]} - 1 ))

  local correlacao="corr-${EXECUCAO_ID}-${RANDOM}"
  local sequencia=$((total_lancamentos_novos + total_reenvios_idempotentes + 1))
  local tipo="CREDITO"
  local valor
  local descricao
  local dia
  local hora
  local payload

  if [ $((RANDOM % 4)) -eq 0 ]; then
    tipo="DEBITO"
  fi

  valor="$(printf '%d.%02d' $((50 + RANDOM % 400)) $((RANDOM % 90)))"
  dia="$(printf '%02d' $((10 + RANDOM % 10)))"
  hora="$(printf '%02d' $((8 + RANDOM % 10)))"

  if [ "$tipo" = "CREDITO" ]; then
    descricao="$(escolher_item DESCRICOES_CREDITO)"
  else
    descricao="$(escolher_item DESCRICOES_DEBITO)"
  fi

  ultimo_lancamento="lanc-simulacao-${EXECUCAO_ID}-${sequencia}"
  ultimo_tipo="$tipo"
  ultimo_valor="$valor"
  cliente_do_ultimo_lancamento="$ultimo_cliente"
  instituicao_do_ultimo_lancamento="$ultima_instituicao"
  agencia_do_ultimo_lancamento="$ultima_agencia"
  conta_do_ultimo_lancamento="$ultima_conta"
  tipo_do_ultimo_lancamento="$ultimo_tipo"
  valor_do_ultimo_lancamento="$ultimo_valor"

  payload="$(criar_payload_lancamento \
    "$ultimo_cliente" \
    "$ultimo_lancamento" \
    "$ultima_instituicao" \
    "$ultima_agencia" \
    "$ultima_conta" \
    "$ultimo_tipo" \
    "$ultimo_valor" \
    "$descricao" \
    "${COMPETENCIA}-${dia}T${hora}:15:00-03:00")"

  request_json "POST" "${URL_INGESTAO}/lancamentos" "$payload" "$correlacao"
  total_lancamentos_novos=$((total_lancamentos_novos + 1))
  log "POST lancamento novo -> HTTP ${RESPONSE_CODE} cliente=${ultimo_cliente} tipo=${ultimo_tipo} valor=${ultimo_valor}"
  registrar_erro_se_necessario "POST /lancamentos"
}

simular_reenvio_idempotente() {
  if [ -z "$ultimo_lancamento" ]; then
    simular_lancamento_novo
    return 0
  fi

  local correlacao="corr-reenvio-${EXECUCAO_ID}-${RANDOM}"
  local payload

  payload="$(criar_payload_lancamento \
    "$cliente_do_ultimo_lancamento" \
    "$ultimo_lancamento" \
    "$instituicao_do_ultimo_lancamento" \
    "$agencia_do_ultimo_lancamento" \
    "$conta_do_ultimo_lancamento" \
    "$tipo_do_ultimo_lancamento" \
    "$valor_do_ultimo_lancamento" \
    "reenvio idempotente" \
    "${COMPETENCIA}-20T11:45:00-03:00")"

  request_json "POST" "${URL_INGESTAO}/lancamentos" "$payload" "$correlacao"
  total_reenvios_idempotentes=$((total_reenvios_idempotentes + 1))
  log "POST reenvio idempotente -> HTTP ${RESPONSE_CODE} cliente=${cliente_do_ultimo_lancamento} lancamento=${ultimo_lancamento}"
  registrar_erro_se_necessario "POST /lancamentos (idempotente)"
}

simular_consulta() {
  selecionar_cliente_existente

  request_json "GET" "${URL_CONSULTA}/extrato/${ultimo_cliente}/${COMPETENCIA}"
  total_consultas=$((total_consultas + 1))
  log "GET extrato -> HTTP ${RESPONSE_CODE} cliente=${ultimo_cliente}"
  registrar_erro_se_necessario "GET /extrato"
}

simular_refresh_forcado() {
  local indice=0
  local cliente_selecionado=""

  if ! possui_clientes; then
    simular_lancamento_novo
  fi

  while [ "$indice" -lt "${#clientes[@]}" ]; do
    if ! cliente_ja_recebeu_refresh "${clientes[$indice]}"; then
      cliente_selecionado="${clientes[$indice]}"
      break
    fi
    indice=$((indice + 1))
  done

  if [ -z "$cliente_selecionado" ]; then
    simular_consulta
    return 0
  fi

  carregar_cliente_por_indice "$indice"
  request_json "GET" "${URL_CONSULTA}/extrato/${ultimo_cliente}/${COMPETENCIA}?atualizar=true"
  total_refresh_forcado=$((total_refresh_forcado + 1))
  marcar_refresh "$ultimo_cliente"
  log "GET extrato?atualizar=true -> HTTP ${RESPONSE_CODE} cliente=${ultimo_cliente}"
  registrar_erro_se_necessario "GET /extrato?atualizar=true"
}

simular_reconsolidacao() {
  selecionar_cliente_existente

  local correlacao="corr-reconsolidacao-${EXECUCAO_ID}-${RANDOM}"
  local payload
  payload="$(cat <<EOF
{
  "idCliente": "${ultimo_cliente}",
  "instituicaoOrigem": "${ultima_instituicao}",
  "agencia": "${ultima_agencia}",
  "conta": "${ultima_conta}",
  "competencia": "${COMPETENCIA}",
  "motivo": "simulacao automatizada de um minuto"
}
EOF
)"

  request_json "POST" "${URL_CONSOLIDACAO}/reconsolidacoes" "$payload" "$correlacao"
  total_reconsolidacoes=$((total_reconsolidacoes + 1))
  log "POST reconsolidacao -> HTTP ${RESPONSE_CODE} cliente=${ultimo_cliente}"
  registrar_erro_se_necessario "POST /reconsolidacoes"
}

verificar_saude() {
  request_json "GET" "${URL_INGESTAO}/q/health"
  [ "$RESPONSE_CODE" = "200" ] || return 1

  request_json "GET" "${URL_CONSOLIDACAO}/q/health"
  [ "$RESPONSE_CODE" = "200" ] || return 1

  request_json "GET" "${URL_CONSULTA}/q/health"
  [ "$RESPONSE_CODE" = "200" ] || return 1
}

imprimir_metricas_prometheus() {
  if [ -z "$URL_PROMETHEUS" ]; then
    return 0
  fi

  log "Resumo Prometheus"
  curl -sS "${URL_PROMETHEUS}/api/v1/query?query=extrato_ingestao_lancamentos_total" || true
  printf '\n'
  curl -sS "${URL_PROMETHEUS}/api/v1/query?query=extrato_consolidacao_lancamentos_total" || true
  printf '\n'
  curl -sS "${URL_PROMETHEUS}/api/v1/query?query=cache_gets_total" || true
  printf '\n'
}

main() {
  local inicio
  local fim
  local iteracao=1
  local acao=0

  log "Iniciando simulacao por ${DURATION_SECONDS}s"
  log "Destino: $(descrever_destino)"
  log "Servicos: ingestao=${URL_INGESTAO} consolidacao=${URL_CONSOLIDACAO} consulta=${URL_CONSULTA}"

  if ! verificar_saude; then
    log "Falha no health check inicial. Suba a stack antes de rodar a simulacao."
    exit 1
  fi

  inicio="$(date +%s)"
  fim=$((inicio + DURATION_SECONDS))

  simular_lancamento_novo
  sleep 1
  simular_consulta

  while [ "$(date +%s)" -lt "$fim" ]; do
    acao=$((iteracao % 5))

    case "$acao" in
      1)
        simular_lancamento_novo
        ;;
      2)
        simular_consulta
        ;;
      3)
        simular_reenvio_idempotente
        ;;
      4)
        simular_reconsolidacao
        ;;
      0)
        simular_refresh_forcado
        ;;
    esac

    iteracao=$((iteracao + 1))
    sleep "$PAUSE_SECONDS"
  done

  log "Fim da simulacao"
  printf '\nResumo:\n'
  printf '  Lancamentos novos: %s\n' "$total_lancamentos_novos"
  printf '  Reenvios idempotentes: %s\n' "$total_reenvios_idempotentes"
  printf '  Consultas: %s\n' "$total_consultas"
  printf '  Atualizacoes forcadas: %s\n' "$total_refresh_forcado"
  printf '  Reconsolidacoes: %s\n' "$total_reconsolidacoes"
  printf '  Erros inesperados: %s\n' "$total_erros"
  printf '  Clientes simulados: %s\n' "${#clientes[@]}"

  imprimir_metricas_prometheus
}

main "$@"
