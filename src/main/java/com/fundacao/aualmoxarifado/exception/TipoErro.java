package com.fundacao.aualmoxarifado.exception;

/**
 * Mapa único exceção → (status HTTP, título) compartilhado pelos dois
 * {@code @ControllerAdvice} ({@link GlobalExceptionHandler} para JSON e
 * {@link WebExceptionHandler} para as views Thymeleaf).
 *
 * <p>Antes o mesmo mapeamento vivia duplicado nos dois handlers e qualquer
 * ajuste num lado divergia silenciosamente do outro.</p>
 */
public enum TipoErro {

    NAO_ENCONTRADO(404, "Recurso não encontrado"),
    // 422 (e não 409): o contrato do app mobile espera 400/422 para falha de
    // validação de negócio (ex. estoque insuficiente) e exibe o campo mensagem.
    REGRA_NEGOCIO(422, "Regra de negócio violada"),
    CONFLITO_DADOS(409, "Conflito de dados"),
    ARQUIVO_GRANDE(413, "Arquivo grande demais");

    private final int status;
    private final String titulo;

    TipoErro(int status, String titulo) {
        this.status = status;
        this.titulo = titulo;
    }

    public int status()    { return status; }
    public String titulo() { return titulo; }

    /**
     * Mensagem segura para violações de integridade: o detalhe do banco
     * (nomes de tabelas/constraints) vai só para o log, nunca para o cliente.
     */
    public static String mensagemConflitoDados() {
        return "Os dados enviados conflitam com registros existentes "
             + "(ex.: valor duplicado ou registro em uso).";
    }
}
