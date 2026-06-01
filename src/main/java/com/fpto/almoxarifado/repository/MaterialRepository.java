package com.fpto.almoxarifado.repository;

import com.fpto.almoxarifado.domain.Material;
import com.fpto.almoxarifado.domain.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    /** RN06 - retorna materiais cujo estoque atual está igual ou abaixo do mínimo. */
    @Query("""
           SELECT m FROM Material m
           WHERE m.estoqueAtual <= m.estoqueMinimo
           ORDER BY (m.estoqueAtual - m.estoqueMinimo) ASC
           """)
    List<Material> findEmAlertaDeEstoque();

    /** RN07 - usado pelo {@code SubcategoriaService.excluir} para barrar exclusões. */
    boolean existsBySubcategoria(Subcategoria subcategoria);
}
