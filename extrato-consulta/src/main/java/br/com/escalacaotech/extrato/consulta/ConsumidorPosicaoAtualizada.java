package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

/**
 * Consumidor interno do evento {@code posicao-atualizada} (US-10): invalida a
 * entrada de cache do (cliente × competência) atualizado (US-06).
 * <p>
 * Idempotente por natureza — invalidar duas vezes tem o mesmo efeito de uma:
 * cumpre a premissa de entrega "pelo menos uma vez" (Sessão 6, decisão 3).
 * Evento fora de ordem também é inofensivo: invalidação não carrega estado.
 * <p>
 * Logs só com identificadores opacos (US-12).
 */
@ApplicationScoped
public class ConsumidorPosicaoAtualizada {

    private static final Logger LOG = Logger.getLogger(ConsumidorPosicaoAtualizada.class);

    @Inject
    ServicoExtrato servico;

    @Incoming("posicao-atualizada-in")
    public void aoAtualizarPosicao(PosicaoAtualizadaEvento evento) {
        servico.invalidar(evento.idCliente(), evento.competencia().toString());
        LOG.debugf("Cache invalidado por evento: cliente=%s competencia=%s",
                evento.idCliente(), evento.competencia());
    }
}
