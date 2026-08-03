package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF12 - Material/Produto do catálogo do almoxarifado.
 *
 * RF17 (categorização hierárquica): cada material pertence a UMA Subcategoria,
 *      que por sua vez pertence a UMA Área. A relação direta com Área é
 *      derivada (subcategoria.area).
 *
 * RF18 (SKU para bipagem): {@link #codigoSku} é gerado AUTOMATICAMENTE pelo
 *      {@code SkuGeneratorService} no momento do cadastro, no formato
 *      {@code AREA-SUB-NNNNN} (ex: ODO-CON-00001). NÃO deve ser editado
 *      manualmente — uma vez impresso em etiqueta, o código é a identidade
 *      física do produto.
 */
@Entity
@Table(name = "material", indexes = {
        // nome é o sort default das listagens; subcategoria_id é o filtro mais comum.
        @Index(name = "idx_material_nome",         columnList = "nome"),
        @Index(name = "idx_material_subcategoria", columnList = "subcategoria_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String nome;

    /**
     * RF18 - SKU gerado automaticamente. UNIQUE no banco — defesa final contra
     * duplicatas, mesmo se o gerador for burlado. Não atualizável após o cadastro.
     */
    @Column(unique = true, length = 50, updatable = false)
    private String codigoSku;

    @Column(length = 30)
    private String unidadeMedida;

    /** Estoque físico atual - decrementado apenas quando saída é APROVADA/ENTREGUE (RN04). */
    @NotNull
    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private Integer estoqueAtual = 0;

    /** Estoque mínimo - usado pela RN06 para gerar alerta de reposição. */
    @NotNull
    @PositiveOrZero
    @Builder.Default
    @Column(nullable = false)
    private Integer estoqueMinimo = 0;

    /** Valor unitário, usado para identificar materiais de alto valor (RN04). */
    @Column(precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /**
     * RF17 - Subcategoria do material (nível 2 da hierarquia). Obrigatória.
     * A Área (nível 1) é alcançada via {@code subcategoria.area}.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subcategoria_id", nullable = false)
    private Subcategoria subcategoria;

    // ---------- auditoria JPA (Spring Data) ----------
    // Populados automaticamente pelo AuditingEntityListener via AuditorAwareImpl.

    @CreatedDate
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @CreatedBy
    @Column(name = "criado_por", updatable = false, length = 120)
    private String criadoPor;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @LastModifiedBy
    @Column(name = "atualizado_por", length = 120)
    private String atualizadoPor;

    /** Helper de UI para a RN06 (não persistido). */
    @Transient
    public boolean isEstoqueAbaixoDoMinimo() {
        return estoqueAtual != null
            && estoqueMinimo != null
            && estoqueAtual <= estoqueMinimo;
    }
}
