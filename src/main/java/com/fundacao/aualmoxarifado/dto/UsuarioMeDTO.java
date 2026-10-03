package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;

/**
 * Perfil do usuário autenticado devolvido por {@code GET /api/me}.
 * Nunca expõe o hash da senha.
 */
public record UsuarioMeDTO(
        String login,
        String nome,
        PerfilUsuario perfil
) {
    public static UsuarioMeDTO from(Usuario u) {
        return new UsuarioMeDTO(u.getUsername(), u.getNome(), u.getPerfil());
    }
}
