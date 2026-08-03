package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Compra;
import com.fundacao.aualmoxarifado.domain.StatusCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    /** Contagem por status via COUNT(*) no banco — usada pelo dashboard. */
    long countByStatus(StatusCompra status);

    /**
     * RF15 - Fila de "Aguardando Compra", paginada.
     * A ordenação (FIFO: primeiras solicitações no topo) vem do Pageable.
     */
    Page<Compra> findByStatus(StatusCompra status, Pageable pageable);
}
