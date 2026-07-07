package br.com.escalacaotech.extrato.consulta;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.jboss.logging.MDC;

/**
 * Propaga o {@code X-Correlation-Id} na chamada interna consulta→consolidação
 * (US-12/Inc-6): o cache miss do cliente aparece nos logs da consolidação com
 * o MESMO id do GET original.
 */
public class PropagadorCorrelacao implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext request) {
        var id = MDC.get(Correlacao.MDC_CHAVE);
        if (id != null) {
            request.getHeaders().putSingle(Correlacao.HEADER_HTTP, id);
        }
    }
}
