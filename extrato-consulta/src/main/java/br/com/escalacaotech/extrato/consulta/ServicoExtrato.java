package br.com.escalacaotech.extrato.consulta;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.YearMonth;

/**
 * Serviço de extrato com cache-first (US-06, ADR-006).
 * <p>
 * Hit: responde da memória. Miss: {@link FontePosicoes} (API interna da
 * consolidação), popula o cache e responde. Invalidação: por evento
 * {@code posicao-atualizada} ({@link ConsumidorPosicaoAtualizada}) e por
 * atualização sob demanda (US-07) — o TTL configurado é a salvaguarda.
 * <p>
 * A chave do cache é o par (idCliente, competencia) — os dois parâmetros dos
 * métodos anotados; invalidar e buscar precisam usar exatamente a mesma forma.
 */
@ApplicationScoped
public class ServicoExtrato {

    public static final String CACHE = "extrato-consolidado";

    @Inject
    FontePosicoes fonte;

    @CacheResult(cacheName = CACHE)
    public ExtratoConsolidado buscar(String idCliente, String competencia) {
        var posicoes = fonte.posicoes(idCliente, YearMonth.parse(competencia));
        return ExtratoConsolidado.de(idCliente, competencia, posicoes);
    }

    @CacheInvalidate(cacheName = CACHE)
    public void invalidar(String idCliente, String competencia) {
        // corpo vazio: a anotação remove a entrada (idCliente, competencia)
    }
}
