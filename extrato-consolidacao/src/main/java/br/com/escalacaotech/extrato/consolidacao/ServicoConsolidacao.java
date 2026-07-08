package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Núcleo transacional da consolidação (Inc-2 / US-05, ADR-005).
 * <p>
 * Os três efeitos — gravar lançamento, atualizar posição, registrar evento na
 * outbox — acontecem numa <b>única transação local</b>. A verificação de dedup
 * (ADR-004) participa da mesma transação: não existe janela em que o lançamento
 * esteja gravado mas ainda "não visto" pela guarda.
 * <p>
 * Análise de queda ponto a ponto: ver ADR-005 (tabela "Análise de queda").
 */
@ApplicationScoped
public class ServicoConsolidacao {

    private static final Logger LOG = Logger.getLogger(ServicoConsolidacao.class);

    public enum Resultado { INCORPORADO, REPETIDO }

    /**
     * @param correlacaoId id de correlação do fluxo (US-12) — carregado
     *        <b>explicitamente</b> pela cadeia de chamadas: o MDC não é
     *        confiável nas threads de mensageria (ver registro de 07/07 no
     *        uso-de-ia.md); a outbox o preserva para o evento.
     */
    @Transactional
    public Resultado incorporar(LancamentoRecebido lancamento, String correlacaoId) {
        // Dedup pela identidade (ADR-004); a constraint UNIQUE de
        // lancamento_incorporado é a última linha de defesa contra corrida.
        if (LancamentoIncorporado.jaIncorporado(lancamento.identidade())) {
            return Resultado.REPETIDO;
        }

        // Efeito 1: gravar o lançamento (que é também a memória de dedup).
        var incorporado = LancamentoIncorporado.de(lancamento);
        incorporado.persist();

        // Efeito 2: atualizar a posição da conta na competência da OCORRÊNCIA —
        // competência antiga é reaberta pelo mesmo caminho (US-03).
        var posicao = PosicaoConsolidada.buscarOuCriar(incorporado);
        posicao.aplicar(incorporado);

        // Efeito 3: registrar o evento na outbox — a publicação em si é
        // assíncrona (PublicadorPosicaoAtualizada), "pelo menos uma vez".
        EventoPendente.de(posicao, correlacaoId).persist();

        return Resultado.INCORPORADO;
    }

    /**
     * Reconsolidação sob demanda (US-09): reapura os lançamentos da conta na
     * competência, refaz os totais do zero e dispara a invalidação do cache
     * (evento via outbox — ADR-005). Recálculo absoluto ⇒ <b>idempotente por
     * natureza</b>: reprocessar o mesmo pedido produz a mesma posição.
     */
    @Transactional
    public void reconsolidar(PedidoReconsolidacao pedido, String correlacaoId) {
        List<LancamentoIncorporado> lancamentos = LancamentoIncorporado.list(
                "instituicaoOrigem = ?1 and agencia = ?2 and conta = ?3 and competencia = ?4",
                pedido.instituicaoOrigem(), pedido.agencia(), pedido.conta(), pedido.competencia());

        var posicao = PosicaoConsolidada.<PosicaoConsolidada>find(
                "instituicaoOrigem = ?1 and agencia = ?2 and conta = ?3 and competencia = ?4",
                pedido.instituicaoOrigem(), pedido.agencia(), pedido.conta(),
                pedido.competencia()).firstResult();

        if (posicao == null && lancamentos.isEmpty()) {
            LOG.infof("Reconsolidação %s: nada a reapurar (sem posição e sem lançamentos)",
                    pedido.idPedido());
            return;
        }
        if (posicao == null) {
            var primeiro = lancamentos.getFirst();
            posicao = PosicaoConsolidada.buscarOuCriar(primeiro);
        }

        // reapuração do zero: a verdade são os lançamentos incorporados
        posicao.entradas = BigDecimal.ZERO;
        posicao.saidas = BigDecimal.ZERO;
        posicao.saldo = BigDecimal.ZERO;
        for (var lancamento : lancamentos) {
            posicao.aplicar(lancamento);
        }
        posicao.atualizadoEm = OffsetDateTime.now();

        EventoPendente.de(posicao, correlacaoId).persist();

        LOG.infof("Reconsolidação %s concluída: %d lançamento(s) reapurado(s) (motivo: %s) [corr=%s]",
                pedido.idPedido(), lancamentos.size(), pedido.motivo(), correlacaoId);
    }
}
