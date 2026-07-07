package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.PedidoReconsolidacao;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * US-09 — fila de reconsolidação (o "guichê" da Sessão 4):
 * <ul>
 *   <li>POST /reconsolidacoes → aceite imediato (202) + pedido na fila;</li>
 *   <li>o trabalhador reapura os lançamentos, refaz os totais (corrigindo a
 *       divergência da contestação) e registra o evento de invalidação;</li>
 *   <li>reprocessar o MESMO pedido é seguro — recálculo absoluto é idempotente.</li>
 * </ul>
 * Roda sem Docker (canais in-memory + H2 — perfil B; o RabbitMQ real é do
 * plano A, ADR-003). A ponte fila-out→fila-in é feita manualmente no teste.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class ReconsolidacaoTest {

    private static final YearMonth JUL = YearMonth.of(2026, 7);

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ServicoConsolidacao servico;

    @Inject
    ApoioBaseDeTeste base;

    @BeforeEach
    void limpar() {
        base.limparBase();
        connector.sink("reconsolidacao-out").clear();
    }

    @Test
    void aceiteImediatoEPedidoNaFila() {
        given().contentType("application/json")
                .body(Map.of(
                        "idCliente", "cliente-001",
                        "instituicaoOrigem", "00360305",
                        "agencia", "0001",
                        "conta", "123456",
                        "competencia", "2026-07",
                        "motivo", "contestação do cliente"))
                .when().post("/reconsolidacoes")
                .then()
                .statusCode(202)
                .body("status", equalTo("ACEITO"))
                .body("idPedido", notNullValue());

        var fila = connector.sink("reconsolidacao-out");
        await().atMost(5, TimeUnit.SECONDS).until(() -> fila.received().size() == 1);

        var pedido = (PedidoReconsolidacao) fila.received().getFirst().getPayload();
        assertEquals("123456", pedido.conta());
        assertEquals(JUL, pedido.competencia());
        assertEquals("contestação do cliente", pedido.motivo());
    }

    @Test
    void solicitacaoSemCamposObrigatoriosRetorna400() {
        given().contentType("application/json")
                .body(Map.of("idCliente", "cliente-001", "competencia", "2026-07"))
                .when().post("/reconsolidacoes")
                .then()
                .statusCode(400)
                .body("camposFaltantes", hasItem("conta"))
                .body("camposFaltantes", hasItem("motivo"));
    }

    @Test
    void guicheReapuraECorrigeADivergencia() {
        // posição legítima: crédito 100 - débito 30 = 70
        servico.incorporar(lancamento("TX-600", TipoLancamento.CREDITO, "100.00"));
        servico.incorporar(lancamento("TX-601", TipoLancamento.DEBITO, "30.00"));

        // contestação: saldo diverge dos lançamentos (corrompido de propósito)
        base.corromperSaldo("00360305", "0001", "123456", JUL, "999.99");

        var eventosAntes = base.eventosRegistrados();

        // o pedido chega ao guichê (ponte manual out->in: RabbitMQ é do plano A)
        connector.source("reconsolidacao-in").send(pedido("pedido-1"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var posicao = base.posicao("00360305", "0001", "123456", JUL);
            assertEquals(0, new BigDecimal("70.00").compareTo(posicao.saldo),
                    "a reapuração deve refazer o saldo a partir dos lançamentos");
        });

        assertEquals(eventosAntes + 1, base.eventosRegistrados(),
                "a reconsolidação dispara a invalidação do cache (evento via outbox)");

        // reprocesso do MESMO pedido (reentrega da fila): idempotente por natureza
        connector.source("reconsolidacao-in").send(pedido("pedido-1"));

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.eventosRegistrados() == eventosAntes + 2);
        var posicao = base.posicao("00360305", "0001", "123456", JUL);
        assertEquals(0, new BigDecimal("70.00").compareTo(posicao.saldo),
                "reprocessar o mesmo pedido produz a mesma posição");
    }

    private static PedidoReconsolidacao pedido(String idPedido) {
        return new PedidoReconsolidacao(idPedido, "cliente-001", "00360305", "0001",
                "123456", JUL, "contestação do cliente", OffsetDateTime.now());
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
                OffsetDateTime.parse("2026-07-06T09:00:00-03:00"),
                "consent-abc",
                null,
                null);
    }
}
