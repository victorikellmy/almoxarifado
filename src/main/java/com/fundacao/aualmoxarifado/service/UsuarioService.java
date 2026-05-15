package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.dto.request.TrocarSenhaRequest;
import com.fundacao.aualmoxarifado.dto.request.UsuarioRequest;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    public Usuario buscarPorLogin(String login) {
        return usuarioRepository.findByLogin(login)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", login));
    }

    @Transactional
    public Usuario criar(UsuarioRequest req) {
        if (usuarioRepository.existsByLogin(req.login().toLowerCase())) {
            throw new RegraDeNegocioException("Já existe um usuário com o login '" + req.login() + "'.");
        }
        if (req.senha() == null || req.senha().isBlank()) {
            throw new RegraDeNegocioException("Senha é obrigatória ao criar um usuário.");
        }
        Usuario u = Usuario.builder()
                .nomeCompleto(req.nomeCompleto())
                .login(req.login().toLowerCase())
                .senhaHash(passwordEncoder.encode(req.senha()))
                .perfil(req.perfil())
                .ativo(req.ativo())
                .build();
        return usuarioRepository.save(u);
    }

    @Transactional
    public Usuario atualizar(Long id, UsuarioRequest req) {
        Usuario u = buscar(id);
        u.setNomeCompleto(req.nomeCompleto());
        u.setPerfil(req.perfil());
        u.setAtivo(req.ativo());
        // Só altera senha se vier preenchida
        if (req.senha() != null && !req.senha().isBlank()) {
            u.setSenhaHash(passwordEncoder.encode(req.senha()));
        }
        return usuarioRepository.save(u);
    }

    @Transactional
    public void trocarSenha(String login, TrocarSenhaRequest req) {
        Usuario u = buscarPorLogin(login);
        if (!passwordEncoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        if (!req.novaSenha().equals(req.confirmarSenha())) {
            throw new RegraDeNegocioException("Confirmação de senha não confere.");
        }
        u.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuarioRepository.save(u);
    }
}
