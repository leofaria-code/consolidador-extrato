package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Resposta da consulta: visão do cliente na competência — todas as contas,
 * totais e o carimbo "atualizado às" (US-06/US-07).
 * <p>
 * {@code atualizadoEm} é o carimbo <b>do dado</b> mais recente entre as posições
 * (Sessão 6, decisão 4) — nulo em extrato vazio, que é resposta bem definida,
 * não erro (US-06, critério 3).
 */
public record ExtratoConsolidado(
        String idCliente,
        String competencia,
        List<PosicaoDaConta> posicoes,
        Totais totais,
        OffsetDateTime atualizadoEm) {

    public record Totais(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {
    }

    public static ExtratoConsolidado de(String idCliente, String competencia,
                                        List<PosicaoDaConta> posicoes) {
        var entradas = soma(posicoes, PosicaoDaConta::entradas);
        var saidas = soma(posicoes, PosicaoDaConta::saidas);
        var carimbo = posicoes.stream()
                .map(PosicaoDaConta::atualizadoEm)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ExtratoConsolidado(idCliente, competencia, posicoes,
                new Totais(entradas, saidas, entradas.subtract(saidas)), carimbo);
    }

    private static BigDecimal soma(List<PosicaoDaConta> posicoes,
                                   java.util.function.Function<PosicaoDaConta, BigDecimal> campo) {
        return posicoes.stream().map(campo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
