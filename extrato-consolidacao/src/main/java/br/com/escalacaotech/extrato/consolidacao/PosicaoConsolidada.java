package br.com.escalacaotech.extrato.consolidacao;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Posição consolidada de uma conta numa competência (US-05): totais mantidos
 * <b>prontos</b> na chegada de cada lançamento, para que a consulta apenas leia.
 * <p>
 * {@code atualizadoEm} é o carimbo "atualizado às" exibido ao cliente — hora da
 * última atualização <b>do dado</b>, não do cache (Sessão 6, decisão 4).
 * <p>
 * Lançamento de competência anterior <b>reabre</b> a posição daquela competência
 * (US-03): o upsert por (conta × competência) não distingue mês corrente de antigo.
 */
@Entity
@Table(name = "posicao_consolidada", uniqueConstraints = @UniqueConstraint(
        name = "uk_posicao_conta_competencia",
        columnNames = {"instituicao_origem", "agencia", "conta", "competencia"}))
public class PosicaoConsolidada extends PanacheEntity {

    @Column(name = "id_cliente", nullable = false)
    public String idCliente;

    @Column(name = "instituicao_origem", nullable = false)
    public String instituicaoOrigem;

    @Column(nullable = false)
    public String agencia;

    @Column(nullable = false)
    public String conta;

    @Column(nullable = false)
    public YearMonth competencia;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal entradas = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal saidas = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal saldo = BigDecimal.ZERO;

    @Column(name = "atualizado_em", nullable = false)
    public OffsetDateTime atualizadoEm;

    /** Incorpora um lançamento aos totais e renova o carimbo do dado. */
    public void aplicar(LancamentoIncorporado lancamento) {
        switch (lancamento.tipo) {
            case CREDITO -> entradas = entradas.add(lancamento.valor);
            case DEBITO -> saidas = saidas.add(lancamento.valor);
        }
        saldo = entradas.subtract(saidas);
        atualizadoEm = OffsetDateTime.now();
    }

    /** Busca a posição da conta na competência ou cria uma zerada (upsert do US-03/US-05). */
    public static PosicaoConsolidada buscarOuCriar(LancamentoIncorporado lancamento) {
        PosicaoConsolidada posicao = find(
                "instituicaoOrigem = ?1 and agencia = ?2 and conta = ?3 and competencia = ?4",
                lancamento.instituicaoOrigem, lancamento.agencia, lancamento.conta,
                lancamento.competencia).firstResult();
        if (posicao == null) {
            posicao = new PosicaoConsolidada();
            posicao.idCliente = lancamento.idCliente;
            posicao.instituicaoOrigem = lancamento.instituicaoOrigem;
            posicao.agencia = lancamento.agencia;
            posicao.conta = lancamento.conta;
            posicao.competencia = lancamento.competencia;
            posicao.atualizadoEm = OffsetDateTime.now();
            posicao.persist();
        }
        return posicao;
    }
}
