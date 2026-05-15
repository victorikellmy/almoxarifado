package com.fundacao.aualmoxarifado.exception;

/**
 * Violação de regra de negócio (ex.: estoque insuficiente, setor obrigatório).
 * Convertida em HTTP 409 pelo {@link GlobalExceptionHandler}.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
