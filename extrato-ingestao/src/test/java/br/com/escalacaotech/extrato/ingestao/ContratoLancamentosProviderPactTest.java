package br.com.escalacaotech.extrato.ingestao;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Lado PROVIDER do contrato de mensagem do tópico {@code lancamentos-recebidos}
 * (2º par da Sessão 6, decisão 2). Reproduz o pact gravado pela consolidação
 * em {@code pacts/} contra a serialização REAL de um {@link LancamentoRecebido}
 * válido — o mesmo shape que o {@code PublicadorLancamentos} põe no fio
 * (JSON via Jackson com JSR-310, datas ISO como no Quarkus).
 * <p>
 * Se alguém mudar o contrato compartilhado de forma incompatível com o que a
 * consolidação espera, este teste quebra o build da ingestão.
 */
@Provider("extrato-ingestao")
@PactFolder("../pacts")
class ContratoLancamentosProviderPactTest {

    @BeforeEach
    void alvo(PactVerificationContext contexto) {
        contexto.setTarget(new MessageTestTarget());
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificaCadaInteracaoDoContrato(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    @PactVerifyProvider("um lancamento validado publicado no topico lancamentos-recebidos")
    String lancamentoValidadoPublicado() throws Exception {
        var lancamento = new LancamentoRecebido(
                "cliente-001",
                "TX-0001",
                "00360305",
                "0001",
                "123456",
                TipoLancamento.CREDITO,
                new BigDecimal("150.00"),
                "BRL",
                OffsetDateTime.parse("2026-07-10T14:30:00-03:00"),
                "consent-001",
                null,
                null);

        // espelha a serialização do canal (Jackson do Quarkus: JSR-310, datas ISO)
        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper.writeValueAsString(lancamento);
    }
}
