package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
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
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * US-02 — teste central da idempotência (critério 3 da rubrica), agora contra a
 * base segregada (ADR-004): reprocessar a mesma mensagem N vezes NÃO duplica
 * lançamento nem altera a posição consolidada.
 * Roda sem Docker (canal in-memory + H2 — perfil B, critério 6).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class ConsumidorIdempotenteTest {

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ApoioBaseDeTeste base;

    @BeforeEach
    void limpar() {
        base.limparBase();
        connector.sink("posicao-atualizada-out").clear();
    }

    @Test
    void reprocessarAMesmaMensagemNaoDuplicaNemAlteraAPosicao() {
        var origem = connector.source("lancamentos-in");
        var lancamento = lancamento("TX-100", "00360305");

        // a origem reenvia o MESMO lançamento 3 vezes (retry legítimo do lado dela)
        origem.send(lancamento);
        origem.send(lancamento);
        origem.send(lancamento);

        // e envia um lançamento DIFERENTE (mesma instituição, outro id)
        origem.send(lancamento("TX-101", "00360305"));

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 2);

        assertEquals(2, base.totalLancamentos(),
                "3 envios do mesmo lançamento + 1 distinto devem resultar em exatamente 2 incorporados");

        // US-02: "sem alterar a posição consolidada" — os totais são os de um
        // processamento único (2 débitos de 250.00, nunca 4)
        var posicao = base.posicao("00360305", "0001", "123456", YearMonth.of(2026, 7));
        assertNotNull(posicao, "posição da conta na competência deve existir");
        assertEquals(0, new BigDecimal("500.00").compareTo(posicao.saidas),
                "saídas devem somar os 2 lançamentos distintos, ignorando os repetidos");
        assertEquals(0, new BigDecimal("-500.00").compareTo(posicao.saldo));
    }

    @Test
    void mesmoIdDeOrigensDiferentesNaoColide() {
        var origem = connector.source("lancamentos-in");

        // mesmo idLancamentoOrigem, instituições diferentes -> identidades diferentes (Sessão 2)
        origem.send(lancamento("TX-200", "00360305"));
        origem.send(lancamento("TX-200", "60701190"));

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 2);
    }

    private static LancamentoRecebido lancamento(String idOrigem, String instituicao) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                instituicao,
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
