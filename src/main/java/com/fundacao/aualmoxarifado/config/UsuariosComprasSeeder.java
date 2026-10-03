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

import java.util.List;

/**
 * Cria os usuários do setor de Compras/Almoxarifado da FPTO com o perfil COMPRAS:
 *
 * <ul>
 *   <li>delva.maria — MAJ QOAPM RR Delva Maria, chefe do setor (registra a decisão da Diretoria)</li>
 *   <li>sarah.luz — auxiliar</li>
 *   <li>daisy.dias — auxiliar</li>
 * </ul>
 *
 * Idempotente: só cria quem ainda não existe e nunca altera senha/perfil de usuário
 * existente. A senha inicial vem de {@code COMPRAS_SENHA_INICIAL}; sem ela (produção
 * sem a variável) o seeder apenas avisa e os usuários podem ser criados em /usuarios.
 */
@Slf4j
@Component
@Order(1)   // depois do UsuarioSeeder (admin)
@RequiredArgsConstructor
public class UsuariosComprasSeeder implements CommandLineRunner {

    record UsuarioCompras(String username, String nome) {}

    static final List<UsuarioCompras> USUARIOS = List.of(
            new UsuarioCompras("delva.maria", "Delva Maria A. Rodrigues"),
            new UsuarioCompras("sarah.luz", "Sarah Luz"),
            new UsuarioCompras("daisy.dias", "Daisy Dias"));

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${compras.usuarios.senha-inicial:}")
    private String senhaInicial;

    @Override
    public void run(String... args) {
        List<UsuarioCompras> faltando = USUARIOS.stream()
                .filter(u -> !usuarioRepository.existsByUsernameIgnoreCase(u.username()))
                .toList();
        if (faltando.isEmpty()) {
            return;
        }
        if (senhaInicial == null || senhaInicial.isBlank()) {
            log.warn("Usuários de Compras ainda não cadastrados ({}) e COMPRAS_SENHA_INICIAL não definida: "
                            + "crie-os em /usuarios com o perfil COMPRAS ou defina a variável e reinicie.",
                    faltando.stream().map(UsuarioCompras::username).toList());
            return;
        }
        String hash = passwordEncoder.encode(senhaInicial);
        for (UsuarioCompras u : faltando) {
            usuarioRepository.save(Usuario.builder()
                    .nome(u.nome())
                    .username(u.username())
                    .senhaHash(hash)
                    .perfil(PerfilUsuario.COMPRAS)
                    .ativo(true)
                    .build());
            log.info("Usuário do setor de Compras criado (login: {}, perfil COMPRAS).", u.username());
        }
    }
}
