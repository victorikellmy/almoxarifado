package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * RF17 - Categoria de Nível 1 (macro). Ex.: Odontologia, Escritório, Tecnologia, Limpeza.
 *
 * A sigla é a parte da Área usada na composição do SKU dos produtos:
 *   {@code [AREA_SIGLA]-[SUB_SIGLA]-[NNNNN]} → ex. ODO-CON-00001.
 *
 * Por isso a sigla é OBRIGATÓRIA, ÚNICA e validada como letras maiúsculas/dígitos
 * (3 a 5 caracteres) — mantendo o código gerado curto e legível em etiquetas/leitor.
 */
@Entity
@Table(name = "area")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Area {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome da área é obrigatório")
    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    /** Sigla usada no prefixo do SKU. Ex: ODO, ESC, TI, LIM. */
    @NotBlank(message = "Sigla é obrigatória")
    @Size(min = 2, max = 5, message = "Sigla deve ter entre 2 e 5 caracteres")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Sigla deve conter apenas letras maiúsculas e dígitos")
    @Column(nullable = false, unique = true, length = 5)
    private String sigla;

    @Column(length = 255)
    private String descricao;
}
