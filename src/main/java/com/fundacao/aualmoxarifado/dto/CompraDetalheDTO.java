package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalhe de {@code GET /api/compras/{id}}: campos do {@link CompraResumoDTO}
 * + dados do documento de origem (Parte/Ofício), itens e metadados dos anexos
 * (sem download pela API por enquanto).
 *
 * <p>Montar dentro de transação a partir de uma compra carregada com itens,
 * materiais, setor e anexos ({@code CompraService.buscarPorId}).</p>
 */
public record CompraDetalheDTO(
        Long id,
        LocalDateTime dataSolicitacao,
        LocalDateTime dataRecebimento,
        TipoCompra tipo,
        StatusCompra status,
        String setorSolicitante,
        String fornecedor,
        String observacao,
        BigDecimal valorEstimado,
        BigDecimal valorRealFinal,
        String numeroNotaFiscal,
        int totalItens,
        String numeroDocumento,
        String numeroSgd,
        LocalDate dataDocumento,
        String assunto,
        String solicitanteDocumento,
        AutorizacaoDiretoria autorizacaoDiretoria,
        String autorizadoPor,
        LocalDateTime autorizadoEm,
        String parecerDiretoria,
        String retiradoPor,
        String setorEntrega,
        Boolean enviarPatrimonio,
        List<ItemCompraDTO> itens,
        List<AnexoCompraDTO> anexos
) {
    public record ItemCompraDTO(
            Long materialId,
            String materialNome,
            String codigoSku,
            Integer quantidade,
            BigDecimal valorUnitario,
            BigDecimal subtotal
    ) {
        public static ItemCompraDTO from(ItemCompra i) {
            Material m = i.getMaterial();
            return new ItemCompraDTO(
                    m != null ? m.getId() : null,
                    i.getNomeItem(),
                    m != null ? m.getCodigoSku() : null,
                    i.getQuantidade(),
                    i.getValorUnitario(),
                    i.getSubtotal());
        }
    }

    public record AnexoCompraDTO(
            Long id,
            TipoAnexoCompra tipo,
            String nomeOriginal,
            Long tamanhoBytes
    ) {
        public static AnexoCompraDTO from(AnexoCompra a) {
            return new AnexoCompraDTO(a.getId(), a.getTipo(), a.getNomeOriginal(), a.getTamanhoBytes());
        }
    }

    public static CompraDetalheDTO from(Compra c) {
        CompraResumoDTO r = CompraResumoDTO.from(c);
        return new CompraDetalheDTO(
                r.id(), r.dataSolicitacao(), r.dataRecebimento(), r.tipo(), r.status(),
                r.setorSolicitante(), r.fornecedor(), r.observacao(),
                r.valorEstimado(), r.valorRealFinal(), r.numeroNotaFiscal(), r.totalItens(),
                c.getNumeroDocumento(), c.getNumeroSgd(), c.getDataDocumento(),
                c.getAssunto(), c.getSolicitanteDocumento(),
                c.getAutorizacaoDiretoria(), c.getAutorizadoPor(), c.getAutorizadoEm(), c.getParecerDiretoria(),
                c.getRetiradoPor(),
                c.getSetorEntrega() != null ? c.getSetorEntrega().getNome() : null,
                c.getEnviarPatrimonio(),
                c.getItens() == null ? List.of() : c.getItens().stream().map(ItemCompraDTO::from).toList(),
                c.getAnexos() == null ? List.of() : c.getAnexos().stream().map(AnexoCompraDTO::from).toList());
    }
}
