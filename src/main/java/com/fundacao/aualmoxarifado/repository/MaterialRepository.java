package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    /**
     * Autocomplete dos formulários que selecionam um material (pré-compra,
     * saída de estoque). Nunca listar todos os materiais num único
     * {@code <select>} — com o catálogo na casa dos milhares isso trava o
     * navegador ao renderizar; por isso a busca já limita o resultado.
     */
    List<Material> findTop20ByNomeContainingIgnoreCaseOrderByNomeAsc(String nome);

    /** Importação em massa: localiza o material a ATUALIZAR quando a planilha traz o SKU. */
    Optional<Material> findByCodigoSku(String codigoSku);

    /**
     * Importação em massa: quando a planilha NÃO traz SKU, o par
     * (subcategoria + nome) é a chave natural usada para decidir entre
     * criar um material novo ou atualizar o existente — evitando duplicar
     * "Papel A4" toda vez que a planilha for reenviada.
     */
    Optional<Material> findFirstBySubcategoriaAndNomeIgnoreCase(Subcategoria subcategoria, String nome);

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
