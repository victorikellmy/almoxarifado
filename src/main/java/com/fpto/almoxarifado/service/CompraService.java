package com.fpto.almoxarifado.service;

import com.fpto.almoxarifado.domain.*;
import com.fpto.almoxarifado.repository.CompraRepository;
import com.fpto.almoxarifado.repository.MaterialRepository;
import com.fpto.almoxarifado.repository.SetorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RF14/RF15/RF16 - Serviço do Módulo de Compras.
 *
 * Concentra todas as regras transacionais do fluxo:
 *   - Etapa 1 (Pré-compra): {@link #criarPreCompra}
 *   - Etapa 2 (Fila): leituras pelas listagens do controller
 *   - Etapa 3 (Recebimento/Baixa): {@link #darBaixa}
 *
 * REUSO INTENCIONAL: a baixa NÃO duplica regras de estoque. Ela delega para o
 * {@link MovimentacaoService}, que já é a fonte da verdade para entradas/saídas
 * de material — assim o saldo continua sendo movido por um único caminho e o
 * Relatório de Consumo por Setor (RF10) também enxerga o que veio por compras
 * diretas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final AnexoStorageService anexoStorageService;
    private final MovimentacaoService movimentacaoService;

    // =====================================================================
    // ETAPA 1 — PRÉ-COMPRA (lançamento)
    // =====================================================================

    /**
     * RF14 - Cria a pré-compra com seus itens e (opcionalmente) o PDF da
     * solicitação física anexado.
     *
     * Validações:
     *   - RN08: tipo DIRETA exige setor solicitante.
     *   - Pelo menos um item, com quantidade positiva, é obrigatório.
     *   - Cada item precisa apontar para um Material existente.
     *
     * @param compra      a entidade vinda do form com tipo, fornecedor, valor, etc.
     * @param itens       linhas com material/quantidade/valorUnitario
     * @param pdfSolicitacao PDF físico da solicitação (pode ser nulo)
     */
    @Transactional
    public Compra criarPreCompra(Compra compra,
                                 List<ItemCompra> itens,
                                 MultipartFile pdfSolicitacao) {

        // RN08 — setor obrigatório quando a compra é DIRETA
        if (compra.getTipo() == TipoCompra.DIRETA
                && (compra.getSetorSolicitante() == null || compra.getSetorSolicitante().getId() == null)) {
            throw new IllegalArgumentException(
                    "RN08: O setor solicitante é obrigatório para Compra Direta.");
        }

        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item à compra.");
        }

        // Resolve o setor (se informado) para garantir referência gerenciada pelo JPA.
        if (compra.getSetorSolicitante() != null && compra.getSetorSolicitante().getId() != null) {
            Setor setor = setorRepository.findById(compra.getSetorSolicitante().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Setor solicitante não encontrado."));
            compra.setSetorSolicitante(setor);
        } else {
            compra.setSetorSolicitante(null);
        }

        // Estado inicial obrigatório (RF14).
        compra.setStatus(StatusCompra.AGUARDANDO_COMPRA);
        compra.setDataSolicitacao(
                compra.getDataSolicitacao() != null ? compra.getDataSolicitacao() : LocalDateTime.now());

        BigDecimal totalEstimado = BigDecimal.ZERO;

        // Resolve Material de cada item e amarra ao agregado Compra.
        for (ItemCompra item : itens) {
            if (item.getMaterial() == null || item.getMaterial().getId() == null) {
                throw new IllegalArgumentException("Item sem material selecionado.");
            }
            if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade do item deve ser maior que zero.");
            }
            Material material = materialRepository.findById(item.getMaterial().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Material id=" + item.getMaterial().getId() + " não encontrado."));
            item.setMaterial(material);
            if (item.getValorUnitario() == null) {
                item.setValorUnitario(BigDecimal.ZERO);
            }
            totalEstimado = totalEstimado.add(item.getSubtotal());
            compra.adicionarItem(item);
        }

        // Se o usuário não digitou um valor estimado, derivamos da soma dos itens.
        if (compra.getValorEstimado() == null
                || compra.getValorEstimado().compareTo(BigDecimal.ZERO) == 0) {
            compra.setValorEstimado(totalEstimado);
        }

        // Persistimos a compra ANTES do anexo para já termos um id (usado na subpasta).
        Compra salva = compraRepository.save(compra);

        if (pdfSolicitacao != null && !pdfSolicitacao.isEmpty()) {
            anexar(salva, pdfSolicitacao, TipoAnexoCompra.SOLICITACAO);
        }

        log.info("[Compras] Pré-compra #{} criada (tipo={}, itens={}, total estimado={}).",
                salva.getId(), salva.getTipo(), salva.getItens().size(), salva.getValorEstimado());

        return salva;
    }

    // =====================================================================
    // ETAPA 3 — BAIXA / RECEBIMENTO
    // =====================================================================

    /**
     * RF15/RF16 - Baixa a compra que estava AGUARDANDO_COMPRA:
     *   - grava número da NF, valor real e PDF da Nota Fiscal;
     *   - se ESTOQUE → incrementa saldo dos materiais (RN09);
     *   - se DIRETA  → registra entrada e saída ao setor solicitante (RN10),
     *                  para que o consumo apareça nos relatórios do RF10.
     *
     * A operação é transacional: se qualquer passo falhar (NF inválida, item
     * sem material, falha de IO no anexo), nada é gravado e o status original
     * é preservado.
     */
    @Transactional
    public Compra darBaixa(Long compraId,
                           String numeroNF,
                           BigDecimal valorRealFinal,
                           MultipartFile pdfNotaFiscal) {

        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra não encontrada."));

        if (compra.getStatus() != StatusCompra.AGUARDANDO_COMPRA) {
            throw new IllegalStateException(
                    "Só é possível dar baixa em compras com status AGUARDANDO_COMPRA. Status atual: "
                            + compra.getStatus());
        }
        if (numeroNF == null || numeroNF.isBlank()) {
            throw new IllegalArgumentException("Informe o número da Nota Fiscal.");
        }
        if (valorRealFinal == null || valorRealFinal.signum() < 0) {
            throw new IllegalArgumentException("Valor real final inválido.");
        }

        compra.setNumeroNotaFiscal(numeroNF.trim());
        compra.setValorRealFinal(valorRealFinal);
        compra.setDataRecebimento(LocalDateTime.now());

        // Despacha a regra que move o estoque conforme o tipo da compra.
        if (compra.getTipo() == TipoCompra.ESTOQUE) {
            baixarComoEntradaDeEstoque(compra);
        } else {
            baixarComoRepasseDireto(compra);
        }

        // Status só vira COMPRA_REALIZADA depois que as movimentações já rodaram
        // — assim, qualquer falha da regra aborta a transação ANTES de marcar o
        // registro como concluído.
        compra.setStatus(StatusCompra.COMPRA_REALIZADA);
        Compra atualizada = compraRepository.save(compra);

        if (pdfNotaFiscal != null && !pdfNotaFiscal.isEmpty()) {
            anexar(atualizada, pdfNotaFiscal, TipoAnexoCompra.NOTA_FISCAL);
        }

        log.info("[Compras] Baixa concluída #{} (tipo={}, NF={}, valor={}).",
                atualizada.getId(), atualizada.getTipo(),
                atualizada.getNumeroNotaFiscal(), atualizada.getValorRealFinal());

        return atualizada;
    }

    /**
     * RN09 - Para Compra ESTOQUE: cada item gera uma ENTRADA no
     * MovimentacaoService, que por sua vez incrementa {@code Material.estoqueAtual}.
     */
    private void baixarComoEntradaDeEstoque(Compra compra) {
        for (ItemCompra item : compra.getItens()) {
            Movimentacao entrada = Movimentacao.builder()
                    .material(item.getMaterial())
                    .quantidade(item.getQuantidade())
                    .fornecedor(compra.getFornecedor())
                    .notaFiscal(compra.getNumeroNotaFiscal())
                    .data(LocalDateTime.now())
                    .build();
            movimentacaoService.registrarEntrada(entrada);
        }
    }

    /**
     * RN10 - Para Compra DIRETA: a mercadoria foi adquirida sob demanda
     * específica para o setor solicitante e entregue diretamente.
     *
     * O material NÃO passa pelo estoque geral do almoxarifado: nenhum saldo é
     * incrementado nem decrementado. Apenas registramos uma movimentação do
     * tipo {@link TipoMovimentacao#COMPRA_DIRETA} para cada item, contendo a
     * referência ao setor de destino, ao fornecedor e à NF — assim o histórico
     * mostra "o que compramos e enviamos para os setores" e o Relatório de
     * Consumo por Setor (RF10) consegue contabilizar o consumo.
     */
    private void baixarComoRepasseDireto(Compra compra) {
        Setor setorDestino = compra.getSetorSolicitante();
        // Defesa em profundidade: a RN08 já bloqueia esse caso na criação,
        // mas validamos novamente para o caso (raro) de o registro chegar inconsistente.
        if (setorDestino == null) {
            throw new IllegalStateException(
                    "RN10: compra direta sem setor solicitante — impossível repassar.");
        }

        for (ItemCompra item : compra.getItens()) {
            Movimentacao mov = Movimentacao.builder()
                    .material(item.getMaterial())
                    .quantidade(item.getQuantidade())
                    .setorDestino(setorDestino)
                    .fornecedor(compra.getFornecedor())
                    .notaFiscal(compra.getNumeroNotaFiscal())
                    .retiradoPor("Compra Direta #" + compra.getId())
                    .data(LocalDateTime.now())
                    .build();
            movimentacaoService.registrarCompraDireta(mov);
        }
    }

    // =====================================================================
    // ANEXOS (RF16)
    // =====================================================================

    /**
     * RF16 - Cria o {@link AnexoCompra}, gravando o PDF em disco via
     * {@link AnexoStorageService} e amarrando os metadados ao agregado.
     *
     * Exposto como público para permitir uploads avulsos (ex.: usuário esqueceu
     * de anexar o PDF da solicitação na criação e quer adicionar depois).
     */
    @Transactional
    public AnexoCompra anexar(Compra compra, MultipartFile arquivo, TipoAnexoCompra tipo) {
        var meta = anexoStorageService.salvar(arquivo, "compras/" + compra.getId());

        AnexoCompra anexo = AnexoCompra.builder()
                .tipo(tipo)
                .nomeOriginal(meta.nomeOriginal())
                .caminhoArmazenado(meta.caminhoRelativo())
                .contentType(meta.contentType())
                .tamanhoBytes(meta.tamanhoBytes())
                .dataUpload(LocalDateTime.now())
                .build();

        compra.adicionarAnexo(anexo);
        compraRepository.save(compra);
        return anexo;
    }

    // =====================================================================
    // LEITURAS
    // =====================================================================

    /** RF15 - usada pela tela "Aguardando Compra". */
    public List<Compra> listarAguardandoCompra() {
        return compraRepository.findByStatusOrderByDataSolicitacaoAsc(StatusCompra.AGUARDANDO_COMPRA);
    }

    /** Lista geral (todas as compras, mais novas primeiro). */
    public List<Compra> listarTodas() {
        return compraRepository.findAllByOrderByDataSolicitacaoDesc();
    }

    public Compra buscarPorId(Long id) {
        return compraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra id=" + id + " não encontrada."));
    }

    /** Cancela uma pré-compra que ainda não recebeu baixa. */
    @Transactional
    public Compra cancelar(Long compraId) {
        Compra compra = buscarPorId(compraId);
        if (compra.getStatus() != StatusCompra.AGUARDANDO_COMPRA) {
            throw new IllegalStateException("Só é possível cancelar compras em AGUARDANDO_COMPRA.");
        }
        compra.setStatus(StatusCompra.CANCELADA);
        return compraRepository.save(compra);
    }
}
