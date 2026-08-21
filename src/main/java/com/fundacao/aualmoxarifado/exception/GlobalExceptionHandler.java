package com.fundacao.aualmoxarifado.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Conversor central de exceções em respostas HTTP uniformes (JSON).
 *
 * <p>Escopado ao pacote {@code controller.api} (REST) — os controllers MVC
 * em {@code com.fundacao.aualmoxarifado.controller} são tratados pelo
 * {@code WebExceptionHandler}.</p>
 */
@RestControllerAdvice(basePackages = "com.fundacao.aualmoxarifado.controller.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleNotFound(RecursoNaoEncontradoException ex,
                                                      HttpServletRequest req) {
        TipoErro t = TipoErro.NAO_ENCONTRADO;
        return ResponseEntity.status(t.status())
                .body(ErroResponse.of(t.status(), t.titulo(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleBusiness(RegraDeNegocioException ex,
                                                      HttpServletRequest req) {
        TipoErro t = TipoErro.REGRA_NEGOCIO;
        return ResponseEntity.status(t.status())
                .body(ErroResponse.of(t.status(), t.titulo(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(SkuFormatoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleSkuInvalido(SkuFormatoInvalidoException ex,
                                                         HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErroResponse.of(400, "SKU em formato inválido", ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Ponte para exceções "cruas" lançadas pelas camadas de service que ainda
     * não usam as exceções de domínio do projeto. {@link IllegalArgumentException}
     * indica dado de entrada inválido → 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                              HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(ErroResponse.of(400, "Requisição inválida", ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Ponte para {@link IllegalStateException} lançada pelo MovimentacaoService
     * em validações de estoque (ex. saldo insuficiente) → 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResponse> handleIllegalState(IllegalStateException ex,
                                                           HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, "Operação não permitida no estado atual",
                        ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest req) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErroResponse.of(400, "Erro de validação", "Campos inválidos", req.getRequestURI(), detalhes));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest req) {
        // Detalhe do banco (tabelas/constraints) fica no log; o cliente recebe texto genérico.
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        TipoErro t = TipoErro.CONFLITO_DADOS;
        return ResponseEntity.status(t.status())
                .body(ErroResponse.of(t.status(), t.titulo(),
                        TipoErro.mensagemConflitoDados(), req.getRequestURI()));
    }

    /**
     * Rotas inexistentes e métodos errados NÃO são erros do servidor: sem eles
     * aqui, cairiam no fallback genérico como 500 com stack trace + UUID por
     * request — sob varredura de bots, isso vira I/O de log significativo.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleNoResource(NoResourceFoundException ex,
                                                         HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "Recurso não encontrado",
                        "Rota inexistente.", req.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErroResponse.of(405, "Método não suportado",
                        ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Fallback — qualquer exceção não prevista.
     * Retorna apenas um trackingId ao cliente; stack completo fica nos logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        String trackingId = UUID.randomUUID().toString();
        log.error("Erro não tratado [tracking={}]", trackingId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of(500,
                        "Erro interno",
                        "Falha inesperada. Código de rastreamento: " + trackingId,
                        req.getRequestURI()));
    }
}
