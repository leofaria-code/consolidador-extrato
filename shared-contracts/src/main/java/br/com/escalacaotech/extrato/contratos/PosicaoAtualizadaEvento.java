package br.com.escalacaotech.extrato.contratos;

import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Evento "posição consolidada atualizada" (US-10), publicado em tópico a cada atualização.
 * <p>
 * Carrega <b>referência</b>, nunca dados de lançamentos (minimização — Sessões 4 e 5).
 * Entrega "pelo menos uma vez": pode atrasar e pode repetir; consumidores devem ser
 * idempotentes (premissa aceita na Sessão 6, decisão 3).
 */
public record PosicaoAtualizadaEvento(
        String idCliente,
        String instituicaoOrigem,
        String agencia,
        String conta,
        YearMonth competencia,
        OffsetDateTime atualizadoEm) {
}
