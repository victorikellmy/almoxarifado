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

import java.util.UUID;

/**
 * Tratamento de exceções para os controllers MVC (views Thymeleaf).
 *
 * <p>Diferente do {@link GlobalExceptionHandler} (que retorna JSON para o
 * pacote {@code controller.api}), este emite uma página de erro amigável.</p>
 *
 * <p>Atenção ao escopo: {@code @RestController} é meta-anotado com
 * {@code @Controller}, então este advice também "casa" com os controllers da
 * API. O conflito não acontece na prática porque o {@code GlobalExceptionHandler}
 * tem {@code HIGHEST_PRECEDENCE} e um fallback de {@code Exception} — toda
 * exceção da API é resolvida lá antes de chegar aqui. Este advice cobre o
 * restante (MVC), inclusive com fallback próprio para não cair na whitelabel.</p>
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class WebExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public String handleNotFound(RecursoNaoEncontradoException ex, Model model, HttpServletRequest req) {
        log.info("404 web: {} — {}", req.getRequestURI(), ex.getMessage());
        return paginaErro(model, TipoErro.NAO_ENCONTRADO, ex.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public String handleBusiness(RegraDeNegocioException ex, Model model, HttpServletRequest req) {
        log.info("{} web: {} — {}", TipoErro.REGRA_NEGOCIO.status(), req.getRequestURI(), ex.getMessage());
        return paginaErro(model, TipoErro.REGRA_NEGOCIO, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex, Model model, HttpServletRequest req) {
        // Detalhe do banco (tabelas/constraints) fica no log; o usuário recebe texto genérico.
        log.warn("Violação de integridade (web): {}", ex.getMostSpecificCause().getMessage());
        return paginaErro(model, TipoErro.CONFLITO_DADOS, TipoErro.mensagemConflitoDados());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooBig(MaxUploadSizeExceededException ex, Model model) {
        return paginaErro(model, TipoErro.ARQUIVO_GRANDE,
                "O arquivo excede o tamanho máximo permitido.");
    }

    /** Fallback MVC — evita a whitelabel page e loga com código de rastreamento. */
    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model, HttpServletRequest req) {
        String trackingId = UUID.randomUUID().toString();
        log.error("Erro não tratado (web) [tracking={}] em {}", trackingId, req.getRequestURI(), ex);
        model.addAttribute("status", 500);
        model.addAttribute("titulo", "Erro interno");
        model.addAttribute("mensagem", "Falha inesperada. Código de rastreamento: " + trackingId);
        return "erro";
    }

    private static String paginaErro(Model model, TipoErro tipo, String mensagem) {
        model.addAttribute("status", tipo.status());
        model.addAttribute("titulo", tipo.titulo());
        model.addAttribute("mensagem", mensagem);
        return "erro";
    }
}
