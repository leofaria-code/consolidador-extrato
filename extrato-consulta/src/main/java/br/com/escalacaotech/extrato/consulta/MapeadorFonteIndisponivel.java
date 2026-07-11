package br.com.escalacaotech.extrato.consulta;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Degradação com transparência (US-05): miss com a consolidação fora e sem
 * última resposta boa vira 503 explícito com Retry-After — nunca um 500 opaco.
 */
@Provider
public class MapeadorFonteIndisponivel implements ExceptionMapper<FonteIndisponivelException> {

    static final int TENTE_NOVAMENTE_SEGUNDOS = 30;

    @Override
    public Response toResponse(FonteIndisponivelException excecao) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header("Retry-After", TENTE_NOVAMENTE_SEGUNDOS)
                .entity(Map.of(
                        "erro", "extrato temporariamente indisponível — fonte de posições fora e sem cópia recente",
                        "tenteNovamenteEmSegundos", TENTE_NOVAMENTE_SEGUNDOS))
                .build();
    }
}
