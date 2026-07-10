# Demo da banca (plano A) do zero, com um comando (issue #9).
# Requisitos: Java 25, Maven, Docker Desktop rodando.
$ErrorActionPreference = "Stop"

Write-Host "== 1/2 Empacotando os serviços (sem testes — o gate é o plano B) =="
mvn -DskipTests package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "== 2/2 Subindo brokers + serviços (docker compose up --build) =="
docker compose up --build
