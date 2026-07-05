package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.IdentidadeLancamento;

/**
 * Guarda de idempotência do consumidor (US-02): decide se um lançamento já foi processado.
 * <p>
 * Contrato: {@link #primeiraVez} retorna {@code true} exatamente uma vez por identidade —
 * chamadas subsequentes com a mesma identidade retornam {@code false} (repetido é ignorado
 * sem erro, Sessão 2 decisão 3).
 * <p>
 * Decisão em aberto (Sessão 6, ADR candidato #3): unicidade na base × janela de deduplicação.
 */
public interface GuardaIdempotencia {

    boolean primeiraVez(IdentidadeLancamento identidade);
}
