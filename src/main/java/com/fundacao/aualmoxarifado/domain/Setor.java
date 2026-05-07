package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * RF04 - Setor (Centro de Custo).
 * Representa departamentos como RH, Financeiro, Diretoria.
 */
@Entity
@Table(name = "setor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Setor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome do setor é obrigatório")
    @Column(nullable = false, length = 120)
    private String nome;

    @NotBlank(message = "Responsável é obrigatório")
    @Column(nullable = false, length = 120)
    private String responsavel;

    /** Código do centro de custo - opcional (RF04). */
    @Column(length = 30)
    private String codigoCentroCusto;
}
