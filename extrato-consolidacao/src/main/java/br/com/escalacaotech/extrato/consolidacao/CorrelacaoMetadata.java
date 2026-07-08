package br.com.escalacaotech.extrato.consolidacao;

/**
 * Metadado interno de correlação (US-12): a forma em-JVM do id que, no fio,
 * viaja como header Kafka ({@code correlation-id}) ou propriedade AMQP
 * ({@code correlation-id}). Publicadores anexam ambos; consumidores extraem
 * o que houver (ver {@link Correlacao#deMensagem}).
 */
public record CorrelacaoMetadata(String id) {
}
