package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    /** RF18 - lookup pelo SKU lido no scanner do app mobile. */
    Optional<Material> findByCodigoSku(String codigoSku);

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
