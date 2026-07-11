package br.com.escalacaotech.extrato.consolidacao;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Troca os canais Kafka pelo connector in-memory nos testes.
 * Garante suite pura-JVM, sem Docker (perfil B — critério 6 da rubrica, ADR-003).
 */
public class RecursosEmMemoria implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        var propriedades = new HashMap<String, String>();
        propriedades.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(
                "lancamentos-in", "reconsolidacao-in"));
        propriedades.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(
                "posicao-atualizada-out", "reconsolidacao-out", "lancamentos-dlq-out"));
        return propriedades;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
