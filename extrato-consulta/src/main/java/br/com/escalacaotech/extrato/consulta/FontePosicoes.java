package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;

import java.time.YearMonth;
import java.util.List;

/**
 * Porta da consulta para a origem das posições em cache miss (ADR-006).
 * <p>
 * Em produção, a implementação chama a API interna da consolidação (a "porta
 * da frente" da base segregada — ADR-002). Nos testes, um dublê em memória
 * permite provar hit/miss/invalidação sem HTTP nem Docker (ADR-003).
 */
public interface FontePosicoes {

    List<PosicaoDaConta> posicoes(String idCliente, YearMonth competencia);
}
