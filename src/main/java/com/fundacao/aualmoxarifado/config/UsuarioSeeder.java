package com.fundacao.aualmoxarifado.config;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstrap do usuário administrador — o ÚNICO dado que o sistema cria sozinho.
 *
 * Regras (contrato do deploy):
 *  - Condicional: só roda se a tabela de usuários estiver VAZIA. Se já existir
 *    qualquer usuário, não faz nada e loga que pulou.
 *  - Credenciais vêm de variáveis de ambiente (ADMIN_USERNAME / ADMIN_PASSWORD,
 *    mapeadas em app.admin.* no application.properties). Em produção NÃO há
 *    default para a senha: banco vazio sem ADMIN_PASSWORD derruba o boot com
 *    mensagem clara — melhor do que subir sem ninguém conseguir entrar ou com
 *    uma senha conhecida publicamente.
 *  - A senha é gravada como hash BCrypt e NUNCA aparece em log.
 *
 * Nos perfis dev/test, application-{dev,test}.properties fornecem a senha
 * padrão local (Famsaudepm.) para conveniência de desenvolvimento.
 *
 * Diferente do {@link DataSeeder} (massa de demonstração, restrito ao perfil
 * "dev"), este componente roda em QUALQUER perfil.
 */
@Slf4j
@Component
@Order(0)   // antes do DataSeeder
@RequiredArgsConstructor
public class UsuarioSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            log.info("Bootstrap do admin PULADO: já existem usuários cadastrados.");
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("""

                    ==========================================================================
                    BOOTSTRAP DO ADMINISTRADOR FALHOU — a aplicação não vai subir.

                    A tabela de usuários está vazia e a variável de ambiente
                    ADMIN_PASSWORD não foi definida. Sem ela, ninguém conseguiria
                    entrar no sistema.

                    Defina ADMIN_PASSWORD (e opcionalmente ADMIN_USERNAME, default
                    "admin") e suba o container novamente. Este bootstrap só roda
                    com o banco vazio — depois do primeiro usuário criado, as
                    variáveis deixam de ser necessárias.
                    ==========================================================================
                    """);
        }

        String username = (adminUsername == null || adminUsername.isBlank())
                ? "admin" : adminUsername.trim().toLowerCase();

        usuarioRepository.save(Usuario.builder()
                .nome("Administrador")
                .username(username)
                .senhaHash(passwordEncoder.encode(adminPassword))
                .perfil(PerfilUsuario.ADMIN)
                .ativo(true)
                .build());

        // Nunca logar a senha — só o login.
        log.info("Usuário administrador inicial criado (login: {}).", username);
    }
}
