package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
}
