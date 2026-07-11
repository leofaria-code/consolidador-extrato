package br.com.escalacaotech.extrato.consolidacao;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Contrato de MENSAGEM do tópico {@code lancamentos-recebidos} — o segundo
 * par eleito na Sessão 6 (decisão 2), fechado após a aula-08 (message pacts).
 * <p>
 * A consolidação (consumer) declara aqui o shape mínimo que precisa de um
 * {@code LancamentoRecebido} para incorporar: a identidade (chave de
 * idempotência, ADR-004), os campos da posição e a competência. Campos
 * opcionais da ficha (descricao, categoriaOrigem — erratum #1 da Sessão 6)
 * ficam FORA do contrato de propósito: opcional não se exige.
 * <p>
 * O teste hidrata o record real do contrato — como no par HTTP, shape que
 * não deserializa quebra aqui, não em produção. Pact versionado em pacts/;
 * o provider (ingestão) o verifica no build.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "extrato-ingestao", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class ContratoLancamentosConsumerPactTest {

    private static final String ISO_OFFSET =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})";

    @Pact(consumer = "extrato-consolidacao", provider = "extrato-ingestao")
    MessagePact contratoDeLancamento(MessagePactBuilder builder) {
        var corpo = new PactDslJsonBody()
                .stringType("idCliente", "cliente-001")
                .stringType("idLancamentoOrigem", "TX-0001")
                .stringType("instituicaoOrigem", "00360305")
                .stringType("agencia", "0001")
                .stringType("conta", "123456")
                .stringMatcher("tipo", "CREDITO|DEBITO", "CREDITO")
                .numberType("valor", 150.00)
                .stringType("moeda", "BRL")
                .stringMatcher("dataHoraOcorrencia", ISO_OFFSET, "2026-07-10T14:30:00-03:00")
                .stringType("idConsentimento", "consent-001");

        return builder
                .expectsToReceive("um lancamento validado publicado no topico lancamentos-recebidos")
                .withMetadata(Map.of("contentType", "application/json"))
                .withContent(corpo)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "contratoDeLancamento")
    void consumidorHidrataOContratoNoRecordReal(List<Message> mensagens) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var lancamento = mapper.readValue(
                mensagens.getFirst().contentsAsBytes(), LancamentoRecebido.class);

        // a chave de idempotência (ADR-004) precisa hidratar por inteiro
        assertEquals("00360305", lancamento.identidade().instituicaoOrigem());
        assertEquals("TX-0001", lancamento.identidade().idLancamentoOrigem());
        assertEquals(TipoLancamento.CREDITO, lancamento.tipo());
        assertNotNull(lancamento.dataHoraOcorrencia(),
                "a competência nasce da data de ocorrência (US-03)");
        assertNotNull(lancamento.valor());
    }
}
