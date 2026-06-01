package com.fpto.almoxarifado.service;

import com.fpto.almoxarifado.domain.*;
import com.fpto.almoxarifado.dto.ConsumoSetorDTO;
import com.fpto.almoxarifado.repository.MaterialRepository;
import com.fpto.almoxarifado.repository.MovimentacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final MaterialRepository materialRepository;

    /**
     * Registra uma SAÍDA de material.
     *
     * RN03 - Setor de destino é OBRIGATÓRIO (não pode ser nulo).
     * RN04 - A saída nasce com status PENDENTE_APROVACAO; o estoque NÃO é debitado nesse momento.
     */
    @Transactional
    public Movimentacao registrarSaida(Movimentacao mov) {

        // RN03 - bloqueia se setor for nulo/vazio
        if (mov.getSetorDestino() == null || mov.getSetorDestino().getId() == null) {
            throw new IllegalArgumentException(
                    "RN03: O setor de destino é obrigatório para registrar uma saída.");
        }

        if (mov.getQuantidade() == null || mov.getQuantidade() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }

        Material material = materialRepository.findById(mov.getMaterial().getId())
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado."));

        // Validação de estoque disponível (não debita ainda - RN04)
        if (material.getEstoqueAtual() < mov.getQuantidade()) {
            throw new IllegalStateException(
                    "Estoque insuficiente. Disponível: " + material.getEstoqueAtual());
        }

        mov.setMaterial(material);
        mov.setTipo(TipoMovimentacao.SAIDA);
        mov.setData(mov.getData() != null ? mov.getData() : LocalDateTime.now());
        // RN04 - inicia pendente; só debita estoque após aprovação
        mov.setStatus(StatusMovimentacao.PENDENTE_APROVACAO);

        return movimentacaoRepository.save(mov);
    }

    /**
     * RF13 + RN05 - Registra ENTRADA (abastecimento) de material.
     *
     * Operação transacional: ao salvar a entrada, o "estoqueAtual" do Material
     * é AUTOMATICAMENTE acrescido da quantidade recebida na MESMA transação.
     * Se qualquer passo falhar, o estoque NÃO é atualizado (rollback).
     */
    @Transactional
    public Movimentacao registrarEntrada(Movimentacao mov) {
        if (mov.getQuantidade() == null || mov.getQuantidade() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (mov.getMaterial() == null || mov.getMaterial().getId() == null) {
            throw new IllegalArgumentException("Material é obrigatório para registrar uma entrada.");
        }

        Material material = materialRepository.findById(mov.getMaterial().getId())
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado."));

        // RN05 - soma a quantidade recebida ao saldo atual.
        material.setEstoqueAtual(material.getEstoqueAtual() + mov.getQuantidade());
        materialRepository.save(material);

        mov.setMaterial(material);
        mov.setTipo(TipoMovimentacao.ENTRADA);
        mov.setData(mov.getData() != null ? mov.getData() : LocalDateTime.now());
        // Entrada já entra como ENTREGUE pois o estoque foi creditado imediatamente.
        mov.setStatus(StatusMovimentacao.ENTREGUE);
        return movimentacaoRepository.save(mov);
    }

    /**
     * RF14/RN10 - Registra uma COMPRA DIRETA: o item foi comprado sob demanda
     * para um setor específico e entregue diretamente, SEM passar pelo estoque
     * geral do almoxarifado.
     *
     * Diferenças importantes em relação a ENTRADA/SAIDA:
     *   - NÃO incrementa Material.estoqueAtual (não é ENTRADA);
     *   - NÃO decrementa Material.estoqueAtual (não é SAIDA);
     *   - Já nasce com status ENTREGUE (a mercadoria já foi repassada ao setor
     *     no ato do recebimento — não há aprovação a fazer).
     *
     * Uso: este método é chamado pelo {@code CompraService} ao baixar uma
     * Compra do tipo DIRETA. O setor de destino é obrigatório (RN08/RN10).
     */
    @Transactional
    public Movimentacao registrarCompraDireta(Movimentacao mov) {
        if (mov.getQuantidade() == null || mov.getQuantidade() <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
        if (mov.getMaterial() == null || mov.getMaterial().getId() == null) {
            throw new IllegalArgumentException("Material é obrigatório para compra direta.");
        }
        if (mov.getSetorDestino() == null || mov.getSetorDestino().getId() == null) {
            throw new IllegalArgumentException(
                    "RN10: O setor de destino é obrigatório em compra direta.");
        }

        // Resolve o material a partir do banco para obter referência gerenciada
        // (não tocamos em estoqueAtual — é o ponto da compra direta).
        Material material = materialRepository.findById(mov.getMaterial().getId())
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado."));

        mov.setMaterial(material);
        mov.setTipo(TipoMovimentacao.COMPRA_DIRETA);
        mov.setData(mov.getData() != null ? mov.getData() : LocalDateTime.now());
        // Compra direta já chega entregue — não passa por aprovação.
        mov.setStatus(StatusMovimentacao.ENTREGUE);

        return movimentacaoRepository.save(mov);
    }

    /**
     * RN04 - Aprovação por gestor.
     * Quando o status muda para APROVADO ou ENTREGUE, o estoque do material é decrementado.
     * A transição PENDENTE_APROVACAO -> APROVADO/ENTREGUE só pode ocorrer uma vez (idempotência).
     */
    @Transactional
    public Movimentacao alterarStatus(Long movimentacaoId, StatusMovimentacao novoStatus) {
        Movimentacao mov = movimentacaoRepository.findById(movimentacaoId)
                .orElseThrow(() -> new IllegalArgumentException("Movimentação não encontrada."));

        StatusMovimentacao atual = mov.getStatus();

        if (atual == novoStatus) {
            return mov;
        }

        // Só debita estoque na PRIMEIRA transição PENDENTE_APROVACAO -> APROVADO/ENTREGUE
        boolean estavaPendente = atual == StatusMovimentacao.PENDENTE_APROVACAO;
        boolean vaiBaixarEstoque = novoStatus == StatusMovimentacao.APROVADO
                                || novoStatus == StatusMovimentacao.ENTREGUE;

        if (estavaPendente && vaiBaixarEstoque && mov.getTipo() == TipoMovimentacao.SAIDA) {
            Material material = mov.getMaterial();
            if (material.getEstoqueAtual() < mov.getQuantidade()) {
                throw new IllegalStateException(
                        "Não é possível aprovar: estoque insuficiente. Disponível: "
                                + material.getEstoqueAtual());
            }
            material.setEstoqueAtual(material.getEstoqueAtual() - mov.getQuantidade());
            materialRepository.save(material);
        }

        mov.setStatus(novoStatus);
        return movimentacaoRepository.save(mov);
    }

    public List<Movimentacao> listarTodas() {
        return movimentacaoRepository.findAll();
    }

    /**
     * RF10 - Relatório de Consumo por Setor no período informado.
     */
    public List<ConsumoSetorDTO> relatorioConsumoPorSetor(LocalDateTime inicio, LocalDateTime fim) {
        return movimentacaoRepository.consumoPorSetor(inicio, fim);
    }
}
