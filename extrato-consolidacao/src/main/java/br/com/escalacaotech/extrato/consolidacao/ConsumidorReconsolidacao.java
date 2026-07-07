package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Trabalhador da fila de reconsolidação (US-09) — o "guichê" da Sessão 4:
 * pedidos são consumidos <b>um a um</b> ({@code max-outstanding-messages=1}),
 * sem competir com a consulta em produção.
 * <p>
 * Correlação (US-12/Inc-6): o id que veio do POST /reconsolidacoes atravessa a
 * fila como propriedade AMQP e segue <b>explícito</b> pela cadeia (MDC não é
 * confiável em thread de mensageria — ver uso-de-ia.md, 07/07). Falha esgotada
 * (política no {@link ProcessadorReconsolidacao}) → nack → dead-letter exchange.
 */
@ApplicationScoped
public class ConsumidorReconsolidacao {

    @Inject
    ProcessadorReconsolidacao processador;

    @Incoming("reconsolidacao-in")
    @Blocking
    public CompletionStage<Void> processar(Message<PedidoReconsolidacao> mensagem) {
        var correlacao = Correlacao.deMensagem(mensagem)
                .orElseGet(() -> UUID.randomUUID().toString());
        try {
            processador.processar(mensagem.getPayload(), correlacao);
            return mensagem.ack();
        } catch (Exception falha) {
            return mensagem.nack(falha);
        }
    }
}
