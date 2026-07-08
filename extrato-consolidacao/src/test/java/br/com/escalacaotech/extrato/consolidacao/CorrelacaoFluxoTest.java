package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * US-12/Inc-6 — o trecho central da trilha: o id que chegou com o lançamento
 * atravessa a transação dos três efeitos, é preservado pela outbox
 * (coluna correlacao_id) e sai no evento posicao-atualizada como header —
 * o fim do fluxo (invalidação na consulta) loga o mesmo id do começo.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class CorrelacaoFluxoTest {

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
    void correlacaoAtravessaConsolidacaoOutboxEEvento() {
        var lancamento = new LancamentoRecebido(
                "cliente-001", "TX-CORR-1", "00360305", "0001", "123456",
                TipoLancamento.CREDITO, new BigDecimal("42.00"), "BRL",
                OffsetDateTime.parse("2026-07-07T18:30:00-03:00"), "consent-abc", null, null);

        connector.source("lancamentos-in").send(
                Message.of(lancamento, Metadata.of(new CorrelacaoMetadata("trace-fim-a-fim"))));

        var sink = connector.sink("posicao-atualizada-out");
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> sink.received().size() == 1 && base.eventosPendentes() == 0);

        var evento = sink.received().getFirst();

        assertEquals("trace-fim-a-fim",
                evento.getMetadata(CorrelacaoMetadata.class).orElseThrow().id(),
                "a outbox preserva a correlação através da fronteira assíncrona");

        var kafka = evento.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow();
        var header = kafka.getHeaders().lastHeader("correlation-id");
        assertEquals("trace-fim-a-fim", new String(header.value(), StandardCharsets.UTF_8),
                "o evento sai para o fio com o header do fluxo original");
    }
}
