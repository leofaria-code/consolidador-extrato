package br.com.escalacaotech.extrato.consulta;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inc-5 — lado CONSUMER do contract test consulta↔consolidação (Sessão 6,
 * decisão 2): a consulta declara aqui o que ESPERA de GET /interno/posicoes
 * (a fonte do cache miss — ADR-006) e o pact resultante é gravado em
 * {@code pacts/} na raiz do repo (pact em disco, Docker-free — ADR-001).
 * A verificação do provider roda na consolidação contra o mesmo arquivo.
 * <p>
 * A deserialização usa o record real do contrato ({@link PosicaoDaConta}):
 * se o shape do JSON não hidratar o tipo compartilhado, o teste quebra aqui —
 * antes de quebrar em produção.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "extrato-consolidacao", pactVersion = PactSpecVersion.V4)
class ContratoPosicoesConsumerPactTest {

    private static final String ISO_OFFSET =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})";

    @Pact(consumer = "extrato-consulta", provider = "extrato-consolidacao")
    V4Pact contratoDePosicoes(PactDslWithProvider builder) {
        var posicoes = PactDslJsonArray.arrayEachLike()
                .stringType("instituicaoOrigem", "00360305")
                .stringType("agencia", "0001")
                .stringType("conta", "123456")
                .stringType("competencia", "2026-07")
                .numberType("entradas", 300.00)
                .numberType("saidas", 120.00)
                .numberType("saldo", 180.00)
                .stringMatcher("atualizadoEm", ISO_OFFSET, "2026-07-07T18:00:00.000-03:00")
                .closeObject();

        return builder
                .given("cliente cliente-001 tem posicao consolidada em 2026-07")
                .uponReceiving("busca de posicoes do cliente na competencia (cache miss)")
                .path("/interno/posicoes")
                .query("idCliente=cliente-001&competencia=2026-07")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json;charset=UTF-8"))
                .body(posicoes)

                .given("cliente cliente-vazio nao tem posicoes")
                .uponReceiving("busca de posicoes de cliente sem contas (extrato vazio, US-06)")
                .path("/interno/posicoes")
                .query("idCliente=cliente-vazio&competencia=2026-07")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json;charset=UTF-8"))
                .body(new PactDslJsonArray())

                .toPact(V4Pact.class);
    }

    @Test
    void consumidorEntendeORespostaDoContrato(MockServer servidor) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var http = HttpClient.newHttpClient();

        // cenário 1: cliente com posições — o shape hidrata o record compartilhado
        var resposta = http.send(HttpRequest.newBuilder(URI.create(
                        servidor.getUrl() + "/interno/posicoes?idCliente=cliente-001&competencia=2026-07"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resposta.statusCode());

        var posicoes = mapper.readValue(resposta.body(), PosicaoDaConta[].class);
        assertEquals(1, posicoes.length);
        assertEquals(YearMonth.of(2026, 7), posicoes[0].competencia());
        assertEquals(0, new BigDecimal("180.0").compareTo(posicoes[0].saldo()));
        assertNotNull(posicoes[0].atualizadoEm(), "carimbo do dado é parte do contrato (US-07)");

        // cenário 2: extrato vazio é resposta bem definida, não erro (US-06)
        var vazia = http.send(HttpRequest.newBuilder(URI.create(
                        servidor.getUrl() + "/interno/posicoes?idCliente=cliente-vazio&competencia=2026-07"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, vazia.statusCode());
        assertTrue(mapper.readValue(vazia.body(), PosicaoDaConta[].class).length == 0);
    }
}
