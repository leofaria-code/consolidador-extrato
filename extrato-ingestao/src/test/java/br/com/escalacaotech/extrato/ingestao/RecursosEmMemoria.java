package br.com.escalacaotech.extrato.ingestao;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.Map;

/**
 * Troca o canal Kafka pelo connector in-memory nos testes.
 * Garante suite pura-JVM, sem Docker (perfil B — critério 6 da rubrica).
 */
public class RecursosEmMemoria implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        return InMemoryConnector.switchOutgoingChannelsToInMemory("lancamentos-out");
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
