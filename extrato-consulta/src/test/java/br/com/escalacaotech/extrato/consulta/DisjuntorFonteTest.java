package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ADR-007 (nota 11/07) — a camada de resiliência do cache miss, fechando o
 * gap "@CircuitBreaker/@Fallback não exercitados" do AVALIACAO:
 * <ul>
 *   <li>fallback serve a <b>última resposta boa</b> quando a fonte falha
 *       (degradação com transparência — US-05; o carimbo expõe a idade);</li>
 *   <li>falhas repetidas <b>abrem o disjuntor</b> e a consulta PARA de chamar
 *       a fonte (redução de carga — o passo além do timeout);</li>
 *   <li>sem última-boa, o cliente recebe <b>503 transparente</b> com
 *       Retry-After — nunca um 500 opaco.</li>
 * </ul>
 * Config do disjuntor encurtada no perfil de teste (volume 4, delay 300ms).
 */
@QuarkusTest
@QuarkusTestResource(RecursosEmMemoria.class)
class DisjuntorFonteTest {

    private static final YearMonth JUL = YearMonth.of(2026, 7);

    @Inject
    FonteResiliente fonteResiliente;

    @Inject
    FontePosicoesEmMemoria fonte;

    @Inject
    CircuitBreakerMaintenance disjuntores;

    @BeforeEach
    @AfterEach
    void limpar() {
        // reset determinístico do disjuntor (estado + janela deslizante):
        // não vaza abertura nem falhas acumuladas entre testes/classes
        fonte.zerar();
        disjuntores.reset("fonte-posicoes");
    }

    @Test
    void fallbackServeAUltimaRespostaBoaComCarimboAntigo() {
        fonte.programar("cli-cb-1", JUL, List.of(posicao("2026-07-11T08:00:00-03:00")));
        var boa = fonteResiliente.posicoes("cli-cb-1", JUL);
        assertEquals(1, boa.size());

        fonte.falharProximas(1);
        var servida = fonteResiliente.posicoes("cli-cb-1", JUL);

        assertEquals(boa, servida, "fonte fora -> última resposta boa, com o carimbo antigo");
    }

    @Test
    void semUltimaBoaOClienteRecebe503Transparente() {
        fonte.falharProximas(1);

        given().get("/extrato/cli-cb-sem-copia/2026-07")
                .then()
                .statusCode(503)
                .header("Retry-After", equalTo("30"))
                .body("tenteNovamenteEmSegundos", equalTo(30));
    }

    @Test
    void falhasRepetidasAbremODisjuntorEAFonteParaDeSerChamada() {
        fonte.falharProximas(100);

        // volume de teste = 4: quatro falhas reais abrem o circuito
        for (int i = 0; i < 4; i++) {
            assertThrows(FonteIndisponivelException.class,
                    () -> fonteResiliente.posicoes("cli-cb-aberto", JUL));
        }
        var chamadasAteAbrir = fonte.chamadas();
        assertEquals(4, chamadasAteAbrir, "as 4 primeiras falhas chegam à fonte");

        // circuito ABERTO: a chamada falha rápido SEM tocar a fonte
        assertThrows(FonteIndisponivelException.class,
                () -> fonteResiliente.posicoes("cli-cb-aberto", JUL));
        assertEquals(chamadasAteAbrir, fonte.chamadas(),
                "disjuntor aberto: a consulta para de martelar a consolidação degradada");
    }

    private static PosicaoDaConta posicao(String atualizadoEm) {
        return new PosicaoDaConta("00360305", "0001", "123456", JUL,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                OffsetDateTime.parse(atualizadoEm));
    }
}
