package com.fundacao.aualmoxarifado.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tratamento de exceções para os controllers MVC (views Thymeleaf).
 *
 * <p>Diferente do {@link GlobalExceptionHandler} (que retorna JSON para o
 * pacote {@code controller.api}), este emite uma página de erro amigável.</p>
 *
 * <p>O filtro {@code @ControllerAdvice(annotations = Controller.class)}
 * captura todos os {@code @Controller} (MVC) — como {@code @RestController}
 * já tem advice próprio em {@link GlobalExceptionHandler}, não há conflito.</p>
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class WebExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public String handleNotFound(RecursoNaoEncontradoException ex, Model model, HttpServletRequest req) {
        log.info("404 web: {} — {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("titulo", "Recurso não encontrado");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro";
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public String handleBusiness(RegraDeNegocioException ex, Model model, HttpServletRequest req) {
        log.info("409 web: {} — {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", 409);
        model.addAttribute("titulo", "Regra de negócio violada");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex, Model model, HttpServletRequest req) {
        log.warn("Violação de integridade (web): {}", ex.getMostSpecificCause().getMessage());
        model.addAttribute("status", 409);
        model.addAttribute("titulo", "Conflito de dados");
        model.addAttribute("mensagem", "Violação de integridade: " + ex.getMostSpecificCause().getMessage());
        return "erro";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooBig(MaxUploadSizeExceededException ex, Model model) {
        model.addAttribute("status", 413);
        model.addAttribute("titulo", "Arquivo grande demais");
        model.addAttribute("mensagem", "O arquivo excede o tamanho máximo permitido.");
        return "erro";
    }
}
