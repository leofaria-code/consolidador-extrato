# Imagem de observabilidade auto-suficiente (opção "rodar do Docker Hub").
# Assa o mesmo prometheus.yml que o docker-compose.yml (build-from-source) monta
# como volume — a fonte de verdade continua sendo o arquivo ao lado; esta imagem
# é só um RETRATO dele para quem sobe a stack sem clonar o repo.
# Refletir mudança do arquivo no Hub = re-rodar ../../../publicar-hub.ps1.
FROM prom/prometheus:v3.5.0
COPY prometheus.yml /etc/prometheus/prometheus.yml
