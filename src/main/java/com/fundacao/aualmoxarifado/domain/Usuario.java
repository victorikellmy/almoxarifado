package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Usuário do sistema (autenticação e autorização).
 *
 * A senha é gravada SEMPRE com hash BCrypt — nunca em texto puro. Quem faz o
 * hash é o {@code UsuarioService.salvar}; nenhuma outra rota deve gravar este
 * campo diretamente.
 *
 * Usuários não são excluídos, apenas DESATIVADOS ({@link #ativo} = false):
 * preserva o histórico de quem operou o sistema e permite reativar depois.
 */
@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome completo exibido na interface. */
    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false, length = 120)
    private String nome;

    /** Login usado na tela de entrada. Único e sem espaços. */
    @NotBlank(message = "Usuário é obrigatório")
    @Size(min = 3, max = 40, message = "Usuário deve ter entre 3 e 40 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Usuário deve conter apenas letras, números, ponto, hífen ou underline")
    @Column(nullable = false, unique = true, length = 40)
    private String username;

    /**
     * Hash BCrypt da senha — nunca a senha em si. Sem validação Bean aqui de
     * propósito: o formulário não envia este campo (o binding dele é bloqueado
     * no controller) e quem garante o preenchimento é o {@code UsuarioService}.
     */
    @Column(nullable = false, length = 100)
    private String senhaHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PerfilUsuario perfil = PerfilUsuario.PADRAO;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    /**
     * Rede de segurança para instâncias criadas via {@code new Usuario()}
     * (binding do formulário): com {@code @Builder.Default}, o Lombok só aplica
     * os valores iniciais no builder — no construtor vazio eles ficam nulos.
     */
    @PrePersist
    void garantirDefaults() {
        if (ativo == null) {
            ativo = true;
        }
        if (perfil == null) {
            perfil = PerfilUsuario.PADRAO;
        }
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
