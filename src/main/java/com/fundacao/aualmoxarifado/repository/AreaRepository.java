package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    /** Busca por sigla (case-sensitive) — usada na validação de duplicidade. */
    Optional<Area> findBySigla(String sigla);

    /**
     * Buscas tolerantes usadas pela importação de planilhas: o usuário pode
     * digitar "odontologia", "Odontologia" ou a sigla "odo" na mesma coluna.
     */
    Optional<Area> findBySiglaIgnoreCase(String sigla);

    Optional<Area> findByNomeIgnoreCase(String nome);

    boolean existsBySiglaIgnoreCase(String sigla);

    /** Áreas ordenadas por nome para popular dropdowns no UI. */
    List<Area> findAllByOrderByNomeAsc();
}
