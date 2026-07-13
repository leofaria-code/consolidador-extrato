# Prints-chave da demo (Plano D)

Capturas dos momentos que mais importam, para embutir nos slides caso a demo ao vivo **e** o vídeo falhem na máquina da banca. Todos gerados da **stack rodando de verdade** (não são mockups) — ver [`roteiro-video.md`](../roteiro-video.md) e [`roteiro-banca.md`](../roteiro-banca.md).

| Print | Ato | O que prova |
|---|---|---|
| [`01-dashboard-fluxo-saudavel.png`](01-dashboard-fluxo-saudavel.png) | 1–3 | Fluxo assíncrono ao vivo: séries **aceitos × incorporados × repetidos ignorados** (a idempotência como série temporal), cache hit ratio, DLQ em 0 (verde). |
| [`02-dashboard-dlq-vermelha.png`](02-dashboard-dlq-vermelha.png) | 6 | Após o veneno: painel **DLQ fica vermelho** (3 mensagens) e a série de **retentativas** sobe. A resiliência (peso 12) visível. |
| [`03-dashboard-disjuntor-ultima-boa.png`](03-dashboard-disjuntor-ultima-boa.png) | 7b | Com a consolidação parada: a série verde **"fallbacks (última resposta boa)"** aparece — o disjuntor servindo a cópia com carimbo antigo. |
| [`04-terminal-evidencias.png`](04-terminal-evidencias.png) | 1·6·7·7b·8 | Folha de terminal com os outputs reais: 202+correlation id, headers de causa da DLQ, o mesmo `corr` nos 3 serviços, 503+Retry-After, e `mvn verify` com **41 testes** sem Docker. |
| [`05-swagger-ingestao.png`](05-swagger-ingestao.png) | arguição | Swagger UI — superfície exploratória ("e se mandar X?"). |
| [`06-github-actions-ci-verde.png`](06-github-actions-ci-verde.png) | 8 | Todos os workflows **verdes** (verify + e2e + pages) — "o repositório se auto-fiscaliza". |
| [`07-github-repo-publico.png`](07-github-repo-publico.png) | 9 | O repo **público**, 130+ commits, 5 contributors, Java 85,6% — projeto de verdade, entregue ao longo das semanas. |

> Gerados em 13/07 pela sessão de IA (ver `uso-de-ia.md`): a stack foi subida, exercitada com tráfego real (`simular-cenario-real.sh` + veneno + parada da consolidação) e capturada headless. Regravar é reproduzir os atos e recapturar.
>
> **Fonte editável:** o print 04 é o único desenhado à mão — sua fonte é [`terminal-evidencias.html`](terminal-evidencias.html) (abre sozinho no navegador; edite e re-renderize para atualizar o PNG). Os demais 6 são capturas de sistemas ao vivo, sem fonte.
