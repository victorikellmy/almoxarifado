package com.fundacao.aualmoxarifado.dto.request;

import com.fundacao.aualmoxarifado.domain.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(

        @NotBlank @Size(max = 150)
        String nomeCompleto,

        @NotBlank @Size(min = 3, max = 60)
        String login,

        /** Senha em texto plano (será hasheada pelo service). Pode ficar em branco no update. */
        @Size(min = 6, max = 100)
        String senha,

        @NotNull
        Perfil perfil,

        boolean ativo
) {}
