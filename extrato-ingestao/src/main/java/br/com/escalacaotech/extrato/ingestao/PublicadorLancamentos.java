package br.com.escalacaotech.extrato.ingestao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.MDC;

import java.nio.charset.StandardCharsets;

/**
 * Publica lançamentos válidos no tópico de ingestão (US-01).
 * <p>
 * Chave da mensagem = instituição+agência+conta: preserva a ordem por conta dentro
 * de uma partição (a ordem global não é garantida nem exigida — Sessão 2, decisão 4).
 * <p>
 * Correlação (US-12/Inc-6): o id do request (posto no MDC pelo
 * {@link FiltroCorrelacao}) segue na mensagem como header Kafka — a consolidação
 * o repõe no MDC dela, e o fluxo inteiro loga o mesmo id.
 */
@ApplicationScoped
public class PublicadorLancamentos {

    @Channel("lancamentos-out")
    Emitter<LancamentoRecebido> emitter;

    @Inject
    MeterRegistry registry;

    public void publicar(LancamentoRecebido lancamento) {
        var chave = lancamento.instituicaoOrigem() + ":" + lancamento.agencia() + ":" + lancamento.conta();
        var correlacao = (String) MDC.get(Correlacao.MDC_CHAVE);

        var headers = new RecordHeaders();
        if (correlacao != null) {
            headers.add(Correlacao.HEADER_MENSAGEM, correlacao.getBytes(StandardCharsets.UTF_8));
        }
        var metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(chave)
                .withHeaders(headers)
                .build();

        var mensagem = Message.of(lancamento).addMetadata(metadata);
        if (correlacao != null) {
            mensagem = mensagem.addMetadata(new CorrelacaoMetadata(correlacao));
        }
        emitter.send(mensagem);
        registry.counter("extrato.ingestao.lancamentos.publicados").increment();
    }
}
