package br.com.escalacaotech.extrato.contratos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Posição consolidada de uma conta numa competência, como servida pela API
 * interna da consolidação à consulta (ADR-006 — resposta do cache miss).
 * <p>
 * Este é o contrato do par HTTP consulta↔consolidação — alvo do contract test
 * (Sessão 6, decisão 2; PACT no Inc-5). {@code atualizadoEm} é o carimbo
 * "atualizado às" <b>do dado</b> (Sessão 6, decisão 4), exibido pela consulta.
 */
public record PosicaoDaConta(
        String instituicaoOrigem,
        String agencia,
        String conta,
        YearMonth competencia,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldo,
        OffsetDateTime atualizadoEm) {
}
