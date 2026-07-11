package br.com.escalacaotech.extrato.consulta;

/**
 * Miss com a fonte fora e sem cópia recente para degradar (US-05): o cliente
 * recebe 503 transparente com Retry-After (ver {@link MapeadorFonteIndisponivel}).
 */
public class FonteIndisponivelException extends RuntimeException {

    public FonteIndisponivelException(String chave) {
        super("fonte de posições indisponível e sem última resposta boa para " + chave);
    }
}
