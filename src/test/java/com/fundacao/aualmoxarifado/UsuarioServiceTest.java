package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import com.fundacao.aualmoxarifado.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Autenticação e regras do cadastro de usuários.
 *
 * O {@code UsuarioSeeder} roda no boot do contexto de teste, então o admin
 * padrão (admin / Famsaudepm.) já existe aqui — os testes aproveitam isso
 * para validar o login inicial prometido.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.show-sql=false"
})
class UsuarioServiceTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void adminPadraoEhCriadoNoPrimeiroBootESenhaConfere() {
        Usuario admin = usuarioRepository.findByUsernameIgnoreCase("admin").orElseThrow();

        assertThat(admin.getPerfil()).isEqualTo(PerfilUsuario.ADMIN);
        assertThat(admin.getAtivo()).isTrue();
        // Senha combinada: Famsaudepm. — armazenada como hash BCrypt, nunca em texto puro.
        assertThat(admin.getSenhaHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("Famsaudepm.", admin.getSenhaHash())).isTrue();

        UserDetails details = usuarioService.loadUserByUsername("ADMIN");   // case-insensitive
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void criaUsuarioComSenhaHasheadaEUsernameNormalizado() {
        Usuario novo = usuarioService.salvar(Usuario.builder()
                .nome("Maria Operadora")
                .username("Maria.Silva")
                .perfil(PerfilUsuario.PADRAO)
                .build(), "segredo123");

        assertThat(novo.getUsername()).isEqualTo("maria.silva");
        assertThat(novo.getSenhaHash()).isNotEqualTo("segredo123").startsWith("$2");
        assertThat(novo.getAtivo()).isTrue();

        // Editar sem informar senha mantém o hash antigo.
        String hashAntes = novo.getSenhaHash();
        novo.setNome("Maria S. Operadora");
        Usuario editado = usuarioService.salvar(novo, "");
        assertThat(editado.getSenhaHash()).isEqualTo(hashAntes);

        usuarioRepository.deleteById(novo.getId());   // não interferir nos demais testes
    }

    @Test
    void recusaSenhaCurtaELoginDuplicado() {
        assertThatThrownBy(() -> usuarioService.salvar(Usuario.builder()
                .nome("X").username("teste.curto").perfil(PerfilUsuario.PADRAO).build(), "123"))
                .hasMessageContaining("pelo menos");

        assertThatThrownBy(() -> usuarioService.salvar(Usuario.builder()
                .nome("Admin 2").username("ADMIN").perfil(PerfilUsuario.PADRAO).build(), "senha123"))
                .hasMessageContaining("Já existe um usuário");
    }

    @Test
    void protegeOUltimoAdminAtivo() {
        Usuario admin = usuarioRepository.findByUsernameIgnoreCase("admin").orElseThrow();

        // Não pode se auto-desativar.
        assertThatThrownBy(() -> usuarioService.alternarAtivo(admin.getId(), "admin"))
                .hasMessageContaining("próprio usuário");

        // Nem outro admin pode desativar o último ADMIN ativo.
        assertThatThrownBy(() -> usuarioService.alternarAtivo(admin.getId(), "outro.admin"))
                .hasMessageContaining("último administrador");

        // Nem rebaixar o perfil dele.
        admin.setPerfil(PerfilUsuario.PADRAO);
        assertThatThrownBy(() -> usuarioService.salvar(admin, null))
                .hasMessageContaining("último ADMIN");
    }

    @Test
    void usuarioDesativadoFicaDisabledNoSpringSecurity() {
        Usuario u = usuarioService.salvar(Usuario.builder()
                .nome("Temporário").username("temp.teste").perfil(PerfilUsuario.PADRAO).build(), "senha123");

        usuarioService.alternarAtivo(u.getId(), "admin");

        assertThat(usuarioService.loadUserByUsername("temp.teste").isEnabled()).isFalse();

        usuarioRepository.deleteById(u.getId());
    }
}
