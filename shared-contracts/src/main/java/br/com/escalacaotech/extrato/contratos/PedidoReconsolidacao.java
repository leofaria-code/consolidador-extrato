package br.com.escalacaotech.extrato.contratos;

import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Pedido de reconsolidação (US-09): recálculo da posição de uma conta numa competência.
 * <p>
 * Trafega em <b>fila de trabalho</b> (producer/consumer): cada pedido é consumido por um
 * único trabalhador, um a um — o "guichê" da Sessão 4. {@code motivo} preserva a origem
 * (contestação, reprocessamento pós-DLQ, correção) para a trilha de auditoria.
 */
public record PedidoReconsolidacao(
        String idPedido,
        String idCliente,
        String instituicaoOrigem,
        String agencia,
        String conta,
        YearMonth competencia,
        String motivo,
        OffsetDateTime solicitadoEm) {
}
