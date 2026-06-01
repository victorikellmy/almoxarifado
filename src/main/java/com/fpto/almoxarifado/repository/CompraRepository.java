package com.fpto.almoxarifado.repository;

import com.fpto.almoxarifado.domain.Compra;
import com.fpto.almoxarifado.domain.StatusCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    /**
     * RF15 - Fila de "Aguardando Compra".
     * Retorna todas as compras com determinado status, ordenadas pela data
     * de solicitação ascendente (FIFO: primeiras solicitações no topo da fila).
     */
    List<Compra> findByStatusOrderByDataSolicitacaoAsc(StatusCompra status);

    /**
     * Histórico geral, ordenado da mais nova para a mais antiga.
     */
    List<Compra> findAllByOrderByDataSolicitacaoDesc();
}
