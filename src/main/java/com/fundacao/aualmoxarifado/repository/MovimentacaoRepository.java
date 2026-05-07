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
     * Agrupa as quantidades de SAIDAs já APROVADAS ou ENTREGUES por setor, dentro do período informado.
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO(
                  s.id, s.nome, s.codigoCentroCusto, SUM(m.quantidade))
           FROM Movimentacao m
           JOIN m.setorDestino s
           WHERE m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.SAIDA
             AND m.status IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.APROVADO,
                              com.fundacao.aualmoxarifado.domain.StatusMovimentacao.ENTREGUE)
             AND m.data BETWEEN :inicio AND :fim
           GROUP BY s.id, s.nome, s.codigoCentroCusto
           ORDER BY SUM(m.quantidade) DESC
           """)
    List<ConsumoSetorDTO> consumoPorSetor(@Param("inicio") LocalDateTime inicio,
                                          @Param("fim") LocalDateTime fim);
}
