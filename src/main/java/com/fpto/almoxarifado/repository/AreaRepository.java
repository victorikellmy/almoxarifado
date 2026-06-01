package com.fpto.almoxarifado.repository;

import com.fpto.almoxarifado.domain.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends JpaRepository<Area, Long> {

    /** Busca por sigla (case-sensitive) — usada na validação de duplicidade. */
    Optional<Area> findBySigla(String sigla);

    /** Áreas ordenadas por nome para popular dropdowns no UI. */
    List<Area> findAllByOrderByNomeAsc();
}
