package br.com.escalacaotech.extrato.ingestao;

/**
 * Metadado interno de correlação (US-12): forma em-JVM do id que, no fio,
 * viaja como header Kafka {@code correlation-id}.
 */
public record CorrelacaoMetadata(String id) {
}
