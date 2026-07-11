package br.com.escalacaotech.extrato.consolidacao;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US-08 na fronteira da deserialização (achado do plano A, 11/07): mensagem
 * ilegível não pode travar a partição nem sumir em silêncio. O handler
 * encaminha os BYTES CRUS à DLQ com a causa nos headers e devolve null
 * (o consumidor confirma e o fluxo segue). O caminho broker→handler é do
 * plano A (ADR-003); aqui prova-se o comportamento do handler em si.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class FalhaDeserializacaoTest {

    @Inject
    @Identifier("falha-deserializacao-lancamentos")
    FalhaDeserializacaoLancamentos handler;

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void mensagemIlegivelVaiParaDlqComCausaEBytesOriginais() {
        var lixo = "isto-nao-e-json{{{".getBytes(StandardCharsets.UTF_8);
        connector.sink("lancamentos-dlq-out").clear();

        var resultado = handler.handleDeserializationFailure(
                "lancamentos-recebidos", false, "jackson", lixo,
                new RuntimeException("JsonParseException simulada"), new RecordHeaders());

        assertNull(resultado, "null sinaliza payload nulo — o consumidor só confirma");

        var dlq = connector.sink("lancamentos-dlq-out");
        await().atMost(5, TimeUnit.SECONDS).until(() -> dlq.received().size() == 1);

        var mensagem = dlq.received().getFirst();
        assertArrayEquals(lixo, (byte[]) mensagem.getPayload(),
                "os bytes originais vão à DLQ — inspecionáveis, sem descarte silencioso");

        var headers = mensagem.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow().getHeaders();
        var causa = new String(headers.lastHeader("dead-letter-reason").value(),
                StandardCharsets.UTF_8);
        assertTrue(causa.contains("falha de deserializacao"),
                "a causa viaja nos headers (contrato da Sessão 4)");
    }
}
