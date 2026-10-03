package com.fundacao.aualmoxarifado.dto.request;

import com.fundacao.aualmoxarifado.domain.AutorizacaoDiretoria;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/compras/{id}/autorizacao}. */
public record AutorizacaoRequestDTO(
        @NotNull(message = "decisao é obrigatória (AUTORIZADA ou NAO_AUTORIZADA)")
        AutorizacaoDiretoria decisao,

        @Size(max = 500, message = "parecer deve ter no máximo 500 caracteres")
        String parecer
) {}
