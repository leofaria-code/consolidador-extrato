package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
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
 * Inc-2 — fluxo completo da consolidação:
 * <ul>
 *   <li>US-05: posição pronta por conta × competência (entradas/saídas/saldo) + carimbo do dado;</li>
 *   <li>US-03: lançamento de competência anterior reabre a competência dele;</li>
 *   <li>US-10 + ADR-005: cada incorporação publica {@code posicao-atualizada} via outbox
 *       (só referência, sem dado de lançamento) e marca {@code publicado_em} após o ack;</li>
 *   <li>ADR-004: repetido não gera evento nem registro na outbox.</li>
 * </ul>
 * Roda sem Docker (in-memory + H2 — perfil B, critério 6).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class FluxoConsolidacaoTest {

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
    void posicaoAgregaEntradasSaidasESaldoComCarimboDoDado() {
        var origem = connector.source("lancamentos-in");

        origem.send(lancamento("TX-300", TipoLancamento.CREDITO, "1000.00", "2026-07-01T09:00:00-03:00"));
        origem.send(lancamento("TX-301", TipoLancamento.DEBITO, "250.00", "2026-07-02T10:00:00-03:00"));

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 2);

        var posicao = base.posicao("00360305", "0001", "123456", YearMonth.of(2026, 7));
        assertNotNull(posicao);
        assertEquals(0, new BigDecimal("1000.00").compareTo(posicao.entradas), "entradas = créditos");
        assertEquals(0, new BigDecimal("250.00").compareTo(posicao.saidas), "saídas = débitos");
        assertEquals(0, new BigDecimal("750.00").compareTo(posicao.saldo), "saldo = entradas - saídas");
        assertNotNull(posicao.atualizadoEm, "carimbo 'atualizado às' é do dado (Sessão 6, decisão 4)");
    }

    @Test
    void lancamentoAtrasadoReabreACompetenciaDaOcorrencia() {
        var origem = connector.source("lancamentos-in");

        // lançamento do mês corrente e um ATRASADO de maio (US-03: competência = ocorrência)
        origem.send(lancamento("TX-310", TipoLancamento.CREDITO, "100.00", "2026-07-03T08:00:00-03:00"));
        origem.send(lancamento("TX-311", TipoLancamento.DEBITO, "40.00", "2026-05-20T14:00:00-03:00"));

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.totalPosicoes() == 2);

        var maio = base.posicao("00360305", "0001", "123456", YearMonth.of(2026, 5));
        assertNotNull(maio, "competência de maio deve ser (re)aberta pela ocorrência atrasada");
        assertEquals(0, new BigDecimal("-40.00").compareTo(maio.saldo));

        var julho = base.posicao("00360305", "0001", "123456", YearMonth.of(2026, 7));
        assertEquals(0, new BigDecimal("100.00").compareTo(julho.saldo));
    }

    @Test
    void cadaIncorporacaoPublicaEventoDeReferenciaEMarcaOutbox() {
        var origem = connector.source("lancamentos-in");
        var sink = connector.sink("posicao-atualizada-out");

        origem.send(lancamento("TX-320", TipoLancamento.CREDITO, "77.00", "2026-07-04T11:00:00-03:00"));

        // o publicador @Scheduled varre a outbox e publica; marca só após o ack
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> sink.received().size() == 1 && base.eventosPendentes() == 0);

        var evento = (PosicaoAtualizadaEvento) sink.received().getFirst().getPayload();
        assertEquals("cliente-001", evento.idCliente());
        assertEquals("00360305", evento.instituicaoOrigem());
        assertEquals("123456", evento.conta());
        assertEquals(YearMonth.of(2026, 7), evento.competencia(),
                "competência do evento é a da ocorrência");
        assertNotNull(evento.atualizadoEm(), "referência carrega o carimbo do dado");
    }

    @Test
    void repetidoNaoGeraEventoNemRegistroNaOutbox() {
        var origem = connector.source("lancamentos-in");
        var mesmo = lancamento("TX-330", TipoLancamento.DEBITO, "10.00", "2026-07-04T12:00:00-03:00");

        origem.send(mesmo);
        origem.send(mesmo);
        origem.send(mesmo);

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 1);

        assertEquals(1, base.eventosRegistrados(),
                "só a primeira incorporação registra evento na outbox (ADR-004/ADR-005)");
    }

    private static LancamentoRecebido lancamento(String idOrigem, TipoLancamento tipo,
                                                 String valor, String ocorrencia) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                "00360305",
                "0001",
                "123456",
                tipo,
                new BigDecimal(valor),
                "BRL",
                OffsetDateTime.parse(ocorrencia),
                "consent-abc",
                null,
                null);
    }
}
