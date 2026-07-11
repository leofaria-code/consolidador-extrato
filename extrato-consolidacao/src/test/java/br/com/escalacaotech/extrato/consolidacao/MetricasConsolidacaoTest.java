package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

/**
 * ADR-008 — a idempotência (ADR-004) vira métrica: o MESMO lançamento enviado
 * duas vezes produz as séries `incorporado` E `repetido` em /q/metrics. É a
 * prova quantitativa do critério 3, sem olhar log. Asserções de presença
 * (counters acumulam entre testes). Roda sem Docker (perfil B, critério 6).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class MetricasConsolidacaoTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void repetidoEIncorporadoViramSeriesDistintas() {
        var origem = connector.source("lancamentos-in");
        var lancamento = lancamento("TX-MET-100");

        origem.send(lancamento);
        origem.send(lancamento); // reenvio: dedup na base -> resultado=repetido

        // consumo é assíncrono: espera as DUAS séries aparecerem no endpoint
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                given().get("/q/metrics").then()
                        .statusCode(200)
                        .body(containsString("extrato_consolidacao_lancamentos_total"))
                        .body(containsString("resultado=\"incorporado\""))
                        .body(containsString("resultado=\"repetido\"")));
    }

    private static LancamentoRecebido lancamento(String idOrigem) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                "00360305",
                "0001",
                "123456",
                TipoLancamento.DEBITO,
                new BigDecimal("250.00"),
                "BRL",
                OffsetDateTime.parse("2026-07-05T12:47:00-03:00"),
                "consent-abc",
                "COMPRA CARTAO MERCADO X",
                null);
    }
}
