package br.com.escalacaotech.extrato.ingestao;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** Smoke test do esqueleto — roda em pura-JVM (perfil plano-b-jvm, sem Docker). */
@QuarkusTest
class InfoResourceTest {

    @Test
    void infoRespondeComNomeDoServico() {
        given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .body("servico", equalTo("extrato-ingestao"));
    }
}
