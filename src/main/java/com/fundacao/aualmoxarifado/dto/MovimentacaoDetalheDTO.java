package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalhe completo de uma {@link Movimentacao} para a REST API:
 * todos os campos do {@link MovimentacaoResumoDTO} + observação, auditoria
 * técnica e a lista de itens.
 *
 * <p>Deve ser montado dentro de transação a partir de uma movimentação
 * carregada com {@code findByIdComItens} (fetch join de itens e materiais).</p>
 */
public record MovimentacaoDetalheDTO(
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
        String resumoItens,
        String observacao,
        String criadoPor,
        LocalDateTime criadoEm,
        String atualizadoPor,
        LocalDateTime atualizadoEm,
        List<MovimentacaoItemDTO> itens
) {
    public static MovimentacaoDetalheDTO from(Movimentacao m) {
        MovimentacaoResumoDTO r = MovimentacaoResumoDTO.from(m);
        List<MovimentacaoItemDTO> itens = m.getItens() == null ? List.of()
                : m.getItens().stream().map(MovimentacaoItemDTO::from).toList();
        return new MovimentacaoDetalheDTO(
                r.id(), r.data(), r.tipo(), r.status(), r.setorId(), r.setorNome(),
                r.retiradoPor(), r.fornecedor(), r.notaFiscal(),
                r.totalItens(), r.quantidadeTotal(), r.resumoItens(),
                m.getObservacao(),
                m.getCriadoPor(), m.getCriadoEm(),
                m.getAtualizadoPor(), m.getAtualizadoEm(),
                itens);
    }
}
