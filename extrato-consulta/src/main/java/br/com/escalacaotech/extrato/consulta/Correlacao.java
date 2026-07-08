package br.com.escalacaotech.extrato.consulta;

import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Correlação ponta a ponta (US-12): o mesmo id atravessa HTTP (header
 * {@code X-Correlation-Id}), o tópico de eventos (header {@code correlation-id})
 * e os logs (campo MDC {@code correlationId}). Utilitário local do módulo
 * (ADR-002: shared-contracts só para tipos que cruzam fronteira).
 */
final class Correlacao {

    static final String MDC_CHAVE = "correlationId";
    static final String HEADER_HTTP = "X-Correlation-Id";
    static final String HEADER_MENSAGEM = "correlation-id";

    private Correlacao() {
    }

    /** Extrai o id de correlação da mensagem: metadado próprio ou header Kafka. */
    static Optional<String> deMensagem(Message<?> mensagem) {
        var propria = mensagem.getMetadata(CorrelacaoMetadata.class).map(CorrelacaoMetadata::id);
        if (propria.isPresent()) {
            return propria;
        }
        return mensagem.getMetadata(IncomingKafkaRecordMetadata.class)
                .map(m -> m.getHeaders().lastHeader(HEADER_MENSAGEM))
                .map(h -> new String(h.value(), StandardCharsets.UTF_8));
    }
}
