package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.MovimentacaoItem;

/** Linha de item no detalhe de uma movimentação ({@code GET /api/movimentacoes/{id}}). */
public record MovimentacaoItemDTO(
        Long materialId,
        String materialNome,
        String codigoSku,
        String unidadeMedida,
        Integer quantidade
) {
    public static MovimentacaoItemDTO from(MovimentacaoItem i) {
        Material m = i.getMaterial();
        return new MovimentacaoItemDTO(
                m != null ? m.getId() : null,
                m != null ? m.getNome() : null,
                m != null ? m.getCodigoSku() : null,
                m != null ? m.getUnidadeMedida() : null,
                i.getQuantidade());
    }
}
