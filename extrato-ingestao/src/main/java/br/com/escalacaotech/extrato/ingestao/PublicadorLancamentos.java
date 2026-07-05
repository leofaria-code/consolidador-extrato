package br.com.escalacaotech.extrato.ingestao;

import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Publica lançamentos válidos no tópico de ingestão (US-01).
 * <p>
 * Chave da mensagem = instituição+agência+conta: preserva a ordem por conta dentro
 * de uma partição (a ordem global não é garantida nem exigida — Sessão 2, decisão 4).
 */
@ApplicationScoped
public class PublicadorLancamentos {

    @Channel("lancamentos-out")
    Emitter<LancamentoRecebido> emitter;

    public void publicar(LancamentoRecebido lancamento) {
        var chave = lancamento.instituicaoOrigem() + ":" + lancamento.agencia() + ":" + lancamento.conta();
        var metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(chave)
                .build();
        emitter.send(Message.of(lancamento).addMetadata(metadata));
    }
}
