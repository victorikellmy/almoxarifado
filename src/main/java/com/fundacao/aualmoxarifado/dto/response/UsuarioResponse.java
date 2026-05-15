package com.fundacao.aualmoxarifado.dto.response;

import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.domain.Usuario;

public record UsuarioResponse(
        Long id,
        String nomeCompleto,
        String login,
        Perfil perfil,
        boolean ativo
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNomeCompleto(), u.getLogin(),
                u.getPerfil(), u.isAtivo());
    }
}
