package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

/**
 * RF12 - Material/Produto do catálogo do almoxarifado.
 */
@Entity
@Table(name = "material")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nome;

    /** Código/SKU - identificação única do material no catálogo (RF12). */
    @Column(unique = true, length = 50)
    private String codigoSku;

    /** Unidade de medida ex: Unidade, Caixa, Pacote, Kg (RF12). */
    @Column(length = 30)
    private String unidadeMedida;

    /** Estoque físico atual - decrementado apenas quando saída é APROVADA/ENTREGUE (RN04). */
    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer estoqueAtual = 0;

    /** Estoque mínimo - usado pela RN06 para gerar alerta de reposição. */
    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    private Integer estoqueMinimo = 0;

    /** Valor unitário, usado para identificar materiais de alto valor (RN04). */
    @Column(precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /** RF12 - Relacionamento ManyToOne com Categoria. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Helper de UI para a RN06 (não persistido). */
    @Transient
    public boolean isEstoqueAbaixoDoMinimo() {
        return estoqueAtual != null
            && estoqueMinimo != null
            && estoqueAtual <= estoqueMinimo;
    }
}
