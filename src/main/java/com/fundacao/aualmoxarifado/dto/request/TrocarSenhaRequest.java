package com.fundacao.aualmoxarifado.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarSenhaRequest(

        @NotBlank
        String senhaAtual,

        @NotBlank @Size(min = 6, max = 100, message = "Nova senha deve ter entre 6 e 100 caracteres")
        String novaSenha,

        @NotBlank
        String confirmarSenha
) {}
