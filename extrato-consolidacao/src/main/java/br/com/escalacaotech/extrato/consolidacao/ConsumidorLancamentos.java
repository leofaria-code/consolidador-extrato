package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Consumidor idempotente do tópico de ingestão (US-01/US-02).
 * <p>
 * Regras (Sessão 2): reenvio é comportamento normal das origens — repetido é
 * <b>ignorado silenciosamente</b>. A dedup vive na base (ADR-004); a política
 * de retentativa/DLQ vive no {@link ProcessadorLancamentos} (ADR-007).
 * <p>
 * Correlação (US-12/Inc-6): o id do header da mensagem (ou um gerado) é
 * carregado <b>explicitamente</b> pela cadeia de chamadas — o MDC provou-se
 * não confiável nas threads de mensageria do Quarkus (put+get na mesma thread
 * devolve null; ver uso-de-ia.md, 07/07). Nas bordas HTTP o MDC funciona e é
 * usado; aqui, o id vai por parâmetro e aparece explícito nos logs.
 * <p>
 * O ack ("marcar processado" — ato 2 da ADR-005) é manual: sucesso → ack e o
 * offset avança; falha esgotada → nack e a failure-strategy publica na DLQ.
 * Queda entre o commit da transação e o ack causa reentrega, que a dedup
 * reconhece — sem duplicação.
 */
@ApplicationScoped
public class ConsumidorLancamentos {

    @Inject
    ProcessadorLancamentos processador;

    @Incoming("lancamentos-in")
    @Blocking
    public CompletionStage<Void> consumir(Message<LancamentoRecebido> mensagem) {
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
