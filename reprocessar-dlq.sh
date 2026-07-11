#!/usr/bin/env bash
# Reprocesso da DLQ (US-08, terceiro critério de aceite): republica as mensagens
# de lancamentos-recebidos-dlq no tópico principal. Seguro por construção:
# a dedup da ADR-004 ignora o que já foi incorporado; mensagens ainda ilegíveis
# voltam à DLQ pelo handler (ADR-007) — nada se perde, nada duplica.
# O consumer group 'reprocesso-dlq' lembra o offset: cada execução reprocessa
# apenas o que chegou à DLQ desde a anterior.
set -euo pipefail

echo "== Reprocessando DLQ -> lancamentos-recebidos (idempotência garante segurança) =="
docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos-dlq --group reprocesso-dlq --from-beginning --timeout-ms 10000 2>/dev/null | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos"
echo "== Concluído — conferir logs da consolidação (repetidos ignorados em debug; corrigidos incorporados) =="
