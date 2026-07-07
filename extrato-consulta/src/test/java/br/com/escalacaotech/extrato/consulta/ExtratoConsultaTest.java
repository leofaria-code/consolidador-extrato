package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoAtualizadaEvento;
import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inc-3 — aceite da issue #3: hit/miss/invalidação DEMONSTRÁVEIS (US-06),
 * carimbo do dado (US-07/Sessão 6 decisão 4), atualizar sob demanda com limite
 * (US-07/Sessão 6 decisão 5) e extrato vazio bem definido (US-06).
 * O contador do dublê da fonte é a prova: hit não chama a fonte; miss chama.
 * Roda sem Docker (in-memory + dublê — perfil B, critério 6).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class ExtratoConsultaTest {

    private static final YearMonth JUL = YearMonth.of(2026, 7);

    @Inject
    @Any
    InMemoryConnector connector;

    @Inject
    FontePosicoesEmMemoria fonte;

    @Inject
    CacheManager cacheManager;

    @BeforeEach
    void limpar() {
        fonte.zerar();
        cacheManager.getCache(ServicoExtrato.CACHE).orElseThrow()
                .invalidateAll().await().indefinitely();
    }

    @Test
    void hitRespondeDoCacheSemIrAFonte() {
        fonte.programar("cli-hit", JUL, List.of(posicao("2026-07-07T10:00:00-03:00")));

        // 1ª consulta: miss -> vai à fonte, popula o cache
        given().get("/extrato/cli-hit/2026-07").then()
                .statusCode(200)
                .body("posicoes", hasSize(1))
                .body("totais.saldo", equalTo(750.00f))
                .body("atualizadoEm", notNullValue());

        // 2ª e 3ª: hit -> não voltam à fonte
        given().get("/extrato/cli-hit/2026-07").then().statusCode(200);
        given().get("/extrato/cli-hit/2026-07").then().statusCode(200);

        assertEquals(1, fonte.chamadas(),
                "3 consultas com cache quente devem ir à fonte exatamente 1 vez");
    }

    @Test
    void eventoPosicaoAtualizadaInvalidaOCache() {
        fonte.programar("cli-evento", JUL, List.of(posicao("2026-07-07T10:00:00-03:00")));

        given().get("/extrato/cli-evento/2026-07").then().statusCode(200);
        assertEquals(1, fonte.chamadas());

        // a consolidação publica o evento (US-10) -> a entrada do cliente é invalidada
        connector.source("posicao-atualizada-in").send(new PosicaoAtualizadaEvento(
                "cli-evento", "00360305", "0001", "123456", JUL,
                OffsetDateTime.parse("2026-07-07T10:05:00-03:00")));

        // a invalidação é assíncrona: consulta até o miss reaparecer
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            given().get("/extrato/cli-evento/2026-07").then().statusCode(200);
            assertEquals(2, fonte.chamadas(),
                    "após o evento, a próxima consulta deve voltar à fonte (miss)");
        });
    }

    @Test
    void atualizarSobDemandaIgnoraOCacheMasRespeitaOLimite() {
        fonte.programar("cli-forcado", JUL, List.of(posicao("2026-07-07T10:00:00-03:00")));

        given().get("/extrato/cli-forcado/2026-07").then().statusCode(200);
        assertEquals(1, fonte.chamadas());

        // atualizar sob demanda: ignora o cache, relê do dado (US-07)
        given().get("/extrato/cli-forcado/2026-07?atualizar=true").then().statusCode(200);
        assertEquals(2, fonte.chamadas(), "atualização forçada deve reler da fonte");

        // cliente ansioso: segunda forçada dentro do intervalo mínimo -> 429
        given().get("/extrato/cli-forcado/2026-07?atualizar=true").then()
                .statusCode(429)
                .body("intervaloMinimoSegundos", equalTo(2));

        // a consulta normal continua servida do cache
        given().get("/extrato/cli-forcado/2026-07").then().statusCode(200);
        assertEquals(2, fonte.chamadas());
    }

    @Test
    void extratoVazioEhRespostaBemDefinidaNaoErro() {
        given().get("/extrato/cli-sem-contas/2026-07").then()
                .statusCode(200)
                .body("posicoes", hasSize(0))
                .body("totais.saldo", equalTo(0))
                .body("atualizadoEm", nullValue());
    }

    @Test
    void competenciaInvalidaRetorna400() {
        given().get("/extrato/cli-x/julho-2026").then().statusCode(400);
    }

    private static PosicaoDaConta posicao(String atualizadoEm) {
        return new PosicaoDaConta("00360305", "0001", "123456", JUL,
                new BigDecimal("1000.00"), new BigDecimal("250.00"), new BigDecimal("750.00"),
                OffsetDateTime.parse(atualizadoEm));
    }
}
