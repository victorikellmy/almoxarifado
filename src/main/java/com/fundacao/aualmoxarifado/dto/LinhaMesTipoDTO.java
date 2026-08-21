package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;

import java.math.BigDecimal;

/**
 * Linha "crua" do agregado por (ano, mês, tipo) — usada como insumo
 * pelos relatórios mensais/trimestrais/anuais. O service faz pivot
 * dessa lista para gerar ResumoMesDTO e ResumoTipoDTO.
 */
public record LinhaMesTipoDTO(
        Integer ano,
        Integer mes,
        TipoMovimentacao tipo,
        Long qtdMovimentacoes,
        Long totalItens,
        BigDecimal valorTotal
) {}
