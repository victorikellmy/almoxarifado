package com.fundacao.aualmoxarifado.security;

import tools.jackson.databind.ObjectMapper;
import com.fundacao.aualmoxarifado.exception.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respostas de segurança em JSON para as rotas {@code /api/**}.
 *
 * <p>Sem isto, uma requisição não autenticada cairia no comportamento default
 * do HTTP Basic (401 com corpo vazio) — o app mobile espera o mesmo
 * {@link ErroResponse} das demais falhas para extrair o campo {@code mensagem}.
 * Nunca redireciona para a página de login (302 é coisa da cadeia web).</p>
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 401 — credencial ausente ou inválida. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // WWW-Authenticate mantém o contrato do RFC 7235 e permite que clientes
        // HTTP com Authenticator reativo (ex. OkHttp) saibam qual esquema usar.
        response.setHeader("WWW-Authenticate", "Basic realm=\"api\"");
        escrever(request, response, 401, "Não autorizado",
                "Credenciais ausentes ou inválidas. Autentique-se via HTTP Basic.");
    }

    /** 403 — autenticado, mas sem o perfil exigido pela rota. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escrever(request, response, 403, "Acesso negado",
                "Seu usuário não tem permissão para executar esta operação.");
    }

    private void escrever(HttpServletRequest request, HttpServletResponse response,
                          int status, String erro, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErroResponse.of(status, erro, mensagem, request.getRequestURI()));
    }
}
