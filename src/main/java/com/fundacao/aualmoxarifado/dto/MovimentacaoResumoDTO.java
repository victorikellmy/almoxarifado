package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;

import java.time.LocalDateTime;

/**
 * Projeção leve de {@link Movimentacao} para a listagem paginada da REST API.
 * Não inclui a lista completa de itens — só a totalização e um resumo textual.
 */
public record MovimentacaoResumoDTO(
        Long id,
        LocalDateTime data,
        TipoMovimentacao tipo,
        StatusMovimentacao status,
        Long setorId,
        String setorNome,
        String retiradoPor,
        String fornecedor,
        String notaFiscal,
        int totalItens,
        int quantidadeTotal,
        String resumoItens
) {
    public static MovimentacaoResumoDTO from(Movimentacao m) {
        return new MovimentacaoResumoDTO(
                m.getId(),
                m.getData(),
                m.getTipo(),
                m.getStatus(),
                m.getSetorDestino() != null ? m.getSetorDestino().getId() : null,
                m.getSetorDestino() != null ? m.getSetorDestino().getNome() : null,
                m.getRetiradoPor(),
                m.getFornecedor(),
                m.getNotaFiscal(),
                m.getItens() != null ? m.getItens().size() : 0,
                m.getQuantidadeTotal(),
                m.getResumoItens()
        );
    }
}
