package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.config.CacheConfig;
import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.dto.MovimentacaoResumoDTO;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.repository.spec.MovimentacaoSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serviço de movimentações de estoque — modelagem <b>cabeçalho + itens</b>.
 *
 * <p>Cada operação (entrada, saída, compra direta) cria <b>uma única
 * {@link Movimentacao}</b> que agrupa vários {@link MovimentacaoItem}.
 * O estoque é movido somando a quantidade item a item.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovimentacaoService {

    private final MovimentacaoRepository movimentacaoRepository;
    private final MaterialRepository materialRepository;
    private final AuditoriaService auditoriaService;

    /** Linha de entrada na criação de uma movimentação multi-item. */
    public record LinhaItem(Long materialId, Integer quantidade) {}

    public Movimentacao buscar(Long id) {
        return movimentacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", id));
    }

    /** Histórico ordenado do mais recente para o mais antigo. */
    public List<Movimentacao> listarTodas() {
        return movimentacaoRepository.findAll(Sort.by(Sort.Direction.DESC, "data"));
    }

    /**
     * Listagem paginada e filtrada — usada pela tela web e pela REST API.
     * Qualquer filtro nulo é ignorado pela Specification.
     */
    public Page<Movimentacao> listar(TipoMovimentacao tipo,
                                     StatusMovimentacao status,
                                     Long materialId,
                                     Long setorId,
                                     LocalDateTime inicio,
                                     LocalDateTime fim,
                                     Pageable pageable) {
        return movimentacaoRepository.findAll(
                MovimentacaoSpecifications.filtrar(tipo, status, materialId, setorId, inicio, fim),
                pageable);
    }

    /**
     * Variante da listagem que já devolve DTOs — compartilhada por
     * {@code MovimentacaoApiController} e {@code SaidaApiController} (antes
     * duplicavam o mesmo endpoint). O mapeamento roda DENTRO da transação
     * read-only: os acessos a itens/materiais do DTO saem em lotes IN
     * (default_batch_fetch_size) em vez de depender do open-in-view.
     */
    public Page<MovimentacaoResumoDTO> listarResumo(TipoMovimentacao tipo,
                                                    StatusMovimentacao status,
                                                    Long materialId,
                                                    Long setorId,
                                                    LocalDateTime inicio,
                                                    LocalDateTime fim,
                                                    Pageable pageable) {
        return listar(tipo, status, materialId, setorId, inicio, fim, pageable)
                .map(MovimentacaoResumoDTO::from);
    }

    // =========================================================================
    // SAÍDA (RF06)
    // =========================================================================

    /**
     * Registra uma SAÍDA multi-item pelo fluxo web (RN04).
     *
     * <p>RN03 — Setor obrigatório. RN04 — nasce com {@code PENDENTE_APROVACAO};
     * o estoque NÃO é debitado nesse momento (somente após aprovação).</p>
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_REL_MENSAL, CacheConfig.CACHE_REL_TRIMESTRAL,
            CacheConfig.CACHE_REL_ANUAL}, allEntries = true)
    public Movimentacao registrarSaida(Setor setor,
                                       String retiradoPor,
                                       String observacao,
                                       List<LinhaItem> linhas) {
        return registrarSaida(setor, retiradoPor, observacao, linhas, false);
    }

    /**
     * Registra uma SAÍDA multi-item.
     *
     * <p>RN03 — Setor obrigatório. Com {@code baixaImediata=false} (fluxo web,
     * RN04) a saída nasce {@code PENDENTE_APROVACAO} e o estoque só é debitado
     * na aprovação ({@link #alterarStatus}). Com {@code baixaImediata=true}
     * (bipagem no balcão via app — o material já está saindo fisicamente) a
     * saída nasce {@code ENTREGUE} e o estoque é debitado aqui, na mesma
     * transação: tudo ou nada.</p>
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_REL_MENSAL, CacheConfig.CACHE_REL_TRIMESTRAL,
            CacheConfig.CACHE_REL_ANUAL}, allEntries = true)
    public Movimentacao registrarSaida(Setor setor,
                                       String retiradoPor,
                                       String observacao,
                                       List<LinhaItem> linhas,
                                       boolean baixaImediata) {
        validarSetorObrigatorio(setor, "RN03: o setor de destino é obrigatório para registrar uma saída.");
        validarLinhasNaoVazias(linhas);

        Movimentacao mov = novaCabeca(TipoMovimentacao.SAIDA, setor, retiradoPor,
                                      null, null, observacao,
                                      baixaImediata ? StatusMovimentacao.ENTREGUE
                                                    : StatusMovimentacao.PENDENTE_APROVACAO);

        Map<Long, Material> materiais = carregarMateriais(linhas);
        for (LinhaItem ln : linhas) {
            Material material = materialDe(materiais, ln.materialId());
            validarQuantidade(ln.quantidade());
            // Validação: estoque suficiente AGORA. Debitando linha a linha,
            // linhas repetidas do mesmo material são validadas contra o saldo
            // já decrementado pelas anteriores.
            if (material.getEstoqueAtual() < ln.quantidade()) {
                throw new RegraDeNegocioException(
                        "Estoque insuficiente para \"" + material.getNome() + "\". "
                                + "Disponível: " + material.getEstoqueAtual()
                                + ", solicitado: " + ln.quantidade());
            }
            if (baixaImediata) {
                // Entidade gerenciada: dirty checking persiste no commit; um
                // rollback (ex. falha em item posterior) desfaz todos os débitos.
                material.setEstoqueAtual(material.getEstoqueAtual() - ln.quantidade());
            }
            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        Movimentacao salva = movimentacaoRepository.save(mov);
        auditoriaService.registrarSaida(salva);
        return salva;
    }

    // =========================================================================
    // ENTRADA (RF13 + RN05) — credita o estoque imediatamente
    // =========================================================================

    /**
     * Registra uma ENTRADA multi-item (uma NF, vários itens).
     * O estoque dos materiais é creditado na mesma transação.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_REL_MENSAL, CacheConfig.CACHE_REL_TRIMESTRAL,
            CacheConfig.CACHE_REL_ANUAL}, allEntries = true)
    public Movimentacao registrarEntrada(String fornecedor,
                                         String notaFiscal,
                                         String observacao,
                                         List<LinhaItem> linhas) {
        validarLinhasNaoVazias(linhas);

        Movimentacao mov = novaCabeca(TipoMovimentacao.ENTRADA, null, null,
                                      fornecedor, notaFiscal, observacao,
                                      StatusMovimentacao.ENTREGUE);

        Map<Long, Material> materiais = carregarMateriais(linhas);
        for (LinhaItem ln : linhas) {
            Material material = materialDe(materiais, ln.materialId());
            validarQuantidade(ln.quantidade());

            // Entidade gerenciada: o dirty checking persiste a alteração no commit,
            // sem save() (que forçaria flushes intermediários) por item.
            material.setEstoqueAtual(material.getEstoqueAtual() + ln.quantidade());

            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        Movimentacao salva = movimentacaoRepository.save(mov);
        auditoriaService.registrarEntrada(salva);
        return salva;
    }

    // =========================================================================
    // COMPRA DIRETA (RF14/RN10) — não passa pelo estoque geral
    // =========================================================================

    /**
     * Registra uma COMPRA_DIRETA multi-item para um setor.
     * Não incrementa nem decrementa o estoque geral.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_REL_MENSAL, CacheConfig.CACHE_REL_TRIMESTRAL,
            CacheConfig.CACHE_REL_ANUAL}, allEntries = true)
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

        Map<Long, Material> materiais = carregarMateriais(linhas);
        for (LinhaItem ln : linhas) {
            Material material = materialDe(materiais, ln.materialId());
            validarQuantidade(ln.quantidade());
            mov.adicionarItem(MovimentacaoItem.builder()
                    .material(material)
                    .quantidade(ln.quantidade())
                    .valorUnitario(material.getValorUnitario())
                    .build());
        }
        Movimentacao salva = movimentacaoRepository.save(mov);
        auditoriaService.registrarCompraDireta(salva);
        return salva;
    }

    // =========================================================================
    // Aprovação por gestor — RN04
    // =========================================================================

    /**
     * Quando o status muda para APROVADO/ENTREGUE pela primeira vez, o estoque
     * de cada item é decrementado. Idempotente: só debita na primeira transição.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.CACHE_REL_MENSAL, CacheConfig.CACHE_REL_TRIMESTRAL,
            CacheConfig.CACHE_REL_ANUAL}, allEntries = true)
    public Movimentacao alterarStatus(Long movimentacaoId, StatusMovimentacao novoStatus) {
        // Fetch join de itens + materiais: 1 query em vez de 1 + N proxies lazy.
        Movimentacao mov = movimentacaoRepository.findByIdComItens(movimentacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentacao", movimentacaoId));

        StatusMovimentacao atual = mov.getStatus();
        if (atual == novoStatus) return mov;

        boolean estavaPendente   = atual == StatusMovimentacao.PENDENTE_APROVACAO;
        boolean vaiBaixarEstoque = novoStatus == StatusMovimentacao.APROVADO
                                || novoStatus == StatusMovimentacao.ENTREGUE;

        if (estavaPendente && vaiBaixarEstoque && mov.getTipo() == TipoMovimentacao.SAIDA) {
            for (MovimentacaoItem item : mov.getItens()) {
                Material material = item.getMaterial();
                if (material.getEstoqueAtual() < item.getQuantidade()) {
                    throw new RegraDeNegocioException(
                            "Não é possível aprovar: estoque insuficiente para \""
                                    + material.getNome() + "\". Disponível: "
                                    + material.getEstoqueAtual());
                }
                // Entidade gerenciada: dirty checking persiste no commit.
                material.setEstoqueAtual(material.getEstoqueAtual() - item.getQuantidade());
            }
        }

        mov.setStatus(novoStatus);
        Movimentacao salva = movimentacaoRepository.save(mov);
        auditoriaService.alterarStatus(salva);
        return salva;
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

    /**
     * Pré-carrega todos os materiais das linhas numa única query (IN),
     * em vez de um findById por item — O(1) round-trips no lugar de O(N).
     */
    private Map<Long, Material> carregarMateriais(List<LinhaItem> linhas) {
        List<Long> ids = linhas.stream()
                .map(LinhaItem::materialId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private static Material materialDe(Map<Long, Material> materiais, Long materialId) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material é obrigatório em todos os itens.");
        }
        Material material = materiais.get(materialId);
        if (material == null) {
            throw new RecursoNaoEncontradoException("Material", materialId);
        }
        return material;
    }

    private static void validarQuantidade(Integer qtd) {
        if (qtd == null || qtd <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero.");
        }
    }

    private static void validarSetorObrigatorio(Setor setor, String mensagem) {
        if (setor == null || setor.getId() == null) {
            // RN03/RN10 é regra de negócio explícita — 422.
            throw new RegraDeNegocioException(mensagem);
        }
    }

    private static void validarLinhasNaoVazias(List<LinhaItem> linhas) {
        if (linhas == null || linhas.isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item.");
        }
    }
}
