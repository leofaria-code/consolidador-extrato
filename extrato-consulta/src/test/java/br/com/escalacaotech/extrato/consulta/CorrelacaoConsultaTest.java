package br.com.escalacaotech.extrato.consulta;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * US-12/Inc-6 — correlação na borda de saída (o canal do app): o id do GET
 * volta no response; a propagação para a consolidação em cache miss é feita
 * pelo PropagadorCorrelacao (header na chamada interna).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class CorrelacaoConsultaTest {

    @Test
    void idDoRequestEcoaNoResponse() {
        given().header("X-Correlation-Id", "trace-consulta-9")
                .get("/extrato/cli-corr/2026-07")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", equalTo("trace-consulta-9"));
    }

    @Test
    void semHeaderOServicoGeraUmId() {
        given().get("/extrato/cli-corr-2/2026-07")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", notNullValue());
    }
}
