package com.fundacao.aualmoxarifado.dto;

import java.math.BigDecimal;

/**
 * Consumo agregado por material num período — ranking de saídas/compras.
 */
public record ConsumoMaterialDTO(
        Long materialId,
        String sku,
        String nome,
        String unidadeMedida,
        Long totalQuantidade,
        BigDecimal valorTotal
) {}
