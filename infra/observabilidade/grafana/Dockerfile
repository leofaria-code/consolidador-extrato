# Imagem de observabilidade auto-suficiente (opção "rodar do Docker Hub").
# Assa o datasource + o dashboard "visão da banca" que o docker-compose.yml
# (build-from-source) monta como volume — a fonte de verdade continua sendo os
# arquivos ao lado; esta imagem é só um RETRATO deles para quem sobe a stack sem
# clonar o repo. Viewer anônimo embutido: abre sem login, igual à demo.
# Refletir mudança de painel no Hub = re-rodar ../../../publicar-hub.ps1.
FROM grafana/grafana:12.1.0
ENV GF_AUTH_ANONYMOUS_ENABLED=true \
    GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer
COPY provisioning /etc/grafana/provisioning
COPY dashboards /var/lib/grafana/dashboards
