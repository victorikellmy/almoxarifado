package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.EnvioPatrimonio;
import com.fundacao.aualmoxarifado.domain.StatusEnvioPatrimonio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnvioPatrimonioRepository extends JpaRepository<EnvioPatrimonio, Long> {

    Optional<EnvioPatrimonio> findByCompraId(Long compraId);

    List<EnvioPatrimonio> findByStatusInAndTentativasLessThanOrderByCriadoEmAsc(
            Collection<StatusEnvioPatrimonio> status, int tentativasMax);

    long countByStatusIn(Collection<StatusEnvioPatrimonio> status);
}
