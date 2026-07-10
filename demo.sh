#!/usr/bin/env bash
# Demo da banca (plano A) do zero, com um comando (issue #9).
# Requisitos: Java 25, Maven, Docker rodando.
set -euo pipefail

echo "== 1/2 Empacotando os serviços (sem testes — o gate é o plano B) =="
mvn -DskipTests package

echo "== 2/2 Subindo brokers + serviços (docker compose up --build) =="
docker compose up --build
