package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.DeserializationFailureHandler;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;

/**
 * Falha de DESERIALIZAÇÃO é uma classe própria de mensagem envenenada (achado
 * do plano A, 11/07): ela acontece <b>antes</b> de a mensagem existir para o
 * consumidor — o retry/DLQ da ADR-007 nunca é alcançado e, sem este handler,
 * o connector re-tenta o poll para sempre: a partição TRAVA e o lançamento
 * válido atrás do lixo nunca é processado (violação direta da US-08).
 * <p>
 * Tratamento no espírito da US-08 — sem descarte silencioso: os bytes crus
 * vão para a MESMA DLQ, com a causa nos headers (contrato da Sessão 4), e o
 * consumo segue. Devolver {@code null} sinaliza ao connector para entregar a
 * mensagem com payload nulo — o consumidor a reconhece e apenas confirma
 * (o encaminhamento já aconteceu aqui).
 */
@ApplicationScoped
@Identifier("falha-deserializacao-lancamentos")
public class FalhaDeserializacaoLancamentos
        implements DeserializationFailureHandler<LancamentoRecebido> {

    private static final Logger LOG = Logger.getLogger(FalhaDeserializacaoLancamentos.class);

    @Channel("lancamentos-dlq-out")
    Emitter<byte[]> dlq;

    @Override
    public LancamentoRecebido handleDeserializationFailure(String topic, boolean isKey,
                                                           String deserializer, byte[] data,
                                                           Exception falha, Headers headers) {
        if (isKey) {
            return null; // chave ilegível: mesmo regime, sem duplicar o encaminhamento
        }

        var causa = new RecordHeaders();
        causa.add("dead-letter-reason",
                ("falha de deserializacao: " + falha)
                        .getBytes(StandardCharsets.UTF_8));
        causa.add("dead-letter-topic", topic.getBytes(StandardCharsets.UTF_8));

        dlq.send(Message.of(data == null ? new byte[0] : data)
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                        .withHeaders(causa).build()));

        LOG.errorf("Mensagem ilegível no tópico %s encaminhada à DLQ (%d bytes): %s",
                topic, data == null ? 0 : data.length, falha.toString());
        return null;
    }
}
