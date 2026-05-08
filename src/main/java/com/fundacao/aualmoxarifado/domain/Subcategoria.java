package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * RF17 - Categoria de Nível 2. Sempre pertence a uma {@link Area}.
 * Ex.: dentro de "Odontologia" → CONSUMO, ENDO, DESCARTÁVEIS, BROCAS, DIVERSOS,
 *      ORTODONTIA, INSTRUMENTAIS, EQUIPAMENTOS.
 *
 * Para suportar a regra do SKU (RF18) com SEGURANÇA EM CONCORRÊNCIA, mantemos
 * aqui um contador {@link #proximoSequencial}: o {@code SkuGeneratorService}
 * adquire um LOCK pessimista nesta linha, lê o número, monta o SKU
 * (ex. ODO-CON-00001), incrementa e libera o lock. Assim, mesmo com requisições
 * simultâneas, dois produtos NUNCA recebem o mesmo número.
 *
 * RN11 - O par (sigla_area, sigla) deve ser único — a constraint composta
 * abaixo evita que duas subcategorias de uma mesma área tenham a mesma sigla.
 */
@Entity
@Table(
        name = "subcategoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_subcategoria_area_sigla",
                                              columnNames = {"area_id", "sigla"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subcategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome da subcategoria é obrigatório")
    @Column(nullable = false, length = 100)
    private String nome;

    /** Sigla usada como segmento do SKU. Ex: CON, END, DES, BRO, ORT, INS, EQU. */
    @NotBlank(message = "Sigla é obrigatória")
    @Size(min = 2, max = 5, message = "Sigla deve ter entre 2 e 5 caracteres")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "Sigla deve conter apenas letras maiúsculas e dígitos")
    @Column(nullable = false, length = 5)
    private String sigla;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    /**
     * Próximo número sequencial a ser usado pelo SKU desta subcategoria.
     * Inicia em 1 e é incrementado a cada cadastro de Material.
     *
     * IMPORTANTE: este campo é gerenciado EXCLUSIVAMENTE pelo
     * {@code SkuGeneratorService} sob lock pessimista. Não altere manualmente.
     */
    @NotNull
    @PositiveOrZero
    @Builder.Default
    @Column(name = "proximo_sequencial", nullable = false)
    private Integer proximoSequencial = 1;

    @Column(length = 255)
    private String descricao;
}
