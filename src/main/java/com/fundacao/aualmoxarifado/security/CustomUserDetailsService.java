package com.fundacao.aualmoxarifado.security;

import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o usuário a partir da tabela {@code usuario} para o fluxo
 * de autenticação do Spring Security.
 *
 * <p>Usuários inativos ficam logicamente fora do login.</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepo;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        return usuarioRepo.findByLogin(login)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + login));
    }
}
