package com.fundacao.aualmoxarifado.dto;

import java.math.BigDecimal;

/**
 * Projeção plana usada pelos relatórios de estoque/alertas: traz só as colunas
 * necessárias numa única query (JOIN), sem hidratar entidades gerenciadas nem
 * disparar lazy loading de subcategoria/área por material.
 */
public record EstoqueLinhaDTO(
        String codigoSku,
        String nome,
        String nomeArea,
        String nomeSubcategoria,
        String unidadeMedida,
        Integer estoqueAtual,
        Integer estoqueMinimo,
        BigDecimal valorUnitario
) {}
