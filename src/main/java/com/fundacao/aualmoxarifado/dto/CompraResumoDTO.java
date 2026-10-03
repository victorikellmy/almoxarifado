package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Compra;
import com.fundacao.aualmoxarifado.domain.StatusCompra;
import com.fundacao.aualmoxarifado.domain.TipoCompra;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Item da listagem {@code GET /api/compras} (acompanhamento no app mobile).
 * {@code setorSolicitante} é o nome do setor (nulo em compra de estoque).
 */
public record CompraResumoDTO(
        Long id,
        LocalDateTime dataSolicitacao,
        LocalDateTime dataRecebimento,
        TipoCompra tipo,
        StatusCompra status,
        String setorSolicitante,
        String fornecedor,
        String observacao,
        BigDecimal valorEstimado,
        BigDecimal valorRealFinal,
        String numeroNotaFiscal,
        int totalItens,
        String numeroDocumento
) {
    public static CompraResumoDTO from(Compra c) {
        return new CompraResumoDTO(
                c.getId(),
                c.getDataSolicitacao(),
                c.getDataRecebimento(),
                c.getTipo(),
                c.getStatus(),
                c.getSetorSolicitante() != null ? c.getSetorSolicitante().getNome() : null,
                c.getFornecedor(),
                c.getObservacao(),
                c.getValorEstimado(),
                c.getValorRealFinal(),
                c.getNumeroNotaFiscal(),
                c.getItens() != null ? c.getItens().size() : 0,
                c.getNumeroDocumento());
    }
}
