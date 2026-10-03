package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

/**
 * RF14 - Linha de uma {@link Compra}: identifica o material desejado, a quantidade
 * solicitada e o preço unitário estimado. Em compras do tipo ESTOQUE, a quantidade
 * dessas linhas é o que será CREDITADO ao saldo do {@link Material} na baixa
 * (RN09); em compras DIRETAS, é o que será REPASSADO ao setor (RN10).
 */
@Entity
@Table(name = "item_compra", indexes = {
        @Index(name = "idx_item_compra_compra",   columnList = "compra_id"),
        @Index(name = "idx_item_compra_material", columnList = "material_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ItemCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    /**
     * Material do catálogo de consumo. Obrigatório em compras ESTOQUE/DIRETA;
     * opcional em compras PATRIMONIAIS, cujos itens são descritos em {@link #descricao}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material;

    /** Descrição livre do item (bens patrimoniais) ou complemento ao material. */
    @Column(length = 255)
    private String descricao;

    @NotNull
    @Positive(message = "Quantidade do item deve ser maior que zero")
    @Column(nullable = false)
    private Integer quantidade;

    /** Preço unitário estimado (na pré-compra) ou efetivo (após baixa). */
    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorUnitario = BigDecimal.ZERO;

    /** Nome a exibir: material do catálogo quando houver, senão a descrição livre. */
    @Transient
    public String getNomeItem() {
        if (material != null && material.getNome() != null) return material.getNome();
        return descricao != null ? descricao : "—";
    }

    /** Subtotal do item (quantidade × valorUnitario), calculado no Service. */
    @Transient
    public BigDecimal getSubtotal() {
        if (quantidade == null || valorUnitario == null) {
            return BigDecimal.ZERO;
        }
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
