package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

/**
 * Linha de item dentro de uma {@link Movimentacao}.
 *
 * <p>Uma movimentação (entrada, saída ou compra direta) pode ter vários
 * itens — cada um aponta para um {@link Material} com sua respectiva
 * quantidade. Permite registrar, por exemplo, "saída para o setor RH:
 * 10 canetas + 5 resmas de papel" como uma única operação.</p>
 */
@Entity
@Table(name = "movimentacao_item",
       indexes = {
               @Index(name = "idx_movitem_movimentacao", columnList = "movimentacao_id"),
               @Index(name = "idx_movitem_material",     columnList = "material_id")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MovimentacaoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimentacao_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_movitem_movimentacao"))
    private Movimentacao movimentacao;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_movitem_material"))
    private Material material;

    @NotNull
    @Positive(message = "Quantidade deve ser maior que zero")
    @Column(nullable = false)
    private Integer quantidade;

    /**
     * Snapshot do valor unitário do material no momento da movimentação
     * (opcional — usado em compras para cálculo de subtotal histórico).
     */
    @Column(name = "valor_unitario", precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /**
     * Subtotal (quantidade × valor unitário) calculado — usado pelos templates.
     * O Thymeleaf 3.1+ proíbe {@code new}/{@code T()} em expressões web, então
     * o cálculo precisa viver aqui (mesmo padrão de {@code ItemCompra}).
     */
    @Transient
    public BigDecimal getSubtotal() {
        if (valorUnitario == null || quantidade == null) return null;
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
