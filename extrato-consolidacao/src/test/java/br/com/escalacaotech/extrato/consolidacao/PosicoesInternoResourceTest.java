package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * API interna de posições (ADR-006) — lado provider do futuro contract test.
 * Semeia a base pelo caminho real (ServicoConsolidacao) e consulta via HTTP.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class PosicoesInternoResourceTest {

    @Inject
    ServicoConsolidacao servico;

    @Inject
    ApoioBaseDeTeste base;

    @BeforeEach
    void limpar() {
        base.limparBase();
    }

    @Test
    void servePosicoesDoClienteNaCompetencia() {
        servico.incorporar(lancamento("TX-400", TipoLancamento.CREDITO, "300.00"), "teste");
        servico.incorporar(lancamento("TX-401", TipoLancamento.DEBITO, "120.00"), "teste");

        given().queryParam("idCliente", "cliente-001")
                .queryParam("competencia", "2026-07")
                .when().get("/interno/posicoes")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].conta", equalTo("123456"))
                .body("[0].competencia", equalTo("2026-07"))
                .body("[0].entradas", equalTo(300.00f))
                .body("[0].saidas", equalTo(120.00f))
                .body("[0].saldo", equalTo(180.00f))
                .body("[0].atualizadoEm", notNullValue());
    }

    @Test
    void clienteSemPosicoesRecebeListaVazia() {
        given().queryParam("idCliente", "cliente-sem-contas")
                .queryParam("competencia", "2026-07")
                .when().get("/interno/posicoes")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void competenciaInvalidaRetorna400() {
        given().queryParam("idCliente", "cliente-001")
                .queryParam("competencia", "julho-2026")
                .when().get("/interno/posicoes")
                .then()
                .statusCode(400);
    }

    private static LancamentoRecebido lancamento(String idOrigem, TipoLancamento tipo, String valor) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                "00360305",
                "0001",
                "123456",
                tipo,
                new BigDecimal(valor),
                "BRL",
                OffsetDateTime.parse("2026-07-06T10:00:00-03:00"),
                "consent-abc",
                null,
                null);
    }
}
