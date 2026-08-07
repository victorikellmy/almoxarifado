package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Login é case-insensitive: "Admin" e "admin" são a mesma pessoa. */
    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Usuario> findAllByOrderByNomeAsc();

    /** Usada pela trava que impede desativar/rebaixar o último ADMIN ativo. */
    long countByPerfilAndAtivoTrue(PerfilUsuario perfil);
}
