package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RF14 - Pré-compra / Compra do almoxarifado.
 *
 * Representa o registro principal do "Módulo de Compras". Modela o ciclo completo:
 *   1) Lançamento (status AGUARDANDO_COMPRA, com valor estimado e PDF da solicitação);
 *   2) Recebimento (status COMPRA_REALIZADA, com NF, valor final e PDF da NF);
 *
 * Regras chave:
 *   - RN08: Setor solicitante é OBRIGATÓRIO quando o tipo é DIRETA.
 *   - RN09: COMPRA_REALIZADA do tipo ESTOQUE incrementa o saldo dos materiais.
 *   - RN10: COMPRA_REALIZADA do tipo DIRETA gera saída registrada para o setor solicitante.
 */
@Entity
@Table(name = "compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Data em que a pré-compra foi lançada no sistema. */
    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataSolicitacao;

    /** Data em que a baixa (recebimento) foi efetivada. Nulo enquanto AGUARDANDO_COMPRA. */
    @Column
    private LocalDateTime dataRecebimento;

    /** RF14 - tipo da compra; influencia o tratamento na baixa (RN09/RN10). */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoCompra tipo;

    /**
     * Setor que originou a solicitação física.
     * RN08: obrigatório quando tipo = DIRETA; opcional quando tipo = ESTOQUE.
     * Validado no Service (CompraService.criarPreCompra).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setor_solicitante_id")
    private Setor setorSolicitante;

    /** Texto livre para identificar quem pediu, observações etc. */
    @Column(length = 200)
    private String observacao;

    /** Fornecedor previsto/escolhido (texto livre). */
    @Column(length = 150)
    private String fornecedor;

    /** Valor estimado/total previsto na etapa de pré-compra. */
    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorEstimado = BigDecimal.ZERO;

    /** Valor final efetivamente pago — preenchido apenas na baixa. */
    @PositiveOrZero
    @Column(precision = 12, scale = 2)
    private BigDecimal valorRealFinal;

    /** Número da Nota Fiscal — preenchido apenas na baixa. */
    @Column(length = 50)
    private String numeroNotaFiscal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusCompra status;

    /**
     * Itens solicitados nesta compra. Cascade ALL + orphanRemoval para que
     * a edição da lista no formulário sincronize automaticamente com o banco.
     */
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemCompra> itens = new ArrayList<>();

    /**
     * Anexos (PDF da solicitação e da NF). Mesma cascata da lista de itens.
     */
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AnexoCompra> anexos = new ArrayList<>();

    // ----- Helpers de relacionamento (mantém os dois lados em sincronia) -----

    public void adicionarItem(ItemCompra item) {
        item.setCompra(this);
        this.itens.add(item);
    }

    public void adicionarAnexo(AnexoCompra anexo) {
        anexo.setCompra(this);
        this.anexos.add(anexo);
    }
}
