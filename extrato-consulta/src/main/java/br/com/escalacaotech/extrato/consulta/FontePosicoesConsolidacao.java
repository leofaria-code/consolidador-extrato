package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.YearMonth;
import java.util.List;

/**
 * Implementação de produção da {@link FontePosicoes}: busca na API interna da
 * consolidação (ADR-006). A política de resiliência (timeout, disjuntor e
 * fallback de última resposta boa) vive na {@link FonteResiliente}, que
 * envolve esta porta — assim os testes exercitam o disjuntor com o dublê.
 */
@ApplicationScoped
public class FontePosicoesConsolidacao implements FontePosicoes {

    @RestClient
    ApiPosicoesConsolidacao api;

    @Override
    public List<PosicaoDaConta> posicoes(String idCliente, YearMonth competencia) {
        return api.buscar(idCliente, competencia.toString());
    }
}
