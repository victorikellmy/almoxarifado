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
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;

/**
 * Cria o usuário administrador inicial se ainda não existir nenhum.
 *
 * <p>Configurável por variável de ambiente / propriedade: {@code app.admin.login},
 * {@code app.admin.senha}, {@code app.admin.nome}.</p>
 *
 * <p><b>Segurança:</b> a senha padrão {@code trocar@123} só é aceita no perfil
 * {@code dev} (H2 em memória, descartável). Em qualquer outro perfil — produção
 * incluída — o boot <b>falha</b> se {@code app.admin.senha} não for fornecida
 * explicitamente, para nunca subir com credencial padrão conhecida.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner {

    /** Sentinela usada como default apenas para detectar "senha não configurada". */
    static final String SENHA_DEV_PADRAO = "trocar@123";

    @Bean
    @Order(1)
    ApplicationRunner criarAdminInicial(UsuarioRepository repo,
                                        PasswordEncoder encoder,
                                        Environment env,
                                        @Value("${app.admin.login:admin}") String login,
                                        @Value("${app.admin.senha:}") String senha,
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

            boolean devProfile = Arrays.asList(env.getActiveProfiles()).contains("dev");
            String senhaEfetiva = senha;
            if (senhaEfetiva == null || senhaEfetiva.isBlank()) {
                if (!devProfile) {
                    // Fora de dev, recusar subir sem senha explícita: uma senha
                    // padrão conhecida em produção é account takeover garantido.
                    throw new IllegalStateException(
                            "Nenhum administrador existe e 'app.admin.senha' não foi definida. "
                            + "Defina a variável de ambiente APP_ADMIN_SENHA (ou app.admin.senha) "
                            + "com uma senha forte antes de subir a aplicação fora do perfil dev.");
                }
                senhaEfetiva = SENHA_DEV_PADRAO; // só em dev
            }

            Usuario admin = Usuario.builder()
                    .nomeCompleto(nome)
                    .login(login.toLowerCase())
                    .senhaHash(encoder.encode(senhaEfetiva))
                    .perfil(Perfil.ADMINISTRADOR)
                    .ativo(true)
                    .build();
            repo.save(admin);

            log.warn("=============================================================");
            log.warn("Administrador inicial criado: login='{}'", login);
            if (devProfile && SENHA_DEV_PADRAO.equals(senhaEfetiva)) {
                log.warn("Perfil DEV: senha padrão '{}' — NÃO use em produção.", SENHA_DEV_PADRAO);
            }
            log.warn("Altere a senha imediatamente após o primeiro login!");
            log.warn("=============================================================");
        };
    }
}
