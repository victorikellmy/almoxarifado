package com.fundacao.aualmoxarifado.security;

import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garante a regra de segurança do seed de admin: nunca subir fora de dev com
 * senha padrão/ausente, e nunca duplicar admin.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapRunnerTest {

    @Mock UsuarioRepository repo;
    @Mock PasswordEncoder encoder;

    private final AdminBootstrapRunner config = new AdminBootstrapRunner();

    private ApplicationRunner runner(MockEnvironment env, String senha) {
        when(encoder.encode(any())).thenReturn("$2a$hash");
        return config.criarAdminInicial(repo, encoder, env, "admin", senha, "Administrador");
    }

    @Test
    void prod_semSenha_falhaOBoot_eNaoCriaAdmin() throws Exception {
        when(repo.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).thenReturn(false);
        when(repo.existsByLogin("admin")).thenReturn(false);
        MockEnvironment env = new MockEnvironment().withProperty("x", "y");
        env.setActiveProfiles("prod");

        ApplicationRunner r = runner(env, "");

        assertThatThrownBy(() -> r.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.admin.senha");

        verify(repo, never()).save(any());
    }

    @Test
    void prod_comSenhaExplicita_criaAdmin() throws Exception {
        when(repo.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).thenReturn(false);
        when(repo.existsByLogin("admin")).thenReturn(false);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        runner(env, "SenhaForte#2026").run(null);

        verify(repo).save(any(Usuario.class));
        verify(encoder).encode("SenhaForte#2026");
    }

    @Test
    void dev_semSenha_usaPadraoDescartavel_eCriaAdmin() throws Exception {
        when(repo.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).thenReturn(false);
        when(repo.existsByLogin("admin")).thenReturn(false);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        runner(env, "").run(null);

        verify(encoder).encode(AdminBootstrapRunner.SENHA_DEV_PADRAO);
        verify(repo).save(any(Usuario.class));
    }

    @Test
    void adminJaExiste_naoRecriaNadaEmNenhumPerfil() throws Exception {
        when(repo.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).thenReturn(true);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        runner(env, "").run(null);

        verify(repo, never()).save(any());
    }
}
