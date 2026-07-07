package br.com.escalacaotech.extrato.consulta;

import br.com.escalacaotech.extrato.contratos.PosicaoDaConta;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dublê da fonte de posições ({@code @Mock} substitui a implementação HTTP nos
 * testes). O contador de chamadas é o instrumento que torna hit/miss/invalidação
 * <b>demonstráveis</b> (aceite do Inc-3): hit não incrementa; miss incrementa.
 */
@Mock
@ApplicationScoped
public class FontePosicoesEmMemoria implements FontePosicoes {

    private final Map<String, List<PosicaoDaConta>> porChave = new ConcurrentHashMap<>();
    private final AtomicInteger chamadas = new AtomicInteger();

    @Override
    public List<PosicaoDaConta> posicoes(String idCliente, YearMonth competencia) {
        chamadas.incrementAndGet();
        return porChave.getOrDefault(chave(idCliente, competencia), List.of());
    }

    public void programar(String idCliente, YearMonth competencia, List<PosicaoDaConta> posicoes) {
        porChave.put(chave(idCliente, competencia), posicoes);
    }

    public int chamadas() {
        return chamadas.get();
    }

    public void zerar() {
        porChave.clear();
        chamadas.set(0);
    }

    private static String chave(String idCliente, YearMonth competencia) {
        return idCliente + "|" + competencia;
    }
}
