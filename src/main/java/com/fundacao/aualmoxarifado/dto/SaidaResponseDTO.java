package com.fundacao.aualmoxarifado.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resposta ao app após o registro da saída.
 * Devolve os IDs das movimentações criadas para conferência.
 */
public record SaidaResponseDTO(
        LocalDateTime dataRegistro,
        Long setorDestinoId,
        String nomeSetor,
        int totalItens,
        List<Long> movimentacaoIds
) {}
