package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumidor idempotente do tópico de ingestão (US-01/US-02).
 * <p>
 * Regras (Sessão 2): reenvio é comportamento normal das origens — repetido é
 * <b>ignorado silenciosamente</b> (log em nível debug, sem erro). A identidade é
 * (instituicaoOrigem, idLancamentoOrigem).
 * <p>
 * Observabilidade (US-12): logs carregam apenas a identidade (identificadores opacos),
 * nunca valor/descrição/dados do cliente.
 * <p>
 * Próximos incrementos: consolidação em base segregada (Inc-2), retry/DLQ (Inc-4),
 * verificação de consentimento (US-04).
 */
@ApplicationScoped
public class ConsumidorLancamentos {

    private static final Logger LOG = Logger.getLogger(ConsumidorLancamentos.class);

    @Inject
    GuardaIdempotencia guardaIdempotencia;

    @Inject
    LancamentosProcessados lancamentosProcessados;

    @Incoming("lancamentos-in")
    @Blocking
    public void consumir(LancamentoRecebido lancamento) {
        var identidade = lancamento.identidade();

        if (!guardaIdempotencia.primeiraVez(identidade)) {
            LOG.debugf("Lançamento repetido ignorado (idempotência): %s", identidade);
            return;
        }

        lancamentosProcessados.incorporar(lancamento);
        LOG.infof("Lançamento incorporado: %s", identidade);
    }
}
