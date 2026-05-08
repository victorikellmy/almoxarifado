package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubcategoriaRepository extends JpaRepository<Subcategoria, Long> {

    /** Subcategorias de uma Área — alimenta o dropdown em cascata no form de Material. */
    List<Subcategoria> findByAreaIdOrderByNomeAsc(Long areaId);

    /** Validação de unicidade composta (RN11): mesma sigla na mesma área. */
    Optional<Subcategoria> findByAreaAndSigla(Area area, String sigla);

    /**
     * RN07/protege exclusão de Área que ainda tenha subcategorias filhas.
     * (Usado pelo {@code AreaService.excluir}.)
     */
    boolean existsByArea(Area area);

    /**
     * Versão "lockada" de {@link #findById}: adquire um LOCK pessimista
     * (SELECT ... FOR UPDATE) na linha da Subcategoria. Usada exclusivamente
     * pelo {@code SkuGeneratorService} para garantir que duas requisições
     * concorrentes não leiam o mesmo {@code proximoSequencial} e gerem SKUs
     * duplicados.
     *
     * Em PostgreSQL e H2 isso traduz para SELECT ... FOR UPDATE; outras
     * threads que tentarem o mesmo SELECT esperam a transação atual terminar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Subcategoria s WHERE s.id = :id")
    Optional<Subcategoria> findByIdComLock(@Param("id") Long id);
}
