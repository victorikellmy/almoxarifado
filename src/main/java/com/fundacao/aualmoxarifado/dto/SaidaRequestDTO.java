package com.fundacao.aualmoxarifado.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload recebido do app mobile ao finalizar uma saída por bipagem.
 *
 * RN03 - setorDestinoId é obrigatório.
 * Cada item da lista vira uma {@code Movimentacao} do tipo SAIDA no service.
 */
public record SaidaRequestDTO(
        @NotNull(message = "setorDestinoId é obrigatório (RN03)")
        Long setorDestinoId,

        String retiradoPor,

        @NotEmpty(message = "A saída precisa de ao menos um item")
        @Valid
        List<ItemSaidaDTO> itens
) {}
