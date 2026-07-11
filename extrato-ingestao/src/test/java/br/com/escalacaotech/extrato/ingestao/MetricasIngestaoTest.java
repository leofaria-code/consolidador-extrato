package br.com.escalacaotech.extrato.ingestao;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * ADR-008 — métricas de negócio da borda de entrada expostas em /q/metrics
 * (formato Prometheus). Asserções de PRESENÇA, nunca de valor exato: counters
 * acumulam entre os testes da mesma JVM. Roda sem Docker (perfil B, critério 6):
 * o registry é em memória e o scrape é pull — nenhum broker envolvido.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class MetricasIngestaoTest {

    @Test
    void lancamentoAceitoApareceNosContadoresDeNegocio() {
        var corpo = Map.ofEntries(
                Map.entry("idCliente", "cliente-001"),
                Map.entry("idLancamentoOrigem", "TX-MET-001"),
                Map.entry("instituicaoOrigem", "00360305"),
                Map.entry("agencia", "0001"),
                Map.entry("conta", "123456"),
                Map.entry("tipo", "DEBITO"),
                Map.entry("valor", "250.00"),
                Map.entry("moeda", "BRL"),
                Map.entry("dataHoraOcorrencia", "2026-07-05T12:47:00-03:00"),
                Map.entry("idConsentimento", "consent-abc"),
                Map.entry("descricao", "COMPRA CARTAO MERCADO X"));

        given().contentType("application/json").body(corpo)
                .when().post("/lancamentos")
                .then().statusCode(202);

        // ficha inválida (sem valor/consentimento) alimenta a série `rejeitado`
        given().contentType("application/json")
                .body(Map.of("idCliente", "cliente-001", "idLancamentoOrigem", "TX-MET-002"))
                .when().post("/lancamentos")
                .then().statusCode(400);

        given().get("/q/metrics").then()
                .statusCode(200)
                .body(containsString("extrato_ingestao_lancamentos_total"))
                .body(containsString("resultado=\"aceito\""))
                .body(containsString("resultado=\"rejeitado\""))
                .body(containsString("extrato_ingestao_lancamentos_publicados_total"));
    }
}
