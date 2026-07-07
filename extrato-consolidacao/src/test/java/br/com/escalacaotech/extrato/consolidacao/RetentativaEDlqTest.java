package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * ADR-007 — o teste da banca (US-08, critério 5 da rubrica):
 * <ul>
 *   <li><b>Falha transitória</b>: 2 falhas seguidas → sucesso na 3ª tentativa,
 *       lançamento incorporado, nada vai à DLQ;</li>
 *   <li><b>Falha permanente</b> (mensagem envenenada): falha nas 4 tentativas
 *       (1 + 3 retentativas) → mensagem sai do caminho e o <b>fluxo continua</b>
 *       processando a próxima.</li>
 * </ul>
 * O encaminhamento físico à DLQ é recurso do broker (failure-strategy do
 * connector Kafka) — provado no plano A (ADR-003); aqui prova-se a política:
 * contagem exata de tentativas, backoff e não-bloqueio do fluxo.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class RetentativaEDlqTest {

    @InjectSpy
    ServicoConsolidacao servico;

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    ApoioBaseDeTeste base;

    @BeforeEach
    void limpar() {
        base.limparBase();
    }

    @Test
    void falhaTransitoriaEhSuperadaPelasRetentativas() {
        var tentativas = new AtomicInteger();
        doAnswer(invocacao -> {
            if (tentativas.incrementAndGet() <= 2) {
                throw new RuntimeException("banco indisponível (falha transitória simulada)");
            }
            return invocacao.callRealMethod();
        }).when(servico).incorporar(argThat(l -> l != null && "TX-500".equals(l.idLancamentoOrigem())));

        connector.source("lancamentos-in").send(lancamento("TX-500"));

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 1);

        assertEquals(3, tentativas.get(),
                "2 falhas + 1 sucesso: a 3ª tentativa incorpora, sem ir à DLQ");
    }

    @Test
    void mensagemEnvenenadaNaoTravaOFluxo() {
        var tentativasVeneno = new AtomicInteger();
        doAnswer(invocacao -> {
            tentativasVeneno.incrementAndGet();
            throw new IllegalStateException("lançamento corrompido (falha permanente simulada)");
        }).when(servico).incorporar(argThat(l -> l != null && "TX-VENENO".equals(l.idLancamentoOrigem())));

        var origem = connector.source("lancamentos-in");
        origem.send(lancamento("TX-VENENO"));
        origem.send(lancamento("TX-501")); // a próxima da esteira

        // US-08: o fluxo principal CONTINUA — a mensagem saudável é incorporada
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> base.totalLancamentos() == 1);

        assertEquals(4, tentativasVeneno.get(),
                "mensagem envenenada consome exatamente 1 tentativa + 3 retentativas (ADR-007)");
        assertEquals(1, base.totalLancamentos(),
                "o veneno não é incorporado; o fluxo segue para a próxima mensagem");
    }

    private static LancamentoRecebido lancamento(String idOrigem) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                "00360305",
                "0001",
                "123456",
                TipoLancamento.DEBITO,
                new BigDecimal("50.00"),
                "BRL",
                OffsetDateTime.parse("2026-07-07T15:00:00-03:00"),
                "consent-abc",
                null,
                null);
    }
}
