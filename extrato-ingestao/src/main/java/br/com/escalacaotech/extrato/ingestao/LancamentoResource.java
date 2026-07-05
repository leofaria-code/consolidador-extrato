package br.com.escalacaotech.extrato.ingestao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Porta de entrada dos lançamentos (simula os conectores das transmissoras e os
 * sistemas internos publicando na esteira — Sessão 2).
 * <p>
 * Aceite assíncrono: a resposta 202 confirma o aceite; a incorporação ao consolidado
 * acontece depois, via tópico (US-01). Ficha inválida -> 400 indicando os campos.
 */
@Path("/lancamentos")
public class LancamentoResource {

    @Inject
    FichaLancamentoValidador validador;

    @Inject
    PublicadorLancamentos publicador;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receber(LancamentoRecebido lancamento) {
        var faltantes = validador.camposFaltantes(lancamento);
        if (!faltantes.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "erro", "ficha do lançamento inválida",
                            "camposFaltantes", faltantes))
                    .build();
        }
        publicador.publicar(lancamento);
        return Response.accepted(Map.of(
                        "status", "ACEITO",
                        "identidade", lancamento.identidade(),
                        "aceitoEm", OffsetDateTime.now().toString()))
                .build();
    }

    @POST
    @Path("/lote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receberLote(List<LancamentoRecebido> lote) {
        int aceitos = 0;
        int rejeitados = 0;
        for (var lancamento : lote) {
            if (validador.camposFaltantes(lancamento).isEmpty()) {
                publicador.publicar(lancamento);
                aceitos++;
            } else {
                rejeitados++;
            }
        }
        return Response.accepted(Map.of("aceitos", aceitos, "rejeitados", rejeitados)).build();
    }
}
