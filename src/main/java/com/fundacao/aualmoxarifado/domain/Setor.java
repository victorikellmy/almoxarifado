package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * RF04 - Setor (Centro de Custo).
 * Representa departamentos como RH, Financeiro, Diretoria.
 *
 * Cada setor pertence a uma {@link Organizacao} (FPTO ou FA-Saúde): as duas
 * têm setores homônimos, então telas e relatórios devem exibir
 * {@link #getNomeCompleto()} quando o setor aparece fora de contexto.
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

    /** Responsável pelo setor — opcional (setores são cadastrados sem pessoas). */
    @Column(length = 120)
    private String responsavel;

    /** Código do centro de custo - opcional (RF04). */
    @Column(length = 30)
    private String codigoCentroCusto;

    @NotNull(message = "Organização é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Organizacao organizacao = Organizacao.FPTO;

    /** "Financeiro — FA-Saúde": nome sem ambiguidade entre as organizações. */
    @Transient
    public String getNomeCompleto() {
        return organizacao == null ? nome : nome + " — " + organizacao.getSigla();
    }
}
