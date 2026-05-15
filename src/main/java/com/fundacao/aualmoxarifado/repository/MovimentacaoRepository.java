package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /**
     * RF10 - Relatório de Consumo por Setor.
     *
     * <p>Soma as quantidades dos <b>itens</b> ({@code MovimentacaoItem}) das
     * movimentações destinadas ao setor que efetivamente saíram do almoxarifado:</p>
     * <ul>
     *   <li>SAÍDAs já APROVADAS ou ENTREGUES (RN04);</li>
     *   <li>COMPRAS DIRETAS (RN10) — sempre entram como ENTREGUE.</li>
     * </ul>
     *
     * <p>Compras DIRETAS contam aqui mesmo sem passar pelo estoque geral porque,
     * do ponto de vista do setor, a mercadoria foi adquirida e consumida via
     * almoxarifado.</p>
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO(
                  s.id, s.nome, s.codigoCentroCusto, SUM(i.quantidade))
           FROM Movimentacao m
           JOIN m.setorDestino s
           JOIN m.itens i
           WHERE (
                   (m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.SAIDA
                    AND m.status IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.APROVADO,
                                     com.fundacao.aualmoxarifado.domain.StatusMovimentacao.ENTREGUE))
                OR  m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.COMPRA_DIRETA
                 )
             AND m.data BETWEEN :inicio AND :fim
           GROUP BY s.id, s.nome, s.codigoCentroCusto
           ORDER BY SUM(i.quantidade) DESC
           """)
    List<ConsumoSetorDTO> consumoPorSetor(@Param("inicio") LocalDateTime inicio,
                                          @Param("fim") LocalDateTime fim);
}
