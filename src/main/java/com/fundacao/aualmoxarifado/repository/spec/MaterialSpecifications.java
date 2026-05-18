package com.fundacao.aualmoxarifado.repository.spec;

import com.fundacao.aualmoxarifado.domain.Material;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtros dinâmicos sobre {@link Material} para o endpoint REST paginado. */
public final class MaterialSpecifications {

    private MaterialSpecifications() {}

    /**
     * Compõe a Specification a partir dos filtros aceitos por
     * {@code GET /api/materiais}. Qualquer parâmetro {@code null} é ignorado
     * — permite combinar livremente os filtros.
     */
    public static Specification<Material> filtrar(
            String nome,
            String sku,
            Long subcategoriaId,
            Long areaId,
            Boolean emAlerta) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (nome != null && !nome.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("nome")),
                        "%" + nome.toLowerCase() + "%"));
            }

            if (sku != null && !sku.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("codigoSku")),
                        sku.trim().toUpperCase() + "%"));
            }

            if (subcategoriaId != null) {
                predicates.add(cb.equal(root.get("subcategoria").get("id"), subcategoriaId));
            }

            if (areaId != null) {
                predicates.add(cb.equal(root.get("subcategoria").get("area").get("id"), areaId));
            }

            if (Boolean.TRUE.equals(emAlerta)) {
                Expression<Integer> atual = root.get("estoqueAtual");
                Expression<Integer> minimo = root.get("estoqueMinimo");
                predicates.add(cb.lessThanOrEqualTo(atual, minimo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
