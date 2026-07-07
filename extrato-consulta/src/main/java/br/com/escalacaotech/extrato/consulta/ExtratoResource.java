package br.com.escalacaotech.extrato.consulta;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * API do extrato consolidado (US-06/US-07): visão do cliente na competência,
 * cache-first, com carimbo "atualizado às" e atualização sob demanda limitada.
 * <p>
 * {@code ?atualizar=true} ignora o cache: invalida a entrada e relê do dado
 * (US-07) — sujeito ao intervalo mínimo por cliente (429 quando excedido).
 */
@Path("/extrato")
public class ExtratoResource {

    @Inject
    ServicoExtrato servico;

    @Inject
    ControleAtualizacaoForcada controle;

    @GET
    @Path("/{idCliente}/{competencia}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response consultar(@PathParam("idCliente") String idCliente,
                              @PathParam("competencia") String competencia,
                              @QueryParam("atualizar") boolean atualizar) {
        final YearMonth mes;
        try {
            mes = YearMonth.parse(competencia);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "competência inválida — formato esperado AAAA-MM"))
                    .build();
        }
        var chave = mes.toString();

        if (atualizar) {
            if (!controle.permitido(idCliente)) {
                return Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .entity(Map.of(
                                "erro", "atualização sob demanda excede o limite",
                                "intervaloMinimoSegundos", controle.intervaloMinimoSegundos()))
                        .build();
            }
            servico.invalidar(idCliente, chave);
        }

        // TODO Inc-6 (US-12): trilha de acesso — quem consultou, qual cliente,
        //  quando e por qual canal (a identidade vem do canal — Sessão 6, decisão 7).
        return Response.ok(servico.buscar(idCliente, chave)).build();
    }
}
