package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Serviço de movimentações de estoque — modelagem <b>cabeçalho + itens</b>.
 *
 * <p>Cada operação (entrada, saída, compra direta) cria <b>uma única
 * {@link Movimentacao}</b> que agrupa vários {@link MovimentacaoItem}.
 * O estoque é movido somando a quantidade item a item.</p>
 */
@Service
@RequiredArgsConstructor
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final MaterialRepository materialRepository;

    /** Linha de entrada na criação de uma movimentação multi-item. */
    public record LinhaItem(Long materialId, Integer quantidade) {}

    public Movimentacao buscar(Long id) {
        return movimentacaoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Movimentação não encontrada."));
    }

    /** Histórico ordenado do mais recente para o mais antigo. */
    public List<Movimentacao> listarTodas() {
        return movimentacaoRepository.findAll(Sort.by(Sort.Direction.DESC, "data"));
    }

    // =========================================================================
    // SAÍDA (RF06)
    // =========================================================================

    /**
     * Registra uma SAÍDA multi-item.
     *
     * <p>RN03 — Setor obrigatório. RN04 — nasce com {@code PENDENTE_APROVACAO};
     * o estoque NÃO é debitado nesse momento (somente após aprovação).</p>
     */
    @Transactional
    public Movimentacao registrarSaida(Setor setor,
                                       String retiradoPor,
                                       String observacao,
                                       List<LinhaItem> linhas) {
        validarSetorObrigatorio(setor, "RN03: o setor de destino é obrigatório para registrar uma saída.");
        validarLinhasNaoVazias(linhas);

        Movimentacao mov = novaCabeca(TipoMovimentacao.SAIDA, setor, retiradoPor,
                                      null, null, observacao, StatusMovimentacao.PENDENTE_APROVACAO);

        for (LinhaItem ln : linhas) {
            Material material = carregarMaterial(ln.materialId());
            validarQuantidade(ln.quantidade());
            // Validação: estoque suficiente AGORA (não debita ainda, mas evita pedidos absurdos)
            if (material.getEstoqueAtual() < ln.quantidade()) {
                throw new IllegalStateException(
                        "Estoque insuficiente para \"" + material.getNome() + "\". "
                                + "Disponível: " + material.getEstoqueAtual()
                                + ", solicitado: " + ln.quantidade());
            }
            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        return movimentacaoRepository.save(mov);
    }

    // =========================================================================
    // ENTRADA (RF13 + RN05) — credita o estoque imediatamente
    // =========================================================================

    /**
     * Registra uma ENTRADA multi-item (uma NF, vários itens).
     * O estoque dos materiais é creditado na mesma transação.
     */
    @Transactional
    public Movimentacao registrarEntrada(String fornecedor,
                                         String notaFiscal,
                                         String observacao,
                                         List<LinhaItem> linhas) {
        validarLinhasNaoVazias(linhas);

        Movimentacao mov = novaCabeca(TipoMovimentacao.ENTRADA, null, null,
                                      fornecedor, notaFiscal, observacao,
                                      StatusMovimentacao.ENTREGUE);

        for (LinhaItem ln : linhas) {
            Material material = carregarMaterial(ln.materialId());
            validarQuantidade(ln.quantidade());

            material.setEstoqueAtual(material.getEstoqueAtual() + ln.quantidade());
            materialRepository.save(material);

            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        return movimentacaoRepository.save(mov);
    }

    // =========================================================================
    // COMPRA DIRETA (RF14/RN10) — não passa pelo estoque geral
    // =========================================================================

    /**
     * Registra uma COMPRA_DIRETA multi-item para um setor.
     * Não incrementa nem decrementa o estoque geral.
     */
    @Transactional
    public Movimentacao registrarCompraDireta(Setor setor,
                                              String retiradoPor,
                                              String fornecedor,
                                              String notaFiscal,
                                              String observacao,
                                              List<LinhaItem> linhas) {
        validarSetorObrigatorio(setor, "RN10: o setor de destino é obrigatório em compra direta.");
        validarLinhasNaoVazias(linhas);

        Movimentacao mov = novaCabeca(TipoMovimentacao.COMPRA_DIRETA, setor, retiradoPor,
                                      fornecedor, notaFiscal, observacao,
                                      StatusMovimentacao.ENTREGUE);

        for (LinhaItem ln : linhas) {
            Material material = carregarMaterial(ln.materialId());
            validarQuantidade(ln.quantidade());
            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        return movimentacaoRepository.save(mov);
    }

    // =========================================================================
    // Aprovação por gestor — RN04
    // =========================================================================

    /**
     * Quando o status muda para APROVADO/ENTREGUE pela primeira vez, o estoque
     * de cada item é decrementado. Idempotente: só debita na primeira transição.
     */
    @Transactional
    public Movimentacao alterarStatus(Long movimentacaoId, StatusMovimentacao novoStatus) {
        Movimentacao mov = buscar(movimentacaoId);

        StatusMovimentacao atual = mov.getStatus();
        if (atual == novoStatus) return mov;

        boolean estavaPendente   = atual == StatusMovimentacao.PENDENTE_APROVACAO;
        boolean vaiBaixarEstoque = novoStatus == StatusMovimentacao.APROVADO
                                || novoStatus == StatusMovimentacao.ENTREGUE;

        if (estavaPendente && vaiBaixarEstoque && mov.getTipo() == TipoMovimentacao.SAIDA) {
            for (MovimentacaoItem item : mov.getItens()) {
                Material material = item.getMaterial();
                if (material.getEstoqueAtual() < item.getQuantidade()) {
                    throw new IllegalStateException(
                            "Não é possível aprovar: estoque insuficiente para \""
                                    + material.getNome() + "\". Disponível: "
                                    + material.getEstoqueAtual());
                }
                material.setEstoqueAtual(material.getEstoqueAtual() - item.getQuantidade());
                materialRepository.save(material);
            }
        }

        mov.setStatus(novoStatus);
        return movimentacaoRepository.save(mov);
    }

    // =========================================================================
    // Relatório de consumo por setor (RF10)
    // =========================================================================

    public List<ConsumoSetorDTO> relatorioConsumoPorSetor(LocalDateTime inicio, LocalDateTime fim) {
        return movimentacaoRepository.consumoPorSetor(inicio, fim);
    }

    // =========================================================================
    // helpers internos
    // =========================================================================

    private Movimentacao novaCabeca(TipoMovimentacao tipo, Setor setor, String retiradoPor,
                                    String fornecedor, String notaFiscal,
                                    String observacao, StatusMovimentacao status) {
        return Movimentacao.builder()
                .tipo(tipo)
                .data(LocalDateTime.now())
                .setorDestino(setor)
                .retiradoPor(retiradoPor)
                .fornecedor(fornecedor)
                .notaFiscal(notaFiscal)
                .observacao(observacao)
                .status(status)
                .build();
    }

    private Material carregarMaterial(Long materialId) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material é obrigatório em todos os itens.");
        }
        return materialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Material id=" + materialId + " não encontrado."));
    }

    private static void validarQuantidade(Integer qtd) {
        if (qtd == null || qtd <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
    }

    private static void validarSetorObrigatorio(Setor setor, String mensagem) {
        if (setor == null || setor.getId() == null) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    private static void validarLinhasNaoVazias(List<LinhaItem> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item.");
        }
    }
}
