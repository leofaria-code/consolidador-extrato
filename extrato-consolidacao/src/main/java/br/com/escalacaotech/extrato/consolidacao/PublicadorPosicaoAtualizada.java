package br.com.escalacaotech.extrato.consolidacao;

import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publicador da outbox (ADR-005): varre {@code evento_pendente} e publica no
 * tópico {@code posicao-atualizada}, marcando {@code publicado_em} <b>somente
 * após o ack do broker</b> — entrega "pelo menos uma vez" (US-10).
 * <p>
 * Queda entre o envio e a marcação ⇒ reenvio na retomada (repetição tolerada —
 * Sessão 6, decisão 3: consumidores idempotentes). Falha de publicação
 * interrompe o lote e preserva a ordem: o pendente que falhou é retentado na
 * próxima varredura, antes dos posteriores.
 * <p>
 * Chave Kafka = instituição+agência+conta (mesma convenção da ingestão):
 * eventos da mesma conta saem ordenados na mesma partição.
 */
@ApplicationScoped
public class PublicadorPosicaoAtualizada {

    private static final Logger LOG = Logger.getLogger(PublicadorPosicaoAtualizada.class);
    private static final int TAMANHO_DO_LOTE = 50;
    private static final long ACK_TIMEOUT_SEGUNDOS = 10;

    @Channel("posicao-atualizada-out")
    Emitter<PosicaoAtualizadaEvento> emitter;

    @Scheduled(every = "{consolidacao.outbox.intervalo}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void publicarPendentes() {
        for (EventoPendente pendente : EventoPendente.pendentes(TAMANHO_DO_LOTE)) {
            try {
                enviarEAguardarAck(pendente);
                pendente.publicadoEm = OffsetDateTime.now();
            } catch (Exception falha) {
                LOG.warnf("Evento %d não publicado — permanece pendente para a próxima varredura: %s",
                        pendente.id, falha.getMessage());
                break; // não pula o que falhou: preservaria fora de ordem
            }
        }
    }

    private void enviarEAguardarAck(EventoPendente pendente) {
        var confirmado = new CompletableFuture<Void>();
        var chave = pendente.instituicaoOrigem + ":" + pendente.agencia + ":" + pendente.conta;

        Message<PosicaoAtualizadaEvento> mensagem = Message.of(
                        pendente.comoEvento(),
                        () -> {
                            confirmado.complete(null);
                            return CompletableFuture.<Void>completedFuture(null);
                        },
                        falha -> {
                            confirmado.completeExceptionally(falha);
                            return CompletableFuture.<Void>completedFuture(null);
                        })
                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder().withKey(chave).build());

        emitter.send(mensagem);
        confirmado.orTimeout(ACK_TIMEOUT_SEGUNDOS, TimeUnit.SECONDS).join();
    }
}
