package br.com.escalacaotech.extrato.ingestao;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US-12/Inc-6 — correlação na borda de entrada: o id do POST volta no response
 * e segue na mensagem Kafka como header — é o começo da trilha "um fluxo
 * rastreável por um único id".
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class CorrelacaoIngestaoTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void limparCanal() {
        connector.sink("lancamentos-out").clear();
    }

    @Test
    void idDoRequestEcoaNoResponseESegueNaMensagem() {
        given().contentType("application/json")
                .header("X-Correlation-Id", "trace-abc-123")
                .body(corpoValido())
                .when().post("/lancamentos")
                .then()
                .statusCode(202)
                .header("X-Correlation-Id", equalTo("trace-abc-123"));

        var sink = connector.sink("lancamentos-out");
        await().until(() -> sink.received().size() == 1);

        var mensagem = sink.received().getFirst();

        var metadadoProprio = mensagem.getMetadata(CorrelacaoMetadata.class).orElseThrow();
        assertEquals("trace-abc-123", metadadoProprio.id());

        var kafka = mensagem.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow();
        var header = kafka.getHeaders().lastHeader("correlation-id");
        assertEquals("trace-abc-123", new String(header.value(), StandardCharsets.UTF_8),
                "o id atravessa o tópico como header Kafka");
    }

    @Test
    void semHeaderOServicoGeraUmIdEDevolve() {
        var resposta = given().contentType("application/json")
                .body(corpoValido())
                .when().post("/lancamentos")
                .then()
                .statusCode(202)
                .header("X-Correlation-Id", notNullValue())
                .extract();

        assertTrue(!resposta.header("X-Correlation-Id").isBlank(),
                "sem id do chamador, o serviço gera e devolve um");
    }

    private static Map<String, Object> corpoValido() {
        return Map.ofEntries(
                Map.entry("idCliente", "cliente-001"),
                Map.entry("idLancamentoOrigem", "TX-CORR-" + System.nanoTime()),
                Map.entry("instituicaoOrigem", "00360305"),
                Map.entry("agencia", "0001"),
                Map.entry("conta", "123456"),
                Map.entry("tipo", "DEBITO"),
                Map.entry("valor", "10.00"),
                Map.entry("moeda", "BRL"),
                Map.entry("dataHoraOcorrencia", "2026-07-07T18:00:00-03:00"),
                Map.entry("idConsentimento", "consent-abc"));
    }
}
