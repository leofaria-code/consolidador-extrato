package br.com.escalacaotech.extrato.consulta;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de frequência do "atualizar sob demanda" por cliente (US-07 /
 * Sessão 6, decisão 5): um cliente ansioso apertando dez vezes seguidas não
 * vira dez leituras caras. Intervalo mínimo configurável
 * ({@code consulta.atualizar-sob-demanda.intervalo-minimo}).
 */
@ApplicationScoped
public class ControleAtualizacaoForcada {

    @ConfigProperty(name = "consulta.atualizar-sob-demanda.intervalo-minimo")
    Duration intervaloMinimo;

    private final ConcurrentHashMap<String, Instant> ultimaPorCliente = new ConcurrentHashMap<>();

    /** @return {@code true} se a atualização forçada é permitida agora (e registra o uso). */
    public boolean permitido(String idCliente) {
        var agora = Instant.now();
        var permitido = new boolean[1];
        ultimaPorCliente.compute(idCliente, (chave, ultima) -> {
            if (ultima == null || Duration.between(ultima, agora).compareTo(intervaloMinimo) >= 0) {
                permitido[0] = true;
                return agora;
            }
            permitido[0] = false;
            return ultima;
        });
        return permitido[0];
    }

    public long intervaloMinimoSegundos() {
        return intervaloMinimo.toSeconds();
    }
}
