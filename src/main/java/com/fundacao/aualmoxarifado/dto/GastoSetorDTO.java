package com.fundacao.aualmoxarifado.dto;

import java.math.BigDecimal;

/**
 * Custo total atribuído a cada setor num período — soma de saídas
 * APROVADO/ENTREGUE + compras diretas, valorizadas pelo snapshot do
 * valor unitário no item da movimentação.
 */
public record GastoSetorDTO(
        Long setorId,
        String nomeSetor,
        String codigoCentroCusto,
        Long totalQuantidade,
        BigDecimal valorTotal
) {}
