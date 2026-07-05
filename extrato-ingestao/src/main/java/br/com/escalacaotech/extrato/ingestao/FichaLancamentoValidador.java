package br.com.escalacaotech.extrato.ingestao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

/**
 * Validação da ficha do lançamento (Sessão 2 / US-01).
 * <p>
 * Obrigatórios: idCliente, idLancamentoOrigem, instituicaoOrigem, agencia, conta,
 * tipo, valor, moeda, dataHoraOcorrencia, idConsentimento.
 * Opcionais (erratum #1 da Sessão 6): descricao, categoriaOrigem.
 * <p>
 * Mantida como validação manual (sem Bean Validation) para que a regra fique explícita
 * no domínio e o retorno indique exatamente "o campo que faltou", como pede a US-01.
 */
@ApplicationScoped
public class FichaLancamentoValidador {

    /** @return lista de campos obrigatórios ausentes/inválidos; vazia se a ficha é válida. */
    public List<String> camposFaltantes(LancamentoRecebido lancamento) {
        var faltantes = new ArrayList<String>();
        if (vazio(lancamento.idCliente())) faltantes.add("idCliente");
        if (vazio(lancamento.idLancamentoOrigem())) faltantes.add("idLancamentoOrigem");
        if (vazio(lancamento.instituicaoOrigem())) faltantes.add("instituicaoOrigem");
        if (vazio(lancamento.agencia())) faltantes.add("agencia");
        if (vazio(lancamento.conta())) faltantes.add("conta");
        if (lancamento.tipo() == null) faltantes.add("tipo");
        if (lancamento.valor() == null) faltantes.add("valor");
        if (vazio(lancamento.moeda())) faltantes.add("moeda");
        if (lancamento.dataHoraOcorrencia() == null) faltantes.add("dataHoraOcorrencia");
        if (vazio(lancamento.idConsentimento())) faltantes.add("idConsentimento");
        return faltantes;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
