package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * API interna de posições — a "porta da frente" da base segregada (ADR-002):
 * é por aqui que a consulta busca em cache miss (ADR-006).
 * <p>
 * Este endpoint é o <b>provider</b> do contract test consulta↔consolidação
 * (Sessão 6, decisão 2 — PACT no Inc-5). Mudanças aqui quebram o pact em disco,
 * não a consulta em produção.
 * <p>
 * Competência trafega como texto "AAAA-MM" na URL (mesma forma da coluna).
 */
@Path("/interno/posicoes")
public class PosicoesInternoResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<PosicaoDaConta> porClienteECompetencia(@QueryParam("idCliente") String idCliente,
                                                       @QueryParam("competencia") String competencia) {
        if (idCliente == null || idCliente.isBlank() || competencia == null || competencia.isBlank()) {
            throw new WebApplicationException("idCliente e competencia são obrigatórios",
                    Response.Status.BAD_REQUEST);
        }
        final YearMonth mes;
        try {
            mes = YearMonth.parse(competencia);
        } catch (DateTimeParseException e) {
            throw new WebApplicationException("competencia inválida — formato esperado AAAA-MM",
                    Response.Status.BAD_REQUEST);
        }

        return PosicaoConsolidada.<PosicaoConsolidada>list(
                        "idCliente = ?1 and competencia = ?2", idCliente, mes)
                .stream()
                .map(p -> new PosicaoDaConta(p.instituicaoOrigem, p.agencia, p.conta,
                        p.competencia, p.entradas, p.saidas, p.saldo, p.atualizadoEm))
                .toList();
    }
}
