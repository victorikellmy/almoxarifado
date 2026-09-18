package com.fundacao.aualmoxarifado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Item de uma saída registrada via app mobile (RF06 + RF18).
 * O {@code codigoSku} é o que foi lido pelo leitor de código de barras.
 */
public record ItemSaidaDTO(
        @NotBlank(message = "codigoSku é obrigatório")
        String codigoSku,

        @NotNull(message = "quantidade é obrigatória")
        @Positive(message = "quantidade deve ser maior que zero")
        Integer quantidade
) {}
