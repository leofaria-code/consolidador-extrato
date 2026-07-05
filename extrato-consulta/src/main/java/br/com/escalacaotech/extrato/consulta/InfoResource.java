package br.com.escalacaotech.extrato.consulta;

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
                "servico", "extrato-consulta",
                "contexto", "Consulta — extrato consolidado com cache, carimbo e auditoria (US-06, US-07, US-12)");
    }
}
