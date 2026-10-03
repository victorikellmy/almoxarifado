package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.dto.UsuarioMeDTO;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/me} — perfil do usuário autenticado (Basic Auth).
 *
 * <p>Usado pelo app mobile logo após o login para exibir nome/perfil e
 * liberar as ações de administrador. Qualquer usuário autenticado pode chamar.</p>
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeApiController {

    private final UsuarioRepository usuarioRepository;

    @GetMapping
    public UsuarioMeDTO me(Authentication authentication) {
        String username = authentication.getName();
        return usuarioRepository.findByUsernameIgnoreCase(username)
                .map(UsuarioMeDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuário '" + username + "' não encontrado."));
    }
}
