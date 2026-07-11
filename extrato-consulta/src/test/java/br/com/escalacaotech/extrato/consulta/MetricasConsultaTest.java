package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * ADR-008 — este serviço não tem contador custom: hit/miss vêm PRONTOS do
 * Caffeine via Micrometer (`metrics-enabled` no cache, ADR-006) e o disjuntor
 * expõe `ft_*` via SmallRye FT. O teste prova que o built-in está ligado:
 * miss (1ª consulta) e hit (2ª) aparecem como séries de `cache_gets_total`.
 * Asserções de presença (counters acumulam). Roda sem Docker (dublê da fonte).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class MetricasConsultaTest {

    private static final YearMonth JUL = YearMonth.of(2026, 7);

    @Inject
    FontePosicoesEmMemoria fonte;

    @Test
    void hitEMissDoCacheSaoExpostosPeloBuiltInDoCaffeine() {
        fonte.programar("cli-metrica", JUL, List.of(
                new PosicaoDaConta("00360305", "0001", "123456", JUL,
                        new BigDecimal("1000.00"), new BigDecimal("250.00"), new BigDecimal("750.00"),
                        OffsetDateTime.parse("2026-07-07T10:00:00-03:00"))));

        given().get("/extrato/cli-metrica/2026-07").then().statusCode(200); // miss
        given().get("/extrato/cli-metrica/2026-07").then().statusCode(200); // hit

        given().get("/q/metrics").then()
                .statusCode(200)
                .body(containsString("cache_gets_total"))
                .body(containsString("result=\"hit\""))
                .body(containsString("result=\"miss\""))
                .body(containsString("cache=\"extrato-consolidado\""));
    }
}
