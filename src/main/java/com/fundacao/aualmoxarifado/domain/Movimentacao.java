package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de Entrada/Saída de material.
 *
 * <p>Modelagem <b>cabeçalho + itens</b>: cada movimentação é um evento
 * único (uma entrada de NF, uma saída para um setor, uma compra direta)
 * que pode conter <b>vários itens</b>, cada um com seu material e
 * quantidade ({@link MovimentacaoItem}).</p>
 *
 * <ul>
 *   <li><b>RF06</b> — Saída exige Setor de destino e quem retirou.</li>
 *   <li><b>RN03</b> — Setor é obrigatório para SAIDA (validado no Service).</li>
 *   <li><b>RN04</b> — Status para hierarquia de aprovação. O estoque dos
 *       itens só é debitado quando o status passa para APROVADO/ENTREGUE.</li>
 * </ul>
 */
@Entity
@Table(name = "movimentacao",
       indexes = {
               @Index(name = "idx_mov_data",   columnList = "data"),
               @Index(name = "idx_mov_tipo",   columnList = "tipo"),
               @Index(name = "idx_mov_status", columnList = "status"),
               @Index(name = "idx_mov_setor",  columnList = "setor_destino_id")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime data;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimentacao tipo;

    /**
     * Setor de destino — obrigatório para SAIDA (RN03)
     * e para COMPRA_DIRETA (RN10). Para ENTRADA pode ser nulo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setor_destino_id",
                foreignKey = @ForeignKey(name = "fk_mov_setor"))
    private Setor setorDestino;

    /** Quem retirou os materiais (RF06). */
    @Column(name = "retirado_por", length = 120)
    private String retiradoPor;

    /** RF13 - Fornecedor (texto livre) - usado em ENTRADAS. */
    @Column(length = 150)
    private String fornecedor;

    /** RF13 - Número da Nota Fiscal (opcional) - usado em ENTRADAS. */
    @Column(name = "nota_fiscal", length = 50)
    private String notaFiscal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusMovimentacao status;

    /** Observação livre (opcional). */
    @Column(length = 500)
    private String observacao;

    /** Itens da movimentação — pelo menos um. Cascade ALL + orphanRemoval. */
    @OneToMany(mappedBy = "movimentacao",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<MovimentacaoItem> itens = new ArrayList<>();

    // ---------- auditoria JPA (Spring Data) ----------
    // `data` (acima) é a data de NEGÓCIO da movimentação. Os campos abaixo são
    // a auditoria técnica: quem criou/alterou o registro e quando, sem mexer
    // no domínio. Populados automaticamente pelo AuditingEntityListener.

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

    // ---------- helpers ----------

    /** Adiciona um item já amarrando o lado inverso (FK). */
    public void adicionarItem(MovimentacaoItem item) {
        if (item == null) return;
        item.setMovimentacao(this);
        this.itens.add(item);
    }

    /** Soma a quantidade de todos os itens — útil para listagens. */
    @Transient
    public int getQuantidadeTotal() {
        if (itens == null) return 0;
        return itens.stream()
                .mapToInt(i -> i.getQuantidade() != null ? i.getQuantidade() : 0)
                .sum();
    }

    /** Resumo textual dos itens — "10× Caneta, 5× Papel A4 (+1)". */
    @Transient
    public String getResumoItens() {
        if (itens == null || itens.isEmpty()) return "—";
        int n = itens.size();
        if (n == 1) {
            MovimentacaoItem i = itens.get(0);
            return i.getQuantidade() + "× " + i.getMaterial().getNome();
        }
        MovimentacaoItem first = itens.get(0);
        String head = first.getQuantidade() + "× " + first.getMaterial().getNome();
        return head + "  (+" + (n - 1) + " " + (n - 1 == 1 ? "item" : "itens") + ")";
    }
}
