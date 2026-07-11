#!/usr/bin/env bash
# Gera as coleções de REFERÊNCIA DE API a partir do OpenAPI dos serviços
# rodando (demo de pé: ./demo.ps1). Sincronia por mecanismo, não por
# disciplina: nunca edite postman/api/* à mão — mudou endpoint, rode isto e
# commite; o CI (e2e) falha o PR se as coleções estiverem defasadas.
set -euo pipefail
cd "$(dirname "$0")/.."

declare -A SERVICOS=( [extrato-ingestao]=8081 [extrato-consolidacao]=8082 [extrato-consulta]=8083 )
mkdir -p postman/api

for svc in "${!SERVICOS[@]}"; do
  porta="${SERVICOS[$svc]}"
  spec="$(mktemp)"
  curl -sf "http://localhost:${porta}/q/openapi?format=json" -o "$spec" \
    || { echo "ERRO: ${svc} (porta ${porta}) fora do ar — suba a demo antes"; exit 1; }
  npx --yes -p openapi-to-postmanv2@6.3.0 openapi2postmanv2 \
    -s "$spec" -o "postman/api/${svc}.postman_collection.json" -p \
    -O folderStrategy=Tags,requestParametersResolution=Schema,includeAuthInfoInExample=false
  node -e "const f=process.argv[1],c=JSON.parse(require('fs').readFileSync(f,'utf8'));delete c.info._postman_id;require('fs').writeFileSync(f,JSON.stringify(c,null,2)+'\n')" "postman/api/${svc}.postman_collection.json"
  rm -f "$spec"
  echo "gerada: postman/api/${svc}.postman_collection.json"
done
echo "OK — importe no Postman ou rode com: npx newman run postman/api/<servico>.postman_collection.json --env-var baseUrl=http://localhost:<porta>"
