package br.com.escalacaotech.extrato.ingestao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * US-01: lançamento válido -> 202 + publicado no tópico; ficha inválida -> 400
 * indicando o campo que faltou. Roda sem Docker (canal in-memory).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class LancamentoResourceTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @BeforeEach
    void limparCanal() {
        connector.sink("lancamentos-out").clear();
    }

    @Test
    void lancamentoValidoEhAceitoEPublicadoNoTopico() {
        var corpo = Map.ofEntries(
                Map.entry("idCliente", "cliente-001"),
                Map.entry("idLancamentoOrigem", "TX-0001"),
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
                .then()
                .statusCode(202)
                .body("status", equalTo("ACEITO"));

        var sink = connector.sink("lancamentos-out");
        await().until(() -> sink.received().size() == 1);

        var publicado = (LancamentoRecebido) sink.received().getFirst().getPayload();
        assertEquals("TX-0001", publicado.idLancamentoOrigem());
        assertEquals("00360305", publicado.instituicaoOrigem());
    }

    @Test
    void fichaInvalidaRetorna400IndicandoOCampoQueFaltou() {
        // sem "valor" e sem "idConsentimento"
        var corpo = Map.ofEntries(
                Map.entry("idCliente", "cliente-001"),
                Map.entry("idLancamentoOrigem", "TX-0002"),
                Map.entry("instituicaoOrigem", "00360305"),
                Map.entry("agencia", "0001"),
                Map.entry("conta", "123456"),
                Map.entry("tipo", "CREDITO"),
                Map.entry("moeda", "BRL"),
                Map.entry("dataHoraOcorrencia", "2026-07-05T12:47:00-03:00"));

        given().contentType("application/json").body(corpo)
                .when().post("/lancamentos")
                .then()
                .statusCode(400)
                .body("camposFaltantes", hasItem("valor"))
                .body("camposFaltantes", hasItem("idConsentimento"));

        assertEquals(0, connector.sink("lancamentos-out").received().size(),
                "ficha inválida não pode chegar ao tópico");
    }
}
