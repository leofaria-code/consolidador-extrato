package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.IdentidadeLancamento;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação PROVISÓRIA em memória (até o Incremento 2, quando a base segregada
 * assume via unicidade de (instituicaoOrigem, idLancamentoOrigem) — ver ADR candidato #3).
 * <p>
 * {@code Set#add} é atômico: retorna {@code true} só para a primeira inserção,
 * o que dá a semântica exata de {@link GuardaIdempotencia#primeiraVez}.
 */
@ApplicationScoped
public class GuardaIdempotenciaEmMemoria implements GuardaIdempotencia {

    private final Set<IdentidadeLancamento> processados = ConcurrentHashMap.newKeySet();

    @Override
    public boolean primeiraVez(IdentidadeLancamento identidade) {
        return processados.add(identidade);
    }
}
