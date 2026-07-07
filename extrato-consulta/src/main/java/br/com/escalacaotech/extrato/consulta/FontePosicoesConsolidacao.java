package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementação de produção da {@link FontePosicoes}: busca na API interna da
 * consolidação (ADR-006).
 * <p>
 * {@code @Timeout} 2s <b>sem retry</b> (ADR-007): miss com a consolidação fora
 * vira erro rápido e claro — retry aqui só amplificaria a carga sobre um
 * serviço já degradado; os hits continuam servindo do cache.
 */
@ApplicationScoped
public class FontePosicoesConsolidacao implements FontePosicoes {

    @RestClient
    ApiPosicoesConsolidacao api;

    @Override
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    public List<PosicaoDaConta> posicoes(String idCliente, YearMonth competencia) {
        return api.buscar(idCliente, competencia.toString());
    }
}
