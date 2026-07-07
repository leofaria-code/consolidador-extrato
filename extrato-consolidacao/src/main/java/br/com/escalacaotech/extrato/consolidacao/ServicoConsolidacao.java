package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

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

    public enum Resultado { INCORPORADO, REPETIDO }

    @Transactional
    public Resultado incorporar(LancamentoRecebido lancamento) {
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
        EventoPendente.de(posicao).persist();

        return Resultado.INCORPORADO;
    }
}
