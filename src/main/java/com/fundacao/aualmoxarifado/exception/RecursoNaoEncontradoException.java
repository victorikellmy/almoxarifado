package com.fundacao.aualmoxarifado.exception;

/**
 * Recurso (entidade) não encontrado no banco.
 * Convertida em HTTP 404 pelo {@link GlobalExceptionHandler}.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String entidade, Object id) {
        super("%s com id '%s' não encontrado.".formatted(entidade, id));
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
