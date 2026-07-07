package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de reconsolidação (US-09): atendimento/operação pede o recálculo
 * de uma conta × competência e recebe <b>aceite imediato</b> — o pedido entra
 * na fila de trabalho e é executado um a um (o "guichê" da Sessão 4), sem
 * impactar a consulta em produção.
 */
@Path("/reconsolidacoes")
public class ReconsolidacaoResource {

    /** Corpo da solicitação — competência como texto "AAAA-MM". */
    public record Solicitacao(String idCliente, String instituicaoOrigem, String agencia,
                              String conta, String competencia, String motivo) {
    }

    @Channel("reconsolidacao-out")
    Emitter<PedidoReconsolidacao> fila;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response solicitar(Solicitacao solicitacao) {
        var faltantes = new ArrayList<String>();
        if (vazio(solicitacao.idCliente())) faltantes.add("idCliente");
        if (vazio(solicitacao.instituicaoOrigem())) faltantes.add("instituicaoOrigem");
        if (vazio(solicitacao.agencia())) faltantes.add("agencia");
        if (vazio(solicitacao.conta())) faltantes.add("conta");
        if (vazio(solicitacao.competencia())) faltantes.add("competencia");
        if (vazio(solicitacao.motivo())) faltantes.add("motivo");
        if (!faltantes.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "solicitação inválida", "camposFaltantes", faltantes))
                    .build();
        }

        final YearMonth competencia;
        try {
            competencia = YearMonth.parse(solicitacao.competencia());
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("erro", "competência inválida — formato esperado AAAA-MM"))
                    .build();
        }

        var pedido = new PedidoReconsolidacao(
                UUID.randomUUID().toString(),
                solicitacao.idCliente(),
                solicitacao.instituicaoOrigem(),
                solicitacao.agencia(),
                solicitacao.conta(),
                competencia,
                solicitacao.motivo(),
                OffsetDateTime.now());

        fila.send(pedido);

        // aceite imediato: o processamento é do guichê, não deste request
        return Response.accepted(Map.of("idPedido", pedido.idPedido(), "status", "ACEITO"))
                .build();
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
