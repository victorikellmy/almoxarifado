package com.fundacao.aualmoxarifado.exception;

/**
 * Código bipado tem formato malformado (vazio, espaços, caracteres de
 * controle, tamanho fora da faixa).
 *
 * <p>Distinção importante para o app mobile: este erro indica leitura
 * corrompida (leitor com defeito, etiqueta danificada) — diferente de
 * {@link RecursoNaoEncontradoException}, que indica SKU bem-formado mas
 * inexistente no cadastro.</p>
 *
 * <p>Convertida em HTTP 400 pelo {@link GlobalExceptionHandler}.</p>
 */
public class SkuFormatoInvalidoException extends RuntimeException {

    public SkuFormatoInvalidoException(String codigo) {
        super("Código bipado em formato inválido: \"" + codigo + "\".");
    }
}
