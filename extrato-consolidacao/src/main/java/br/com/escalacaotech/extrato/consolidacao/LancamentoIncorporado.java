package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.IdentidadeLancamento;
import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Lançamento incorporado à base segregada da consolidação (Inc-2 / US-05).
 * <p>
 * Dupla função (ADR-004): além de sustentar a reapuração (reconsolidação — US-09),
 * a constraint UNIQUE em (instituicao_origem, id_lancamento_origem) é a
 * <b>memória de deduplicação que não expira</b> — última linha de defesa da
 * idempotência quando a verificação de existência sofre corrida.
 * <p>
 * A competência é derivada da data de <b>ocorrência</b> (US-01/US-03 — relógio da
 * origem), gravada desnormalizada para indexar a reapuração por conta × competência.
 */
@Entity
@Table(name = "lancamento_incorporado", uniqueConstraints = @UniqueConstraint(
        name = "uk_identidade_lancamento",
        columnNames = {"instituicao_origem", "id_lancamento_origem"}))
public class LancamentoIncorporado extends PanacheEntity {

    @Column(name = "id_cliente", nullable = false)
    public String idCliente;

    @Column(name = "id_lancamento_origem", nullable = false)
    public String idLancamentoOrigem;

    @Column(name = "instituicao_origem", nullable = false)
    public String instituicaoOrigem;

    @Column(nullable = false)
    public String agencia;

    @Column(nullable = false)
    public String conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TipoLancamento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    public BigDecimal valor;

    @Column(nullable = false)
    public String moeda;

    @Column(name = "data_hora_ocorrencia", nullable = false)
    public OffsetDateTime dataHoraOcorrencia;

    @Column(name = "id_consentimento", nullable = false)
    public String idConsentimento;

    public String descricao;

    @Column(name = "categoria_origem")
    public String categoriaOrigem;

    @Column(nullable = false)
    public YearMonth competencia;

    public static LancamentoIncorporado de(LancamentoRecebido lancamento) {
        var incorporado = new LancamentoIncorporado();
        incorporado.idCliente = lancamento.idCliente();
        incorporado.idLancamentoOrigem = lancamento.idLancamentoOrigem();
        incorporado.instituicaoOrigem = lancamento.instituicaoOrigem();
        incorporado.agencia = lancamento.agencia();
        incorporado.conta = lancamento.conta();
        incorporado.tipo = lancamento.tipo();
        incorporado.valor = lancamento.valor();
        incorporado.moeda = lancamento.moeda();
        incorporado.dataHoraOcorrencia = lancamento.dataHoraOcorrencia();
        incorporado.idConsentimento = lancamento.idConsentimento();
        incorporado.descricao = lancamento.descricao();
        incorporado.categoriaOrigem = lancamento.categoriaOrigem();
        incorporado.competencia = YearMonth.from(lancamento.dataHoraOcorrencia());
        return incorporado;
    }

    /** Verificação de dedup (ADR-004) — deve rodar na mesma transação da incorporação. */
    public static boolean jaIncorporado(IdentidadeLancamento identidade) {
        return count("instituicaoOrigem = ?1 and idLancamentoOrigem = ?2",
                identidade.instituicaoOrigem(), identidade.idLancamentoOrigem()) > 0;
    }
}
