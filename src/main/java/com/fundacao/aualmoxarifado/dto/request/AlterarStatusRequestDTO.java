package com.fundacao.aualmoxarifado.dto.request;

import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/movimentacoes/{id}/status}.
 *
 * <p>{@code motivo} é obrigatório (3–255 caracteres) apenas quando
 * {@code status = REJEITADO} — validado no {@code MovimentacaoService}.</p>
 */
public record AlterarStatusRequestDTO(
        @NotNull(message = "status é obrigatório")
        StatusMovimentacao status,

        @Size(max = 255, message = "motivo deve ter no máximo 255 caracteres")
        String motivo
) {}
