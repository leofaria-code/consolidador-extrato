package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import io.smallrye.faulttolerance.api.CircuitBreakerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camada de resiliência do cache miss (ADR-007, nota de 11/07 — fecha o gap
 * "@CircuitBreaker/@Fallback não exercitados"):
 * <ul>
 *   <li><b>@Timeout 2s</b>: miss lento vira erro rápido (sem retry — retry
 *       amplificaria a carga sobre um serviço já degradado);</li>
 *   <li><b>@CircuitBreaker</b>: falhas repetidas ABREM o circuito e a consulta
 *       para de martelar a consolidação — o passo além do timeout na mesma
 *       lógica de não-amplificação; fecha sozinho após a janela;</li>
 *   <li><b>@Fallback = última resposta boa</b>: degrada com transparência
 *       (US-05) — serve a última cópia conhecida e o carimbo do dado (US-07)
 *       mostra honestamente a idade; sem cópia → 503 com Retry-After.</li>
 * </ul>
 * A memória de última-boa vive junto do cache (mesma instância, mesmo ciclo
 * de vida) e guarda só o que já foi consultado — sem dado pessoal além do que
 * o cache já teria.
 */
@ApplicationScoped
public class FonteResiliente {

    private static final Logger LOG = Logger.getLogger(FonteResiliente.class);

    @Inject
    FontePosicoes fonte;

    private final Map<String, List<PosicaoDaConta>> ultimaBoa = new ConcurrentHashMap<>();

    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 10_000)
    @CircuitBreakerName("fonte-posicoes")
    @Fallback(fallbackMethod = "ultimaRespostaBoa")
    public List<PosicaoDaConta> posicoes(String idCliente, YearMonth competencia) {
        var posicoes = fonte.posicoes(idCliente, competencia);
        ultimaBoa.put(chave(idCliente, competencia), posicoes);
        return posicoes;
    }

    List<PosicaoDaConta> ultimaRespostaBoa(String idCliente, YearMonth competencia) {
        var chave = chave(idCliente, competencia);
        var copia = ultimaBoa.get(chave);
        if (copia == null) {
            throw new FonteIndisponivelException(chave);
        }
        LOG.warnf("Fonte de posições indisponível — servindo última resposta boa para %s "
                + "(o carimbo do dado expõe a idade — US-05/US-07)", chave);
        return copia;
    }

    private static String chave(String idCliente, YearMonth competencia) {
        return idCliente + "|" + competencia;
    }
}
