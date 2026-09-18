package com.fundacao.aualmoxarifado.repository.spec;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.domain.AuditoriaLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Filtros dinâmicos sobre {@link AuditoriaLog}. */
public final class AuditoriaLogSpecifications {

    private AuditoriaLogSpecifications() {}

    public static Specification<AuditoriaLog> filtrar(
            String usuario,
            AcaoAuditoria acao,
            String entidade,
            Long entidadeId,
            LocalDateTime inicio,
            LocalDateTime fim) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (usuario != null && !usuario.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("usuario")), usuario.toLowerCase()));
            }
            if (acao != null) {
                predicates.add(cb.equal(root.get("acao"), acao));
            }
            if (entidade != null && !entidade.isBlank()) {
                predicates.add(cb.equal(root.get("entidade"), entidade));
            }
            if (entidadeId != null) {
                predicates.add(cb.equal(root.get("entidadeId"), entidadeId));
            }
            if (inicio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), inicio));
            }
            if (fim != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), fim));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
