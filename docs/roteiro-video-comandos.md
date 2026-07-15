# Roteiro "cola e grava" — comandos exatos da demo

> Companheiro do [`roteiro-video.md`](roteiro-video.md) (que tem o shot-list e as durações). Aqui estão **os comandos exatos, na ordem**, para colar enquanto grava. Todos testados na stack real.

**Onde rodar:** abra o **Git Bash** (vem com o Git for Windows) para os comandos de terminal — no PowerShell o `curl` é outro comando e o aspeamento de JSON quebra. Alternativa visual para os atos 1–5: **Postman → Run Collection** (pastas 1–7, 35 asserções verdes).

## Pré-voo (antes de gravar — corte esta parte)

```bash
docker compose up -d
# espere ~2 min e aqueça (evita timeout de stack fria):
curl -s -X POST http://localhost:8081/lancamentos -H "Content-Type: application/json" \
  -d '{"idCliente":"aquece","idLancamentoOrigem":"a1","instituicaoOrigem":"banco-a","agencia":"0001","conta":"1-0","tipo":"CREDITO","valor":1.00,"moeda":"BRL","dataHoraOcorrencia":"2026-07-15T10:00:00-03:00","idConsentimento":"c1"}' > /dev/null
```

Abra o Grafana (`localhost:3000`) e o pôster. **REC a partir daqui.**

---

## Ato 0 — abertura (card do pôster, 8s)

Legenda: *Consolidador de Extrato · Open Finance — demo*

## Ato 1 — aceite assíncrono

Legenda: *202 na hora + correlation id ecoado. Processa depois.*

```bash
curl -i -X POST http://localhost:8081/lancamentos \
  -H "Content-Type: application/json" -H "X-Correlation-Id: banca-01" \
  -d '{"idCliente":"cliente-001","idLancamentoOrigem":"banca-01-lanc","instituicaoOrigem":"banco-a","agencia":"0001","conta":"12345-6","tipo":"CREDITO","valor":250.00,"moeda":"BRL","dataHoraOcorrencia":"2026-07-15T10:00:00-03:00","idConsentimento":"consent-001"}'
```

Vê: `HTTP/1.1 202` · `X-Correlation-Id: banca-01` · `"status":"ACEITO"`

## Ato 2 — cache miss → hit

Legenda: *1ª busca na fonte; 2ª responde da memória. Carimbo = idade do dado.*

```bash
curl -s http://localhost:8083/extrato/cliente-001/2026-07   # miss → fonte
curl -s http://localhost:8083/extrato/cliente-001/2026-07   # hit → cache
```

(narre o miss/hit; o carimbo `atualizadoEm` aparece nos dois)

## Ato 3 — idempotência

Legenda: *Mesmo lançamento 2× — saldo NÃO muda. Dedup na base (ADR-004).*

```bash
curl -s -X POST http://localhost:8081/lancamentos -H "Content-Type: application/json" -H "X-Correlation-Id: banca-01" \
  -d '{"idCliente":"cliente-001","idLancamentoOrigem":"banca-01-lanc","instituicaoOrigem":"banco-a","agencia":"0001","conta":"12345-6","tipo":"CREDITO","valor":250.00,"moeda":"BRL","dataHoraOcorrencia":"2026-07-15T10:00:00-03:00","idConsentimento":"consent-001"}' -o /dev/null -w "HTTP %{http_code}\n"
curl -s http://localhost:8083/extrato/cliente-001/2026-07   # entradas iguais
```

## Ato 4 — atualizar com limite (429)

Legenda: *Releitura forçada; 2ª imediata → 429 (limite por cliente).*

```bash
curl -si "http://localhost:8083/extrato/cliente-001/2026-07?atualizar=true" | grep HTTP   # 200
curl -si "http://localhost:8083/extrato/cliente-001/2026-07?atualizar=true" | grep HTTP   # 429
```

## Ato 5 — o guichê (fila RabbitMQ)

Legenda: *Fila de trabalho — aceite imediato, processa um a um.*

```bash
curl -i -X POST http://localhost:8082/reconsolidacoes \
  -H "Content-Type: application/json" -H "X-Correlation-Id: banca-01" \
  -d '{"idCliente":"cliente-001","instituicaoOrigem":"banco-a","agencia":"0001","conta":"12345-6","competencia":"2026-07","motivo":"contestacao (demo)"}'
docker compose logs consolidacao | grep banca-01 | tail -2
```

## Ato 6 — veneno → DLQ (clímax 1) — mostre o Grafana ao lado

Legenda: *Falha permanente → DLQ com a causa. A partição NÃO trava.*

```bash
echo '{"idCliente":"c1","idLancamentoOrigem":"veneno-demo","instituicaoOrigem":"banco-x","agencia":"0001","conta":"999","dataHoraOcorrencia":"2026-07-15T12:00:00-03:00"}' | docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos"

# ~10s depois — a mensagem na DLQ com a CAUSA nos headers:
docker compose exec -T kafka sh -c "exec /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic lancamentos-recebidos-dlq --from-beginning --max-messages 1 --timeout-ms 30000 --property print.headers=true"

# prova de que o fluxo seguiu:
curl -s -X POST http://localhost:8081/lancamentos -H "Content-Type: application/json" \
  -d '{"idCliente":"pos","idLancamentoOrigem":"ok-apos","instituicaoOrigem":"banco-a","agencia":"0001","conta":"5-0","tipo":"CREDITO","valor":10.00,"moeda":"BRL","dataHoraOcorrencia":"2026-07-15T10:00:00-03:00","idConsentimento":"c1"}' -o /dev/null -w "HTTP %{http_code}\n"
```

No Grafana: **painel DLQ fica vermelho.**

## Ato 6b — final feliz do veneno

Legenda: *Reprocesso em 1 comando — seguro pela idempotência.*

```bash
./reprocessar-dlq.sh      # (Git Bash) — ou ./reprocessar-dlq.ps1 no PowerShell
```

## Ato 7 — correlação ponta a ponta

Legenda: *O mesmo id atravessa HTTP → Kafka → outbox → invalidação.*

```bash
docker compose logs | grep banca-01
```

## Ato 7b — disjuntor + última-boa (clímax 2) — Grafana ao lado

Legenda: *Fonte cai: serve a última boa com a idade exposta; sem cópia, 503 honesto.*

```bash
docker compose stop consolidacao

# hit com cache quente → ÚLTIMA-BOA (200 + carimbo antigo):
curl -si http://localhost:8083/extrato/cliente-001/2026-07 | grep -iE "HTTP|atualizadoEm"

# sem cópia → 503 + Retry-After:
curl -si http://localhost:8083/extrato/cliente-nunca-visto/2026-07 | grep -iE "HTTP|Retry-After|erro"

docker compose start consolidacao
```

## Ato 8 — testabilidade (sem Docker)

Legenda: *41 testes sem Docker · 3 contratos PACT · CI duplo verde.*

```bash
mvn verify -Pplano-b-jvm
```

Corte a espera (acelere) e pare no `BUILD SUCCESS`. Mostre a aba **Actions** do GitHub (tudo verde).

## Ato 9 — fechamento (card do pôster + URLs)

Legenda: *Repo público · imagens no Docker Hub · pôster no Pages.*

---

**Ordem de gravação:** grave **ato por ato** (para/inicia entre cada) — um erro re-grava só aquele. Depois junte no Clipchamp e adicione as legendas acima. Exporte **MP4 H.264 1080p**, guarde em 3 vetores (notebook + Drive + arquivo local).
