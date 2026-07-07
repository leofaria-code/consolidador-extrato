package br.com.escalacaotech.extrato.consulta;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

import java.util.Map;

/**
 * Troca o canal Kafka do evento posicao-atualizada pelo connector in-memory.
 * Garante suite pura-JVM, sem Docker (perfil B — critério 6, ADR-003).
 */
public class RecursosEmMemoria implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        return InMemoryConnector.switchIncomingChannelsToInMemory("posicao-atualizada-in");
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}
