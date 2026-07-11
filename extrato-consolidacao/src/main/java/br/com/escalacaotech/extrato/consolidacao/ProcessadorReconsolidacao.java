package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

/**
 * Camada de resiliência do guichê (ADR-007) — mesma política do consumo de
 * lançamentos: 3 retentativas com backoff exponencial, cada tentativa numa
 * transação nova (via proxy CDI). O reprocesso é seguro: reconsolidação é
 * recálculo absoluto, idempotente por natureza.
 */
@ApplicationScoped
public class ProcessadorReconsolidacao {

    private static final Logger LOG = Logger.getLogger(ProcessadorReconsolidacao.class);

    @Inject
    ServicoConsolidacao servico;

    @Inject
    MeterRegistry registry;

    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS)
    @ExponentialBackoff(factor = 2, maxDelay = 10, maxDelayUnit = ChronoUnit.SECONDS)
    public void processar(PedidoReconsolidacao pedido, String correlacao) {
        LOG.infof("Guichê: processando pedido %s [corr=%s]", pedido.idPedido(), correlacao);
        servico.reconsolidar(pedido, correlacao);
        // só o desfecho conta — tentativa falha do @Retry não incrementa (ADR-008)
        registry.counter("extrato.consolidacao.reconsolidacoes").increment();
    }
}
