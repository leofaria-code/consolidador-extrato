package br.com.escalacaotech.extrato.consolidacao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.YearMonth;

/**
 * Acesso transacional à base segregada a partir dos testes (as entidades Panache
 * exigem transação/sessão ativa; o thread do teste não tem uma por padrão).
 */
@ApplicationScoped
public class ApoioBaseDeTeste {

    @Transactional
    public void limparBase() {
        EventoPendente.deleteAll();
        PosicaoConsolidada.deleteAll();
        LancamentoIncorporado.deleteAll();
    }

    @Transactional
    public long totalLancamentos() {
        return LancamentoIncorporado.count();
    }

    @Transactional
    public long totalPosicoes() {
        return PosicaoConsolidada.count();
    }

    @Transactional
    public long eventosPendentes() {
        return EventoPendente.count("publicadoEm is null");
    }

    @Transactional
    public long eventosRegistrados() {
        return EventoPendente.count();
    }

    @Transactional
    public PosicaoConsolidada posicao(String instituicao, String agencia, String conta, YearMonth competencia) {
        return PosicaoConsolidada.find(
                "instituicaoOrigem = ?1 and agencia = ?2 and conta = ?3 and competencia = ?4",
                instituicao, agencia, conta, competencia).firstResult();
    }

    /** Simula divergência (contestação da US-09): corrompe o saldo direto na base. */
    @Transactional
    public void corromperSaldo(String instituicao, String agencia, String conta,
                               YearMonth competencia, String saldoErrado) {
        PosicaoConsolidada p = PosicaoConsolidada.find(
                "instituicaoOrigem = ?1 and agencia = ?2 and conta = ?3 and competencia = ?4",
                instituicao, agencia, conta, competencia).firstResult();
        p.saldo = new java.math.BigDecimal(saldoErrado);
    }
}
