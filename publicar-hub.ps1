# Publica as 5 imagens do projeto no Docker Hub (opção "rodar do Docker Hub", ADR-009).
# NÃO substitui o demo.ps1 (build-from-source) — é a via de distribuição.
#
# Uso:
#   docker login                                   # UMA vez; a credencial é sua, não do script
#   ./publicar-hub.ps1 -Namespace seu-usuario      # tag 1.0.0 + latest
#   ./publicar-hub.ps1 -Namespace seu-usuario -Tag 1.0.1
#   ./publicar-hub.ps1 -Namespace localtest -Tag dev -SkipPush   # só builda/taggeia (validação)
#
# Depois de publicar, qualquer um sobe a stack completa sem clonar/Maven/JDK:
#   docker compose -f docker-compose.hub.yml up -d              (namespace leofariacode)
#   $env:HUB_NS="seu-usuario"; docker compose -f docker-compose.hub.yml up -d   (outro namespace)
param(
  [Parameter(Mandatory = $true)][string]$Namespace,
  [string]$Tag = "1.0.0",
  [switch]$SkipPush,
  [switch]$SkipPackage
)
$ErrorActionPreference = "Stop"

# name => [contexto, dockerfile]. Prometheus/Grafana assam a config de infra/.
$imagens = [ordered]@{
  "extrato-ingestao"     = @("./extrato-ingestao", "src/main/docker/Dockerfile.jvm")
  "extrato-consolidacao" = @("./extrato-consolidacao", "src/main/docker/Dockerfile.jvm")
  "extrato-consulta"     = @("./extrato-consulta", "src/main/docker/Dockerfile.jvm")
  "extrato-prometheus"   = @("./infra/observabilidade/prometheus", "Dockerfile")
  "extrato-grafana"      = @("./infra/observabilidade/grafana", "Dockerfile")
}

if (-not $SkipPackage) {
  Write-Host "== 1/3 Empacotando os serviços (mvn -DskipTests package) ==" -ForegroundColor Cyan
  mvn --batch-mode -DskipTests package
  if ($LASTEXITCODE -ne 0) { throw "mvn package falhou" }
} else {
  Write-Host "== 1/3 Empacotamento PULADO (-SkipPackage) — usando target/ existente ==" -ForegroundColor Yellow
}

Write-Host "== 2/3 Buildando e taggeando 5 imagens ($Namespace/*:$Tag e :latest) ==" -ForegroundColor Cyan
foreach ($nome in $imagens.Keys) {
  $contexto, $dockerfile = $imagens[$nome]
  $ref = "$Namespace/${nome}:$Tag"
  Write-Host "  -> $ref" -ForegroundColor Green
  docker build -f "$contexto/$dockerfile" -t $ref -t "$Namespace/${nome}:latest" $contexto
  if ($LASTEXITCODE -ne 0) { throw "docker build de $nome falhou" }
}

if ($SkipPush) {
  Write-Host "== 3/3 Push PULADO (-SkipPush). Imagens prontas localmente. ==" -ForegroundColor Yellow
  exit 0
}

Write-Host "== 3/3 Push para o Docker Hub (exige 'docker login' previo) ==" -ForegroundColor Cyan
foreach ($nome in $imagens.Keys) {
  docker push "$Namespace/${nome}:$Tag"
  if ($LASTEXITCODE -ne 0) { throw "docker push de $nome falhou — rodou 'docker login'?" }
  docker push "$Namespace/${nome}:latest"
  if ($LASTEXITCODE -ne 0) { throw "docker push (latest) de $nome falhou" }
}

Write-Host "OK — 5 imagens publicadas em https://hub.docker.com/u/$Namespace" -ForegroundColor Green
Write-Host "Testar: `$env:HUB_NS=`"$Namespace`"; `$env:TAG=`"$Tag`"; docker compose -f docker-compose.hub.yml up -d"
