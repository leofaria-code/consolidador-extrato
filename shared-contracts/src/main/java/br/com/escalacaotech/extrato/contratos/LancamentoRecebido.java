package br.com.escalacaotech.extrato.contratos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Mensagem publicada no tópico de ingestão (contrato de mensagem — US-01).
 * <p>
 * Campos obrigatórios conforme a ficha do lançamento (Sessão 2). {@code descricao} e
 * {@code categoriaOrigem} são opcionais (erratum #1 da Sessão 6: descrição é opcional;
 * a exibição usa um texto genérico quando ausente).
 * <p>
 * A identidade do lançamento — chave de idempotência (US-02) — é o par
 * ({@code instituicaoOrigem}, {@code idLancamentoOrigem}); ver {@link IdentidadeLancamento}.
 */
public record LancamentoRecebido(
        String idCliente,
        String idLancamentoOrigem,
        String instituicaoOrigem,
        String agencia,
        String conta,
        TipoLancamento tipo,
        BigDecimal valor,
        String moeda,
        OffsetDateTime dataHoraOcorrencia,
        String idConsentimento,
        String descricao,
        String categoriaOrigem) {

    /** Identidade única do lançamento no mundo (Sessão 2). */
    public IdentidadeLancamento identidade() {
        return new IdentidadeLancamento(instituicaoOrigem, idLancamentoOrigem);
    }
}
