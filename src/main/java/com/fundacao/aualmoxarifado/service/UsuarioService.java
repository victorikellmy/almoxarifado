package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.dto.request.TrocarSenhaRequest;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Cadastro de usuários + integração com o Spring Security.
 *
 * Este service é o {@link UserDetailsService} usado no login: o Spring chama
 * {@link #loadUserByUsername} com o que foi digitado na tela e compara o hash
 * BCrypt. Usuário desativado é tratado como "disabled" — o Spring exibe a
 * mensagem de conta desativada sem revelar se a senha estava certa.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService implements UserDetailsService {

    public static final int TAMANHO_MINIMO_SENHA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // =====================================================================
    // Spring Security
    // =====================================================================

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));

        return User.builder()
                .username(u.getUsername())
                .password(u.getSenhaHash())
                .authorities(u.getPerfil().getAuthority())
                .disabled(!Boolean.TRUE.equals(u.getAtivo()))
                .build();
    }

    // =====================================================================
    // CRUD
    // =====================================================================

    public List<Usuario> listar() {
        return usuarioRepository.findAllByOrderByNomeAsc();
    }

    public Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
    }

    /**
     * Cria ou edita um usuário.
     *
     * @param usuario    dados vindos do formulário (senhaHash é IGNORADO aqui)
     * @param senhaNova  em criação: obrigatória; em edição: em branco mantém a atual
     */
    @Transactional
    public Usuario salvar(Usuario usuario, String senhaNova) {
        boolean ehNovo = usuario.getId() == null;

        usuario.setUsername(usuario.getUsername().trim().toLowerCase(Locale.ROOT));

        usuarioRepository.findByUsernameIgnoreCase(usuario.getUsername()).ifPresent(existente -> {
            if (!existente.getId().equals(usuario.getId())) {
                throw new IllegalStateException(
                        "Já existe um usuário com o login \"" + usuario.getUsername() + "\".");
            }
        });

        if (ehNovo) {
            if (senhaNova == null || senhaNova.isBlank()) {
                throw new IllegalArgumentException("Informe a senha do novo usuário.");
            }
            usuario.setAtivo(true);
        } else {
            Usuario atual = buscar(usuario.getId());
            // Campos que o formulário não controla são preservados do banco.
            usuario.setSenhaHash(atual.getSenhaHash());
            usuario.setAtivo(atual.getAtivo());
            usuario.setCriadoEm(atual.getCriadoEm());

            // Trava: o sistema nunca pode ficar sem administrador ativo.
            if (atual.getPerfil() == PerfilUsuario.ADMIN
                    && usuario.getPerfil() != PerfilUsuario.ADMIN
                    && Boolean.TRUE.equals(atual.getAtivo())
                    && usuarioRepository.countByPerfilAndAtivoTrue(PerfilUsuario.ADMIN) <= 1) {
                throw new IllegalStateException(
                        "Não é possível remover o perfil de administrador do último ADMIN ativo.");
            }
        }

        if (senhaNova != null && !senhaNova.isBlank()) {
            validarSenha(senhaNova);
            usuario.setSenhaHash(passwordEncoder.encode(senhaNova));
        }

        return usuarioRepository.save(usuario);
    }

    /** Ativa/desativa o acesso, impedindo desativar o último ADMIN ativo. */
    @Transactional
    public Usuario alternarAtivo(Long id, String usernameLogado) {
        Usuario u = buscar(id);

        boolean vaiDesativar = Boolean.TRUE.equals(u.getAtivo());

        if (vaiDesativar && u.getUsername().equalsIgnoreCase(usernameLogado)) {
            throw new IllegalStateException("Você não pode desativar o seu próprio usuário.");
        }
        if (vaiDesativar
                && u.getPerfil() == PerfilUsuario.ADMIN
                && usuarioRepository.countByPerfilAndAtivoTrue(PerfilUsuario.ADMIN) <= 1) {
            throw new IllegalStateException(
                    "Não é possível desativar o último administrador ativo do sistema.");
        }

        u.setAtivo(!Boolean.TRUE.equals(u.getAtivo()));
        return usuarioRepository.save(u);
    }

    /**
     * Troca de senha feita pelo próprio usuário logado (/minha-conta/senha).
     *
     * Diferente do {@link #salvar}, exige a senha ATUAL: sem isso, uma sessão
     * esquecida aberta permitiria a qualquer um trocar a credencial da conta.
     */
    @Transactional
    public void trocarSenha(String username, TrocarSenhaRequest req) {
        Usuario u = usuarioRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!passwordEncoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new IllegalArgumentException("Senha atual incorreta.");
        }
        if (!req.novaSenha().equals(req.confirmarSenha())) {
            throw new IllegalArgumentException("Confirmação de senha não confere.");
        }
        validarSenha(req.novaSenha());

        u.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuarioRepository.save(u);
    }

    private void validarSenha(String senha) {
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalArgumentException(
                    "A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
    }
}
