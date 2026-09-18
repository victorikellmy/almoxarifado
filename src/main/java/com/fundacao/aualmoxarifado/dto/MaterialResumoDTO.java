package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Material;

import java.math.BigDecimal;

/**
 * Projeção leve de {@link Material} para listagens da REST API — evita
 * serializar lazy-fields e relações pesadas.
 */
public record MaterialResumoDTO(
        Long id,
        String codigoSku,
        String nome,
        String unidadeMedida,
        Integer estoqueAtual,
        Integer estoqueMinimo,
        BigDecimal valorUnitario,
        Long subcategoriaId,
        String subcategoriaNome,
        Long areaId,
        String areaNome,
        boolean emAlerta
) {
    public static MaterialResumoDTO from(Material m) {
        var sub = m.getSubcategoria();
        var area = sub != null ? sub.getArea() : null;
        return new MaterialResumoDTO(
                m.getId(),
                m.getCodigoSku(),
                m.getNome(),
                m.getUnidadeMedida(),
                m.getEstoqueAtual(),
                m.getEstoqueMinimo(),
                m.getValorUnitario(),
                sub != null ? sub.getId() : null,
                sub != null ? sub.getNome() : null,
                area != null ? area.getId() : null,
                area != null ? area.getNome() : null,
                m.isEstoqueAbaixoDoMinimo()
        );
    }
}
