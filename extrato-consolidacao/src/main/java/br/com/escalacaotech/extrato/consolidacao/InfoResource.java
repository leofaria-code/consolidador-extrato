package br.com.escalacaotech.extrato.consolidacao;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Identificação do serviço/contexto — endpoint mínimo do esqueleto. */
@Path("/info")
public class InfoResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> info() {
        return Map.of(
                "servico", "extrato-consolidacao",
                "contexto", "Consolidação — posição conta x competência, resiliência, eventos (US-05, US-08..US-11)");
    }
}
