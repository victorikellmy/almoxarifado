package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    /** Usado pelo bootstrap do admin — EXISTS no banco em vez de findAll(). */
    boolean existsByPerfilAndAtivoTrue(Perfil perfil);
}
