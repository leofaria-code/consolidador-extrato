# Reprocesso da DLQ (US-08, terceiro criterio de aceite): republica as mensagens
# de lancamentos-recebidos-dlq no topico principal. Seguro por construcao:
# a dedup da ADR-004 ignora o que ja foi incorporado; mensagens ainda ilegiveis
# voltam a DLQ pelo handler (ADR-007) — nada se perde, nada duplica.
# O consumer group 'reprocesso-dlq' lembra o offset: cada execucao reprocessa
# apenas o que chegou a DLQ desde a anterior.
$ErrorActionPreference = "Stop"

Write-Host "== Reprocessando DLQ -> lancamentos-recebidos (idempotencia garante seguranca) =="
docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos-dlq --group reprocesso-dlq --from-beginning --timeout-ms 10000 2>/dev/null | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos"
Write-Host "== Concluido — conferir logs da consolidacao (repetidos ignorados em debug; corrigidos incorporados) =="
