package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Armazenamento PROVISÓRIO dos lançamentos incorporados (até o Incremento 2,
 * quando a base segregada de posições assume — US-05).
 * <p>
 * Existe para dar semântica observável ao consumidor ("incorporou de fato") e
 * sustentar o teste de idempotência do Incremento 1.
 */
@ApplicationScoped
public class LancamentosProcessados {

    private final List<LancamentoRecebido> incorporados = new CopyOnWriteArrayList<>();

    public void incorporar(LancamentoRecebido lancamento) {
        incorporados.add(lancamento);
    }

    public int total() {
        return incorporados.size();
    }

    public List<LancamentoRecebido> todos() {
        return List.copyOf(incorporados);
    }

    public void limpar() {
        incorporados.clear();
    }
}
