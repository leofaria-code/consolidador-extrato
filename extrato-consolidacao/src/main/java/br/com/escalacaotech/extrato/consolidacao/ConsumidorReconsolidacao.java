package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

/**
 * Trabalhador da fila de reconsolidação (US-09) — o "guichê" da Sessão 4:
 * pedidos são consumidos <b>um a um</b> ({@code max-outstanding-messages=1}),
 * sem competir com a consulta em produção.
 * <p>
 * Mesma política de resiliência do consumo de lançamentos (ADR-007): 3
 * retentativas com backoff exponencial; esgotadas, o {@code reject} encaminha
 * ao dead-letter exchange ({@code reconsolidacao-dlq}). O reprocesso é seguro:
 * a reconsolidação é recálculo absoluto — idempotente por natureza.
 */
@ApplicationScoped
public class ConsumidorReconsolidacao {

    private static final Logger LOG = Logger.getLogger(ConsumidorReconsolidacao.class);

    @Inject
    ServicoConsolidacao servico;

    @Incoming("reconsolidacao-in")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS)
    @ExponentialBackoff(factor = 2, maxDelay = 10, maxDelayUnit = ChronoUnit.SECONDS)
    public void processar(PedidoReconsolidacao pedido) {
        LOG.infof("Guichê: processando pedido %s", pedido.idPedido());
        servico.reconsolidar(pedido);
    }
}
