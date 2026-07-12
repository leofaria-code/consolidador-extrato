echo 'isto-nao-e-json' | \
docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos"