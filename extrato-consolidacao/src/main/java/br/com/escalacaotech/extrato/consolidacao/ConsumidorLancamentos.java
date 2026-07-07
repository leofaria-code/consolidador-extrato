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
 * (instituicaoOrigem, idLancamentoOrigem); a dedup vive na base (ADR-004).
 * <p>
 * O retorno deste método commita o offset ("marcar processado" — ato 2 da
 * ADR-005): queda entre o commit da transação e o do offset causa reentrega,
 * que a dedup reconhece — sem duplicação.
 * <p>
 * Observabilidade (US-12): logs carregam apenas a identidade (identificadores
 * opacos), nunca valor/descrição/dados do cliente.
 * <p>
 * Próximos incrementos: retry/DLQ (Inc-4), verificação de consentimento (US-04).
 */
@ApplicationScoped
public class ConsumidorLancamentos {

    private static final Logger LOG = Logger.getLogger(ConsumidorLancamentos.class);

    @Inject
    ServicoConsolidacao servico;

    @Incoming("lancamentos-in")
    @Blocking
    public void consumir(LancamentoRecebido lancamento) {
        var identidade = lancamento.identidade();

        switch (servico.incorporar(lancamento)) {
            case REPETIDO -> LOG.debugf(
                    "Lançamento repetido ignorado (idempotência, ADR-004): %s", identidade);
            case INCORPORADO -> LOG.infof("Lançamento incorporado: %s", identidade);
        }
    }
}
