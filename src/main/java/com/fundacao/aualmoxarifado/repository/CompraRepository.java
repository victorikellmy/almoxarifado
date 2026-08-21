package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Compra;
import com.fundacao.aualmoxarifado.domain.StatusCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
