package com.fundacao.aualmoxarifado.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "Recurso não encontrado", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleBusiness(RegraDeNegocioException ex,
                                                      HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, "Regra de negócio violada", ex.getMessage(), req.getRequestURI()));
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
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, "Conflito de dados",
                        "Violação de integridade: " + ex.getMostSpecificCause().getMessage(),
                        req.getRequestURI()));
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
