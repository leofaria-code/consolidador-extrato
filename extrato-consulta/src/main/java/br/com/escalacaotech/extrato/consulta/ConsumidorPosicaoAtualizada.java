package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

/**
 * Consumidor interno do evento {@code posicao-atualizada} (US-10): invalida a
 * entrada de cache do (cliente × competência) atualizado (US-06).
 * <p>
 * Idempotente por natureza — invalidar duas vezes tem o mesmo efeito de uma:
 * cumpre a premissa de entrega "pelo menos uma vez" (Sessão 6, decisão 3).
 * Evento fora de ordem também é inofensivo: invalidação não carrega estado.
 * <p>
 * Correlação (US-12/Inc-6): o id que nasceu no POST da ingestão chega aqui
 * pelo header do evento (preservado pela outbox) e vai <b>explícito</b> no log
 * (MDC não é confiável em thread de mensageria — ver uso-de-ia.md, 07/07) —
 * o fim do fluxo loga o mesmo id do começo. Logs só com identificadores opacos.
 */
@ApplicationScoped
public class ConsumidorPosicaoAtualizada {

    private static final Logger LOG = Logger.getLogger(ConsumidorPosicaoAtualizada.class);

    @Inject
    ServicoExtrato servico;

    // @Blocking é obrigatório: com Kafka real a entrega vem no EVENT LOOP do
    // Vert.x e o @CacheInvalidate bloqueia -> "current thread cannot be
    // blocked" mata a subscription do canal (achado do plano A/Postman, 10/07;
    // o in-memory do plano B entrega em outra thread e nunca exercita isso).
    @Incoming("posicao-atualizada-in")
    @Blocking
    public CompletionStage<Void> aoAtualizarPosicao(Message<PosicaoAtualizadaEvento> mensagem) {
        var correlacao = Correlacao.deMensagem(mensagem).orElse("(sem-correlacao)");
        var evento = mensagem.getPayload();
        servico.invalidar(evento.idCliente(), evento.competencia().toString());
        LOG.infof("Cache invalidado por evento: cliente=%s competencia=%s [corr=%s]",
                evento.idCliente(), evento.competencia(), correlacao);
        return mensagem.ack();
    }
}
