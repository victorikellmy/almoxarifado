package com.fundacao.aualmoxarifado.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope padronizado para listas paginadas da REST API.
 *
 * <p>Wrapper sobre {@link Page} do Spring Data: fixa o contrato JSON que sai
 * da API (campos previsíveis, sem o blob inteiro de Pageable/Sort) e permite
 * mapear de entidade → DTO via {@link #of(Page, Function)}.</p>
 *
 * <p>Convenção de chamada: query params {@code page} (0-based),
 * {@code size}, {@code sort} (ex. {@code sort=data,desc}). Resolvido
 * automaticamente pelo Spring quando o controller declara {@link
 * org.springframework.data.domain.Pageable Pageable}.</p>
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /** Mapeia entidade para DTO mantendo a paginação. */
    public static <E, D> PageResponse<D> of(Page<E> page, Function<E, D> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
