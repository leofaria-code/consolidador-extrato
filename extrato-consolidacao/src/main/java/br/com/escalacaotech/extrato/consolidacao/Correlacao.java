package br.com.escalacaotech.extrato.consolidacao;

import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Correlação ponta a ponta (US-12): o mesmo id atravessa HTTP (header
 * {@code X-Correlation-Id}), tópicos Kafka (header {@code correlation-id}),
 * fila Rabbit (propriedade AMQP) e os logs (campo MDC {@code correlationId}).
 * <p>
 * Utilitário local do módulo — a ADR-002 restringe {@code shared-contracts}
 * a tipos que cruzam fronteira; no fio, correlação viaja como header, não
 * como tipo.
 */
final class Correlacao {

    static final String MDC_CHAVE = "correlationId";
    static final String HEADER_HTTP = "X-Correlation-Id";
    static final String HEADER_MENSAGEM = "correlation-id";

    private Correlacao() {
    }

    /** Extrai o id de correlação da mensagem: metadado próprio, header Kafka ou propriedade AMQP. */
    static Optional<String> deMensagem(Message<?> mensagem) {
        var propria = mensagem.getMetadata(CorrelacaoMetadata.class).map(CorrelacaoMetadata::id);
        if (propria.isPresent()) {
            return propria;
        }

        var kafka = mensagem.getMetadata(IncomingKafkaRecordMetadata.class)
                .map(m -> m.getHeaders().lastHeader(HEADER_MENSAGEM))
                .map(h -> new String(h.value(), StandardCharsets.UTF_8));
        if (kafka.isPresent()) {
            return kafka;
        }

        return mensagem.getMetadata(IncomingRabbitMQMetadata.class)
                .flatMap(IncomingRabbitMQMetadata::getCorrelationId);
    }
}
