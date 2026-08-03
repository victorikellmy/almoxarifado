package com.fundacao.aualmoxarifado.security;

import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria o usuário administrador inicial se ainda não existir nenhum.
 *
 * <p>Configurável via {@code application.yml} ({@code app.admin.login} /
 * {@code app.admin.senha} / {@code app.admin.nome}).</p>
 *
 * <p>A senha padrão {@code trocar@123} só é aplicada se nenhum admin existir.
 * Em produção, SEMPRE defina a variável de ambiente correspondente.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner {

    @Bean
    @Order(1)
    ApplicationRunner criarAdminInicial(UsuarioRepository repo,
                                        PasswordEncoder encoder,
                                        @Value("${app.admin.login:admin}") String login,
                                        @Value("${app.admin.senha:trocar@123}") String senha,
                                        @Value("${app.admin.nome:Administrador}") String nome) {
        return args -> {
            boolean existeAlgumAdmin = repo.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR);
            if (existeAlgumAdmin) {
                log.debug("Administrador já existe — seed ignorado.");
                return;
            }

            if (repo.existsByLogin(login)) {
                log.warn("Login '{}' já existe mas sem perfil ADMINISTRADOR — ignorando seed.", login);
                return;
            }

            Usuario admin = Usuario.builder()
                    .nomeCompleto(nome)
                    .login(login.toLowerCase())
                    .senhaHash(encoder.encode(senha))
                    .perfil(Perfil.ADMINISTRADOR)
                    .ativo(true)
                    .build();
            repo.save(admin);

            log.warn("=============================================================");
            log.warn("Administrador inicial criado: login='{}'", login);
            log.warn("Altere a senha imediatamente após o primeiro login!");
            log.warn("=============================================================");
        };
    }
}
