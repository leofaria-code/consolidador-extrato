package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Outbox do evento {@code posicao-atualizada} (ADR-005): o registro entra na
 * <b>mesma transação</b> que grava o lançamento e atualiza a posição — queda em
 * qualquer ponto não perde evento, no máximo atrasa ou repete a publicação.
 * <p>
 * Carrega só <b>referência</b> (minimização — US-10): sem valor, sem descrição,
 * sem dado de lançamento. {@code publicadoEm} nulo = pendente; preenchido apenas
 * após o <b>ack do broker</b> (ver {@code PublicadorPosicaoAtualizada}).
 */
@Entity
@Table(name = "evento_pendente")
public class EventoPendente extends PanacheEntity {

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

    @Column(name = "atualizado_em", nullable = false)
    public OffsetDateTime atualizadoEm;

    @Column(name = "criado_em", nullable = false)
    public OffsetDateTime criadoEm;

    @Column(name = "publicado_em")
    public OffsetDateTime publicadoEm;

    /**
     * Correlação da requisição que originou a atualização (US-12/Inc-6): a
     * outbox preserva o id através da fronteira assíncrona — o publicador o
     * repõe como header do evento, e a invalidação na consulta loga o mesmo id
     * do POST original.
     */
    @Column(name = "correlacao_id")
    public String correlacaoId;

    public static EventoPendente de(PosicaoConsolidada posicao, String correlacaoId) {
        var evento = new EventoPendente();
        evento.idCliente = posicao.idCliente;
        evento.instituicaoOrigem = posicao.instituicaoOrigem;
        evento.agencia = posicao.agencia;
        evento.conta = posicao.conta;
        evento.competencia = posicao.competencia;
        evento.atualizadoEm = posicao.atualizadoEm;
        evento.criadoEm = OffsetDateTime.now();
        evento.correlacaoId = correlacaoId;
        return evento;
    }

    public PosicaoAtualizadaEvento comoEvento() {
        return new PosicaoAtualizadaEvento(
                idCliente, instituicaoOrigem, agencia, conta, competencia, atualizadoEm);
    }

    /** Pendentes em ordem de criação — preserva a ordem de publicação por conta. */
    public static List<EventoPendente> pendentes(int maximo) {
        return find("publicadoEm is null", Sort.by("criadoEm").and("id"))
                .page(0, maximo).list();
    }
}
