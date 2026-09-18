package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long>,
                                             JpaSpecificationExecutor<Material> {

    /**
     * Autocomplete dos formulários que selecionam um material (pré-compra,
     * saída de estoque). Nunca listar todos os materiais num único
     * {@code <select>} — com o catálogo na casa dos milhares isso trava o
     * navegador ao renderizar; por isso a busca já limita o resultado.
     */
    List<Material> findTop20ByNomeContainingIgnoreCaseOrderByNomeAsc(String nome);

    /**
     * Lookup pelo SKU. Usado pelo scanner do app mobile (RF18) e pela importação
     * em massa, para localizar o material a ATUALIZAR quando a planilha traz o SKU.
     */
    Optional<Material> findByCodigoSku(String codigoSku);

    /** RF18 - lookup em lote: resolve todos os SKUs de uma bipagem numa única query. */
    List<Material> findByCodigoSkuIn(Collection<String> codigosSku);

    /**
     * Listagem paginada com subcategoria/área já carregadas (só ManyToOne no
     * grafo, então o LIMIT continua no banco) — evita 2 SELECTs por linha.
     */
    @Override
    @EntityGraph(attributePaths = {"subcategoria", "subcategoria.area"})
    Page<Material> findAll(Specification<Material> spec, Pageable pageable);

    /**
     * Importação em massa: quando a planilha NÃO traz SKU, o par
     * (subcategoria + nome) é a chave natural usada para decidir entre
     * criar um material novo ou atualizar o existente — evitando duplicar
     * "Papel A4" toda vez que a planilha for reenviada.
     */
    Optional<Material> findFirstBySubcategoriaAndNomeIgnoreCase(Subcategoria subcategoria, String nome);

    /** RN06 - retorna materiais cujo estoque atual está igual ou abaixo do mínimo. */
    @EntityGraph(attributePaths = {"subcategoria", "subcategoria.area"})
    @Query("""
           SELECT m FROM Material m
           WHERE m.estoqueAtual <= m.estoqueMinimo
           ORDER BY (m.estoqueAtual - m.estoqueMinimo) ASC
           """)
    List<Material> findEmAlertaDeEstoque();

    /** RN06 - contagem de materiais em alerta, para telas que só exibem o número. */
    @Query("SELECT COUNT(m) FROM Material m WHERE m.estoqueAtual <= m.estoqueMinimo")
    long countEmAlertaDeEstoque();

    /**
     * Projeção plana para o relatório de estoque: 1 query, zero entidades
     * gerenciadas (antes: findAll() + 2 SELECTs lazy por material).
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.EstoqueLinhaDTO(
                  m.codigoSku, m.nome, a.nome, s.nome, m.unidadeMedida,
                  m.estoqueAtual, m.estoqueMinimo, m.valorUnitario)
           FROM Material m
           LEFT JOIN m.subcategoria s
           LEFT JOIN s.area a
           ORDER BY m.nome
           """)
    List<com.fundacao.aualmoxarifado.dto.EstoqueLinhaDTO> linhasRelatorioEstoque();

    /** RN07 - usado pelo {@code SubcategoriaService.excluir} para barrar exclusões. */
    boolean existsBySubcategoria(Subcategoria subcategoria);
}
