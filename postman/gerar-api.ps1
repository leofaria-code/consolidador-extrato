# Gera as coleções de REFERÊNCIA DE API a partir do OpenAPI dos serviços
# rodando (demo de pé: ./demo.ps1). Nunca edite postman/api/* à mão — mudou
# endpoint, rode isto e commite; o CI (e2e) falha o PR se estiverem defasadas.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$servicos = @{ "extrato-ingestao" = 8081; "extrato-consolidacao" = 8082; "extrato-consulta" = 8083 }
New-Item -ItemType Directory -Force postman/api | Out-Null

foreach ($svc in $servicos.Keys) {
  $porta = $servicos[$svc]
  $spec = New-TemporaryFile
  Invoke-WebRequest "http://localhost:$porta/q/openapi?format=json" -OutFile $spec
  npx --yes -p openapi-to-postmanv2@6.3.0 openapi2postmanv2 `
    -s $spec -o "postman/api/$svc.postman_collection.json" -p `
    -O "folderStrategy=Tags,requestParametersResolution=Example,includeAuthInfoInExample=false"
  Remove-Item $spec -Force
  Write-Host "gerada: postman/api/$svc.postman_collection.json"
}
