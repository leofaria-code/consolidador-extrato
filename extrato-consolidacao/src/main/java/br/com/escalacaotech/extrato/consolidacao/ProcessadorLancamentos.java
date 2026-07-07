package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

/**
 * Camada de resiliência do consumo de lançamentos (ADR-007): 3 retentativas
 * em processo com backoff exponencial (1s → 2s → 4s, jitter), cada tentativa
 * numa transação nova. Vive num bean separado do consumidor porque o retry
 * precisa envolver a transação via proxy CDI — e o consumidor, desde o Inc-6,
 * gerencia ack/nack manualmente para extrair a correlação (US-12).
 * <p>
 * O retry roda na mesma thread ⇒ o MDC posto pelo consumidor vale para os
 * logs de todas as tentativas.
 */
@ApplicationScoped
public class ProcessadorLancamentos {

    private static final Logger LOG = Logger.getLogger(ProcessadorLancamentos.class);

    @Inject
    ServicoConsolidacao servico;

    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS)
    @ExponentialBackoff(factor = 2, maxDelay = 10, maxDelayUnit = ChronoUnit.SECONDS)
    public void processar(LancamentoRecebido lancamento, String correlacao) {
        var identidade = lancamento.identidade();

        switch (servico.incorporar(lancamento, correlacao)) {
            case REPETIDO -> LOG.debugf(
                    "Lançamento repetido ignorado (idempotência, ADR-004): %s [corr=%s]",
                    identidade, correlacao);
            case INCORPORADO -> LOG.infof("Lançamento incorporado: %s [corr=%s]",
                    identidade, correlacao);
        }
    }
}
