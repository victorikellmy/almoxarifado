package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.dto.ConsumoMaterialDTO;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.dto.GastoSetorDTO;
import com.fundacao.aualmoxarifado.dto.LinhaMesTipoDTO;
import com.fundacao.aualmoxarifado.dto.ResumoTipoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long>,
                                                 JpaSpecificationExecutor<Movimentacao> {

    /** Contagem por status via COUNT(*) no banco — usada pelo dashboard. */
    long countByStatus(StatusMovimentacao status);

    /**
     * Listagem paginada com setorDestino já carregado (evita 1 SELECT por linha).
     * A coleção {@code itens} fica de fora de propósito: fetch join de coleção
     * com paginação forçaria o Hibernate a paginar em memória; ela é resolvida
     * pelo {@code default_batch_fetch_size}.
     */
    @Override
    @EntityGraph(attributePaths = {"setorDestino"})
    Page<Movimentacao> findAll(Specification<Movimentacao> spec, Pageable pageable);

    /**
     * Carrega a movimentação com itens e materiais numa única query — usado
     * pela aprovação (RN04) e telas de detalhe, que percorrem os itens.
     */
    @Query("""
           SELECT DISTINCT m FROM Movimentacao m
           LEFT JOIN FETCH m.itens i
           LEFT JOIN FETCH i.material mat
           LEFT JOIN FETCH mat.subcategoria sub
           LEFT JOIN FETCH sub.area
           LEFT JOIN FETCH m.setorDestino
           WHERE m.id = :id
           """)
    Optional<Movimentacao> findByIdComItens(@Param("id") Long id);

    /**
     * Inicializa itens/materiais/setor de uma PÁGINA já paginada.
     *
     * <p>Com {@code spring.jpa.open-in-view=false} a sessão fecha antes do
     * Thymeleaf renderizar, então a listagem precisa das coleções carregadas
     * ainda dentro da transação. Fazer o fetch join direto na consulta paginada
     * obrigaria o Hibernate a paginar em memória (HHH90003004); aqui a página
     * já veio do banco com LIMIT e esta segunda query só hidrata aquelas linhas.</p>
     */
    @Query("""
           SELECT DISTINCT m FROM Movimentacao m
           LEFT JOIN FETCH m.itens i
           LEFT JOIN FETCH i.material
           LEFT JOIN FETCH m.setorDestino
           WHERE m IN :movimentacoes
           """)
    List<Movimentacao> carregarItens(@Param("movimentacoes") Collection<Movimentacao> movimentacoes);

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

    /**
     * Agregação por tipo (ENTRADA/SAIDA/COMPRA_DIRETA) num período.
     * Inclui só movimentações efetivas (não-pendentes e não-rejeitadas).
     * Usado pelo cabeçalho dos relatórios mensal/trimestral/anual.
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.ResumoTipoDTO(
                  m.tipo,
                  COUNT(DISTINCT m.id),
                  SUM(i.quantidade),
                  SUM(i.quantidade * COALESCE(i.valorUnitario, 0)))
           FROM Movimentacao m
           JOIN m.itens i
           WHERE m.data BETWEEN :inicio AND :fim
             AND m.status NOT IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.PENDENTE_APROVACAO,
                                  com.fundacao.aualmoxarifado.domain.StatusMovimentacao.REJEITADO)
           GROUP BY m.tipo
           ORDER BY m.tipo
           """)
    List<ResumoTipoDTO> resumoPorTipo(@Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim);

    /**
     * Top materiais (saídas + compras diretas) por quantidade no período.
     * Suporta o "Top 10 materiais consumidos" do relatório mensal.
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.ConsumoMaterialDTO(
                  mat.id, mat.codigoSku, mat.nome, mat.unidadeMedida,
                  SUM(i.quantidade),
                  SUM(i.quantidade * COALESCE(i.valorUnitario, 0)))
           FROM Movimentacao m
           JOIN m.itens i
           JOIN i.material mat
           WHERE m.data BETWEEN :inicio AND :fim
             AND (
                   (m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.SAIDA
                    AND m.status IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.APROVADO,
                                     com.fundacao.aualmoxarifado.domain.StatusMovimentacao.ENTREGUE))
                OR  m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.COMPRA_DIRETA
                 )
           GROUP BY mat.id, mat.codigoSku, mat.nome, mat.unidadeMedida
           ORDER BY SUM(i.quantidade) DESC
           """)
    List<ConsumoMaterialDTO> topMateriais(@Param("inicio") LocalDateTime inicio,
                                          @Param("fim") LocalDateTime fim,
                                          Pageable pageable);

    /**
     * Custo por setor no período — soma valor de saídas APROVADO/ENTREGUE
     * + compras diretas. Comparativo de custo entre setores.
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.GastoSetorDTO(
                  s.id, s.nome, s.codigoCentroCusto,
                  SUM(i.quantidade),
                  SUM(i.quantidade * COALESCE(i.valorUnitario, 0)))
           FROM Movimentacao m
           JOIN m.setorDestino s
           JOIN m.itens i
           WHERE m.data BETWEEN :inicio AND :fim
             AND (
                   (m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.SAIDA
                    AND m.status IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.APROVADO,
                                     com.fundacao.aualmoxarifado.domain.StatusMovimentacao.ENTREGUE))
                OR  m.tipo = com.fundacao.aualmoxarifado.domain.TipoMovimentacao.COMPRA_DIRETA
                 )
           GROUP BY s.id, s.nome, s.codigoCentroCusto
           ORDER BY SUM(i.quantidade * COALESCE(i.valorUnitario, 0)) DESC
           """)
    List<GastoSetorDTO> gastoPorSetor(@Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim);

    /**
     * Agregação "matéria-prima" por (ano, mês, tipo) — base para o resumo
     * mês-a-mês dos relatórios trimestrais e anuais. O service pivota.
     *
     * <p>EXTRACT(YEAR|MONTH FROM ...) é JPQL padrão e funciona em Hibernate 6
     * sobre PostgreSQL / MySQL / H2 / Oracle.</p>
     */
    @Query("""
           SELECT new com.fundacao.aualmoxarifado.dto.LinhaMesTipoDTO(
                  CAST(EXTRACT(YEAR  FROM m.data) AS integer),
                  CAST(EXTRACT(MONTH FROM m.data) AS integer),
                  m.tipo,
                  COUNT(DISTINCT m.id),
                  SUM(i.quantidade),
                  SUM(i.quantidade * COALESCE(i.valorUnitario, 0)))
           FROM Movimentacao m
           JOIN m.itens i
           WHERE m.data BETWEEN :inicio AND :fim
             AND m.status NOT IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.PENDENTE_APROVACAO,
                                  com.fundacao.aualmoxarifado.domain.StatusMovimentacao.REJEITADO)
           GROUP BY CAST(EXTRACT(YEAR FROM m.data) AS integer),
                    CAST(EXTRACT(MONTH FROM m.data) AS integer), m.tipo
           ORDER BY 1, 2, 3
           """)
    List<LinhaMesTipoDTO> agregadoPorMesTipo(@Param("inicio") LocalDateTime inicio,
                                             @Param("fim") LocalDateTime fim);

    /**
     * Lista detalhada de movimentações efetivas num intervalo — usado pelo
     * export "detalhado" do relatório anual para a contabilidade.
     */
    @Query("""
           SELECT DISTINCT m FROM Movimentacao m
           LEFT JOIN FETCH m.itens i
           LEFT JOIN FETCH i.material
           LEFT JOIN FETCH m.setorDestino
           WHERE m.data BETWEEN :inicio AND :fim
             AND m.status NOT IN (com.fundacao.aualmoxarifado.domain.StatusMovimentacao.PENDENTE_APROVACAO,
                                  com.fundacao.aualmoxarifado.domain.StatusMovimentacao.REJEITADO)
           ORDER BY m.data
           """)
    List<Movimentacao> findEfetivasNoIntervalo(@Param("inicio") LocalDateTime inicio,
                                               @Param("fim") LocalDateTime fim);
}
