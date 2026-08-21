package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;

import java.math.BigDecimal;

/**
 * Agregação de movimentações por tipo (ENTRADA / SAIDA / COMPRA_DIRETA)
 * dentro de um período. Usado pelos relatórios mensal, trimestral e anual.
 */
public record ResumoTipoDTO(
        TipoMovimentacao tipo,
        Long qtdMovimentacoes,
        Long totalItens,
        BigDecimal valorTotal
) {}
