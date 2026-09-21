package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Compra;
import com.fundacao.aualmoxarifado.domain.StatusCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    /** Contagem por status via COUNT(*) no banco — usada pelo dashboard. */
    long countByStatus(StatusCompra status);

    /**
     * Listagem geral paginada com o setor solicitante já carregado — a tela
     * lista.html lê {@code c.setorSolicitante.nome} por linha; sem o EntityGraph
     * seria 1 SELECT lazy por linha (assimetria com Material/Movimentacao).
     */
    @Override
    @EntityGraph(attributePaths = {"setorSolicitante"})
    Page<Compra> findAll(Pageable pageable);

    /**
     * RF15 - Fila de "Aguardando Compra", paginada.
     * A ordenação (FIFO: primeiras solicitações no topo) vem do Pageable.
     */
    @EntityGraph(attributePaths = {"setorSolicitante"})
    Page<Compra> findByStatus(StatusCompra status, Pageable pageable);

    /**
     * Busca com itens/materiais/setor já carregados: a aplicação roda com
     * {@code open-in-view=false}, então a sessão fecha antes do Thymeleaf
     * renderizar — detalhes/recebimento/baixa leem {@code compra.itens} e
     * {@code i.material.nome}, que ficariam lazy sem esta query.
     */
    @Query("""
           SELECT DISTINCT c FROM Compra c
           LEFT JOIN FETCH c.itens i
           LEFT JOIN FETCH i.material
           LEFT JOIN FETCH c.setorSolicitante
           WHERE c.id = :id
           """)
    Optional<Compra> findByIdComItens(@Param("id") Long id);

    /**
     * Hidrata {@code anexos} na MESMA instância já carregada por
     * {@link #findByIdComItens}, dentro da mesma transação — não dá para
     * unir os dois num JOIN FETCH só porque {@code itens} e {@code anexos}
     * são ambos {@code List} (bag) sem coluna de ordenação; o Hibernate
     * recusa fetch join simultâneo de duas bags (MultipleBagFetchException).
     * O identity map da sessão garante que o objeto retornado aqui é o
     * mesmo Java object de {@code findByIdComItens}, só com a coleção nova
     * preenchida.
     */
    @Query("""
           SELECT DISTINCT c FROM Compra c
           LEFT JOIN FETCH c.anexos
           WHERE c.id = :id
           """)
    Optional<Compra> findByIdComAnexos(@Param("id") Long id);

    /**
     * Inicializa itens/materiais de uma PÁGINA já paginada (mesmo motivo do
     * {@link #findByIdComItens}). A listagem (compras/lista.html) mostra a
     * contagem de itens por linha via {@code #lists.size(c.itens)} — fazer o
     * fetch join direto na consulta paginada obrigaria o Hibernate a paginar
     * em memória; esta segunda query só hidrata as linhas que já vieram com
     * LIMIT do banco.
     */
    @Query("""
           SELECT DISTINCT c FROM Compra c
           LEFT JOIN FETCH c.itens i
           LEFT JOIN FETCH i.material
           WHERE c IN :compras
           """)
    List<Compra> carregarItens(@Param("compras") Collection<Compra> compras);
}
