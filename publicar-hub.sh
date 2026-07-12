#!/usr/bin/env bash
# Publica as 5 imagens do projeto no Docker Hub (opção "rodar do Docker Hub", ADR-009).
# NÃO substitui o demo.sh (build-from-source) — é a via de distribuição.
#
# Uso:
#   docker login                                        # UMA vez; a credencial é sua
#   ./publicar-hub.sh -n seu-usuario                    # tag 1.0.0 + latest
#   ./publicar-hub.sh -n seu-usuario -t 1.0.1
#   ./publicar-hub.sh -n localtest -t dev --skip-push   # só builda/taggeia (validação)
#
# Depois de publicar, qualquer um sobe a stack completa sem clonar/Maven/JDK:
#   HUB_NS=seu-usuario docker compose -f docker-compose.hub.yml up -d
set -euo pipefail

NAMESPACE=""
TAG="1.0.0"
SKIP_PUSH=0
SKIP_PACKAGE=0

while [ $# -gt 0 ]; do
  case "$1" in
    -n|--namespace) NAMESPACE="$2"; shift 2 ;;
    -t|--tag)       TAG="$2"; shift 2 ;;
    --skip-push)    SKIP_PUSH=1; shift ;;
    --skip-package) SKIP_PACKAGE=1; shift ;;
    *) echo "argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$NAMESPACE" ]; then
  echo "erro: informe o namespace com -n <seu-usuario-dockerhub>" >&2
  exit 2
fi

# nome|contexto|dockerfile — Prometheus/Grafana assam a config de infra/.
IMAGENS=(
  "extrato-ingestao|./extrato-ingestao|src/main/docker/Dockerfile.jvm"
  "extrato-consolidacao|./extrato-consolidacao|src/main/docker/Dockerfile.jvm"
  "extrato-consulta|./extrato-consulta|src/main/docker/Dockerfile.jvm"
  "extrato-prometheus|./infra/observabilidade/prometheus|Dockerfile"
  "extrato-grafana|./infra/observabilidade/grafana|Dockerfile"
)

if [ "$SKIP_PACKAGE" -eq 0 ]; then
  echo "== 1/3 Empacotando os serviços (mvn -DskipTests package) =="
  mvn --batch-mode -DskipTests package
else
  echo "== 1/3 Empacotamento PULADO (--skip-package) — usando target/ existente =="
fi

echo "== 2/3 Buildando e taggeando 5 imagens ($NAMESPACE/*:$TAG e :latest) =="
for entry in "${IMAGENS[@]}"; do
  IFS='|' read -r nome contexto dockerfile <<< "$entry"
  echo "  -> $NAMESPACE/$nome:$TAG"
  docker build -f "$contexto/$dockerfile" -t "$NAMESPACE/$nome:$TAG" -t "$NAMESPACE/$nome:latest" "$contexto"
done

if [ "$SKIP_PUSH" -eq 1 ]; then
  echo "== 3/3 Push PULADO (--skip-push). Imagens prontas localmente. =="
  exit 0
fi

echo "== 3/3 Push para o Docker Hub (exige 'docker login' previo) =="
for entry in "${IMAGENS[@]}"; do
  IFS='|' read -r nome _ _ <<< "$entry"
  docker push "$NAMESPACE/$nome:$TAG"
  docker push "$NAMESPACE/$nome:latest"
done

echo "OK — 5 imagens publicadas em https://hub.docker.com/u/$NAMESPACE"
echo "Testar: HUB_NS=$NAMESPACE TAG=$TAG docker compose -f docker-compose.hub.yml up -d"
