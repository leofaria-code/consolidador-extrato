package br.com.escalacaotech.extrato.contratos;

/**
 * Identidade única de um lançamento: (instituição de origem + id na origem).
 * É a chave de idempotência do consumidor de ingestão (US-02, Sessão 2).
 */
public record IdentidadeLancamento(String instituicaoOrigem, String idLancamentoOrigem) {
}
