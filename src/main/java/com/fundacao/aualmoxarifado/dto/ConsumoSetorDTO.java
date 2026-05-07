package com.fundacao.aualmoxarifado.dto;

/**
 * DTO usado pela query do RF10 (Relatório de Consumo por Setor).
 */
public record ConsumoSetorDTO(
        Long setorId,
        String nomeSetor,
        String codigoCentroCusto,
        Long totalConsumido
) {}
