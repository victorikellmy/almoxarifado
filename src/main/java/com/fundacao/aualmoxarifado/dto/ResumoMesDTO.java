package com.fundacao.aualmoxarifado.dto;

import java.math.BigDecimal;

/**
 * Linha mês-a-mês de um período (usado no consolidado anual e trimestral).
 * <p>Cada linha agrega TODAS as movimentações efetivas (não-rejeitadas e
 * não-pendentes) do mês — entrada, saída e compra direta são separadas em
 * colunas próprias.</p>
 */
public record ResumoMesDTO(
        Integer ano,
        Integer mes,
        Long qtdEntradas,
        Long qtdSaidas,
        Long qtdComprasDiretas,
        Long totalItens,
        BigDecimal valorEntradas,
        BigDecimal valorSaidas,
        BigDecimal valorComprasDiretas
) {
    public BigDecimal valorTotal() {
        BigDecimal e = valorEntradas != null ? valorEntradas : BigDecimal.ZERO;
        BigDecimal s = valorSaidas != null ? valorSaidas : BigDecimal.ZERO;
        BigDecimal c = valorComprasDiretas != null ? valorComprasDiretas : BigDecimal.ZERO;
        return e.add(s).add(c);
    }

    public Long qtdTotalMovimentacoes() {
        return (qtdEntradas != null ? qtdEntradas : 0L)
             + (qtdSaidas != null ? qtdSaidas : 0L)
             + (qtdComprasDiretas != null ? qtdComprasDiretas : 0L);
    }
}
