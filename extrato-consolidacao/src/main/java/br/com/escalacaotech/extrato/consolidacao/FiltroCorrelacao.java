package br.com.escalacaotech.extrato.consolidacao;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

/**
 * Correlação nas bordas HTTP (US-12/Inc-6): aceita o {@code X-Correlation-Id}
 * do chamador (ou gera um), põe no MDC — todo log do request carrega o id —
 * e o devolve no response para o chamador seguir a trilha.
 */
@Provider
public class FiltroCorrelacao implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        var id = request.getHeaderString(Correlacao.HEADER_HTTP);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(Correlacao.MDC_CHAVE, id);
        request.setProperty(Correlacao.MDC_CHAVE, id);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var id = (String) request.getProperty(Correlacao.MDC_CHAVE);
        if (id != null) {
            response.getHeaders().putSingle(Correlacao.HEADER_HTTP, id);
        }
        MDC.remove(Correlacao.MDC_CHAVE);
    }
}
