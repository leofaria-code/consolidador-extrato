package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;

/**
 * Consumidor idempotente do tópico de ingestão (US-01/US-02).
 * <p>
 * Regras (Sessão 2): reenvio é comportamento normal das origens — repetido é
 * <b>ignorado silenciosamente</b> (log em nível debug, sem erro). A identidade é
 * (instituicaoOrigem, idLancamentoOrigem); a dedup vive na base (ADR-004).
 * <p>
 * Resiliência (US-08, ADR-007): falha ganha <b>3 retentativas em processo</b>
 * com backoff exponencial (1s → 2s → 4s, jitter) — cada tentativa numa
 * transação nova (o retry envolve o método transacional, não o contrário).
 * Esgotadas, a exceção propaga, a mensagem é nacked e a
 * {@code failure-strategy=dead-letter-queue} a publica na DLQ com a causa nos
 * headers. O fluxo principal segue para a próxima mensagem.
 * <p>
 * O retorno deste método commita o offset ("marcar processado" — ato 2 da
 * ADR-005): queda entre o commit da transação e o do offset causa reentrega,
 * que a dedup reconhece — sem duplicação.
 * <p>
 * Observabilidade (US-12): logs carregam apenas a identidade (identificadores
 * opacos), nunca valor/descrição/dados do cliente.
 */
@ApplicationScoped
public class ConsumidorLancamentos {

    private static final Logger LOG = Logger.getLogger(ConsumidorLancamentos.class);

    @Inject
    ServicoConsolidacao servico;

    @Incoming("lancamentos-in")
    @Blocking
    @Retry(maxRetries = 3, delay = 1, delayUnit = ChronoUnit.SECONDS)
    @ExponentialBackoff(factor = 2, maxDelay = 10, maxDelayUnit = ChronoUnit.SECONDS)
    public void consumir(LancamentoRecebido lancamento) {
        var identidade = lancamento.identidade();

        switch (servico.incorporar(lancamento)) {
            case REPETIDO -> LOG.debugf(
                    "Lançamento repetido ignorado (idempotência, ADR-004): %s", identidade);
            case INCORPORADO -> LOG.infof("Lançamento incorporado: %s", identidade);
        }
    }
}
