package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Cliente HTTP da API interna de posições da consolidação (ADR-006).
 * <p>
 * Este é o lado <b>consumer</b> do contract test consulta↔consolidação
 * (Sessão 6, decisão 2 — PACT no Inc-5). Competência trafega como "AAAA-MM".
 */
@RegisterRestClient(configKey = "consolidacao")
@Path("/interno/posicoes")
public interface ApiPosicoesConsolidacao {

    @GET
    List<PosicaoDaConta> buscar(@QueryParam("idCliente") String idCliente,
                                @QueryParam("competencia") String competencia);
}
