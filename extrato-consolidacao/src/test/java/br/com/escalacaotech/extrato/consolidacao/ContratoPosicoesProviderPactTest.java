package br.com.escalacaotech.extrato.consolidacao;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import br.com.escalacaotech.extrato.contratos.LancamentoRecebido;
import br.com.escalacaotech.extrato.contratos.TipoLancamento;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Inc-5 — lado PROVIDER do contract test consulta↔consolidação (Sessão 6,
 * decisão 2): reproduz o pact gravado em {@code pacts/} (pelo consumer da
 * consulta) contra a aplicação REAL — endpoint, serialização e banco de
 * verdade (H2 no perfil B). Estados são semeados pelo caminho real
 * ({@code ServicoConsolidacao.incorporar}), não por fixture de banco.
 * <p>
 * Se alguém mudar GET /interno/posicoes de forma incompatível, este teste
 * quebra no build — antes de quebrar a consulta em produção.
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
@Provider("extrato-consolidacao")
@PactFolder("../pacts")
class ContratoPosicoesProviderPactTest {

    @Inject
    ServicoConsolidacao servico;

    @Inject
    ApoioBaseDeTeste base;

    @BeforeEach
    void prepararAlvo(PactVerificationContext contexto) {
        contexto.setTarget(new HttpTestTarget("localhost", RestAssured.port));
        base.limparBase();
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verificaCadaInteracaoDoContrato(PactVerificationContext contexto) {
        contexto.verifyInteraction();
    }

    @State("cliente cliente-001 tem posicao consolidada em 2026-07")
    void clienteComPosicao() {
        servico.incorporar(lancamento("TX-PACT-1", TipoLancamento.CREDITO, "300.00"), "pact");
        servico.incorporar(lancamento("TX-PACT-2", TipoLancamento.DEBITO, "120.00"), "pact");
    }

    @State("cliente cliente-vazio nao tem posicoes")
    void clienteSemPosicoes() {
        // base já limpa no @BeforeEach — extrato vazio é o estado natural
    }

    private static LancamentoRecebido lancamento(String idOrigem, TipoLancamento tipo, String valor) {
        return new LancamentoRecebido(
                "cliente-001",
                idOrigem,
                "00360305",
                "0001",
                "123456",
                tipo,
                new BigDecimal(valor),
                "BRL",
                OffsetDateTime.parse("2026-07-06T10:00:00-03:00"),
                "consent-abc",
                null,
                null);
    }
}
