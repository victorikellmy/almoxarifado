package com.fundacao.aualmoxarifado.repository.spec;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.MovimentacaoItem;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Filtros dinâmicos sobre {@link Movimentacao}. */
public final class MovimentacaoSpecifications {

    private MovimentacaoSpecifications() {}

    public static Specification<Movimentacao> filtrar(
            TipoMovimentacao tipo,
            StatusMovimentacao status,
            Long materialId,
            Long setorId,
            LocalDateTime inicio,
            LocalDateTime fim) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (tipo != null)    predicates.add(cb.equal(root.get("tipo"), tipo));
            if (status != null)  predicates.add(cb.equal(root.get("status"), status));
            if (setorId != null) predicates.add(cb.equal(root.get("setorDestino").get("id"), setorId));
            if (inicio != null)  predicates.add(cb.greaterThanOrEqualTo(root.get("data"), inicio));
            if (fim != null)     predicates.add(cb.lessThanOrEqualTo(root.get("data"), fim));

            // Filtro por material agora navega pelos itens (modelagem multi-item).
            if (materialId != null) {
                Join<Movimentacao, MovimentacaoItem> itens = root.join("itens");
                predicates.add(cb.equal(itens.get("material").get("id"), materialId));
                if (query != null) query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
