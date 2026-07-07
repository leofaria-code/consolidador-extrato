package br.com.escalacaotech.extrato.ingestao;

/**
 * Constantes de correlação (US-12/Inc-6). Utilitário local do módulo — a
 * ADR-002 restringe shared-contracts a tipos que cruzam fronteira; no fio, a
 * correlação viaja como header HTTP/Kafka, não como tipo.
 */
final class Correlacao {

    static final String MDC_CHAVE = "correlationId";
    static final String HEADER_HTTP = "X-Correlation-Id";
    static final String HEADER_MENSAGEM = "correlation-id";

    private Correlacao() {
    }
}
