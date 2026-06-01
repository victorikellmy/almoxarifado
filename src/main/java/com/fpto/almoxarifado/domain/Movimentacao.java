package com.fpto.almoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro de Entrada/Saída de material.
 *
 * RF06 - Saída exige Setor de destino e quem retirou.
 * RN03 - Setor é obrigatório para SAIDA (validado no Service).
 * RN04 - Possui status para hierarquia de aprovação.
 */
@Entity
@Table(name = "movimentacao")
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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @NotNull
    @Positive(message = "Quantidade deve ser maior que zero")
    @Column(nullable = false)
    private Integer quantidade;

    /**
     * Setor de destino - OBRIGATÓRIO para SAIDA (RN03).
     * Para ENTRADA pode ser nulo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setor_destino_id")
    private Setor setorDestino;

    /** Quem retirou o material (RF06). */
    @Column(length = 120)
    private String retiradoPor;

    /** RF13 - Fornecedor (texto livre) - usado em ENTRADAS. */
    @Column(length = 150)
    private String fornecedor;

    /** RF13 - Número da Nota Fiscal (opcional) - usado em ENTRADAS. */
    @Column(length = 50)
    private String notaFiscal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusMovimentacao status;
}
