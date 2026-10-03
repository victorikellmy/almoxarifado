package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.dto.CompraDetalheDTO;
import com.fundacao.aualmoxarifado.dto.CompraResumoDTO;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.CompraRepository;
import com.fundacao.aualmoxarifado.service.integracao.PatrimonioIntegracaoService;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@Transactional(readOnly = true)
public class CompraService {

    private final CompraRepository compraRepository;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final AnexoStorageService anexoStorageService;
    private final MovimentacaoService movimentacaoService;
    private final PatrimonioIntegracaoService patrimonioIntegracaoService;

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
        return criarPreCompra(compra, itens, pdfSolicitacao, null);
    }

    /**
     * Variante usada pela tela "Nova pré-compra" com leitura automática da Parte:
     * o PDF já foi enviado antes (para ser lido) e fica na sessão como
     * {@link LeituraParteCompraService.ArquivoLido}; aqui ele vira o anexo SOLICITACAO
     * quando nenhum novo arquivo foi escolhido no submit.
     */
    @Transactional
    public Compra criarPreCompra(Compra compra,
                                 List<ItemCompra> itens,
                                 MultipartFile pdfSolicitacao,
                                 LeituraParteCompraService.ArquivoLido pdfLido) {

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

        // Toda pré-compra nasce aguardando a decisão da Diretoria.
        compra.setAutorizacaoDiretoria(AutorizacaoDiretoria.PENDENTE);
        compra.setAutorizadoPor(null);
        compra.setAutorizadoEm(null);

        boolean patrimonial = compra.getTipo() == TipoCompra.PATRIMONIAL;

        // Resolve Material de cada item e amarra ao agregado Compra.
        for (ItemCompra item : itens) {
            boolean temMaterial = item.getMaterial() != null && item.getMaterial().getId() != null;
            boolean temDescricao = item.getDescricao() != null && !item.getDescricao().isBlank();
            if (!temMaterial && !(patrimonial && temDescricao)) {
                throw new IllegalArgumentException(patrimonial
                        ? "Descreva o bem em cada item da compra patrimonial."
                        : "Item sem material selecionado.");
            }
            if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade do item deve ser maior que zero.");
            }
            if (temMaterial) {
                Material material = materialRepository.findById(item.getMaterial().getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Material id=" + item.getMaterial().getId() + " não encontrado."));
                item.setMaterial(material);
            } else {
                item.setMaterial(null);
            }
            if (temDescricao) {
                item.setDescricao(item.getDescricao().trim());
            }
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
        } else if (pdfLido != null) {
            anexar(salva, pdfLido.conteudo(), pdfLido.nomeOriginal(), pdfLido.contentType(),
                    TipoAnexoCompra.SOLICITACAO);
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
        return darBaixa(compraId, numeroNF, valorRealFinal, pdfNotaFiscal, new DadosRecebimento(null, null, false, null));
    }

    /**
     * Dados complementares informados na tela de recebimento.
     *
     * @param retiradoPor       quem retirou a mercadoria (obrigatório quando vai para o Patrimônio)
     * @param setorEntregaId    setor/unidade que recebeu (default: setor solicitante)
     * @param enviarPatrimonio  "será patrimoniado": cria o envio para o sistema de Patrimônio
     * @param usuario           login de quem está dando a baixa
     */
    public record DadosRecebimento(String retiradoPor, Long setorEntregaId, boolean enviarPatrimonio, String usuario) {}

    /**
     * Variante completa da baixa (RF15/RF16 + fluxo da Diretoria + Patrimônio):
     * exige a compra AUTORIZADA pela Diretoria; compras PATRIMONIAIS não mexem em
     * estoque nem consumo; com "será patrimoniado" (obrigatório para PATRIMONIAL,
     * opcional para DIRETA) registra o envio ao Patrimônio na mesma transação —
     * o POST HTTP acontece depois, fora dela ({@code PatrimonioIntegracaoService}).
     */
    @Transactional
    public Compra darBaixa(Long compraId,
                           String numeroNF,
                           BigDecimal valorRealFinal,
                           MultipartFile pdfNotaFiscal,
                           DadosRecebimento dados) {

        Compra compra = compraRepository.findById(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra não encontrada."));

        if (compra.getStatus() != StatusCompra.AGUARDANDO_COMPRA) {
            throw new IllegalStateException(
                    "Só é possível dar baixa em compras com status AGUARDANDO_COMPRA. Status atual: "
                            + compra.getStatus());
        }
        if (compra.getAutorizacaoDiretoria() != AutorizacaoDiretoria.AUTORIZADA) {
            throw new RegraDeNegocioException(
                    "A compra #" + compra.getId() + " ainda não foi autorizada pela Diretoria. "
                            + "Registre a decisão da Diretoria antes de dar baixa.");
        }
        if (numeroNF == null || numeroNF.isBlank()) {
            throw new IllegalArgumentException("Informe o número da Nota Fiscal.");
        }
        if (valorRealFinal == null || valorRealFinal.signum() < 0) {
            throw new IllegalArgumentException("Valor real final inválido.");
        }

        boolean patrimonial = compra.getTipo() == TipoCompra.PATRIMONIAL;
        boolean enviarPatrimonio = patrimonial || (dados != null && dados.enviarPatrimonio());
        if (enviarPatrimonio && compra.getTipo() == TipoCompra.ESTOQUE) {
            throw new RegraDeNegocioException("Compra de ESTOQUE não pode ser enviada ao Patrimônio.");
        }

        // Destino e retirada
        Setor setorEntrega = compra.getSetorSolicitante();
        if (dados != null && dados.setorEntregaId() != null) {
            setorEntrega = setorRepository.findById(dados.setorEntregaId())
                    .orElseThrow(() -> new IllegalArgumentException("Setor de entrega não encontrado."));
        }
        String retiradoPor = dados == null || dados.retiradoPor() == null ? null : dados.retiradoPor().trim();
        if (enviarPatrimonio) {
            if (retiradoPor == null || retiradoPor.isBlank()) {
                throw new RegraDeNegocioException("Informe quem retirou o bem para enviá-lo ao Patrimônio.");
            }
            if (setorEntrega == null) {
                throw new RegraDeNegocioException("Informe o setor/unidade que recebeu o bem para enviá-lo ao Patrimônio.");
            }
        }

        compra.setNumeroNotaFiscal(numeroNF.trim());
        compra.setValorRealFinal(valorRealFinal);
        compra.setDataRecebimento(LocalDateTime.now());
        compra.setRetiradoPor(retiradoPor == null || retiradoPor.isBlank() ? null : retiradoPor);
        compra.setSetorEntrega(setorEntrega);
        compra.setEnviarPatrimonio(enviarPatrimonio);
        compra.setRecebidoPor(dados != null ? dados.usuario() : null);

        // Despacha a regra que move o estoque conforme o tipo da compra.
        if (compra.getTipo() == TipoCompra.ESTOQUE) {
            baixarComoEntradaDeEstoque(compra);
        } else if (compra.getTipo() == TipoCompra.DIRETA) {
            baixarComoRepasseDireto(compra);
        }
        // PATRIMONIAL: bem permanente — não entra no estoque nem no consumo do setor;
        // o registro de destino fica na compra e segue para o Patrimônio.

        // Status só vira COMPRA_REALIZADA depois que as movimentações já rodaram
        // — assim, qualquer falha da regra aborta a transação ANTES de marcar o
        // registro como concluído.
        compra.setStatus(StatusCompra.COMPRA_REALIZADA);
        Compra atualizada = compraRepository.save(compra);

        if (pdfNotaFiscal != null && !pdfNotaFiscal.isEmpty()) {
            anexar(atualizada, pdfNotaFiscal, TipoAnexoCompra.NOTA_FISCAL);
        }

        if (enviarPatrimonio) {
            patrimonioIntegracaoService.registrarEnvio(atualizada);
        }

        log.info("[Compras] Baixa concluída #{} (tipo={}, NF={}, valor={}, patrimonio={}).",
                atualizada.getId(), atualizada.getTipo(),
                atualizada.getNumeroNotaFiscal(), atualizada.getValorRealFinal(), enviarPatrimonio);

        return atualizada;
    }

    // =====================================================================
    // ETAPA 2 — DECISÃO DA DIRETORIA (registrada pelo setor de Compras)
    // =====================================================================

    /**
     * Registra no sistema se a Diretoria autorizou ou não a pré-compra.
     * NÃO autorizada → a pré-compra é cancelada, guardando o parecer.
     */
    @Transactional
    public Compra registrarAutorizacao(Long compraId, AutorizacaoDiretoria decisao, String parecer, String usuario) {
        if (decisao == null || decisao == AutorizacaoDiretoria.PENDENTE) {
            throw new IllegalArgumentException("Informe a decisão da Diretoria (autorizada ou não autorizada).");
        }
        Compra compra = buscarPorId(compraId);
        if (compra.getStatus() != StatusCompra.AGUARDANDO_COMPRA) {
            throw new RegraDeNegocioException("A decisão da Diretoria só pode ser registrada em pré-compras aguardando compra "
                    + "(status atual: " + compra.getStatus() + ").");
        }
        String p = parecer == null ? null : parecer.trim();
        if (decisao == AutorizacaoDiretoria.NAO_AUTORIZADA && (p == null || p.length() < 3)) {
            throw new RegraDeNegocioException("Informe o motivo/parecer da Diretoria para a não autorização.");
        }
        compra.setAutorizacaoDiretoria(decisao);
        compra.setAutorizadoPor(usuario);
        compra.setAutorizadoEm(LocalDateTime.now());
        compra.setParecerDiretoria(p == null || p.isBlank() ? null : (p.length() > 500 ? p.substring(0, 497) + "..." : p));
        if (decisao == AutorizacaoDiretoria.NAO_AUTORIZADA) {
            compra.setStatus(StatusCompra.CANCELADA);
        }
        Compra salva = compraRepository.save(compra);
        log.info("[Compras] Decisão da Diretoria registrada na compra #{}: {} por {}.", compraId, decisao, usuario);
        return salva;
    }

    /**
     * RN09 - Para Compra ESTOQUE: agrupa todos os itens em UMA ENTRADA multi-item
     * (uma NF, vários materiais) e delega ao MovimentacaoService, que credita o
     * saldo de cada material na mesma transação.
     */
    private void baixarComoEntradaDeEstoque(Compra compra) {
        movimentacaoService.registrarEntrada(
                compra.getFornecedor(),
                compra.getNumeroNotaFiscal(),
                "Entrada da Compra #" + compra.getId(),
                linhasDe(compra));
    }

    /**
     * RN10 - Para Compra DIRETA: a mercadoria foi adquirida sob demanda
     * específica para o setor solicitante e entregue diretamente.
     *
     * <p>O material NÃO passa pelo estoque geral. Agrupamos todos os itens da
     * compra em UMA movimentação multi-item do tipo {@link TipoMovimentacao#COMPRA_DIRETA}
     * com o setor de destino — assim o Relatório de Consumo por Setor (RF10)
     * consegue contabilizar o consumo somando os itens.</p>
     */
    private void baixarComoRepasseDireto(Compra compra) {
        Setor setorDestino = compra.getSetorSolicitante();
        // Defesa em profundidade: a RN08 já bloqueia esse caso na criação,
        // mas validamos novamente para o caso (raro) de o registro chegar inconsistente.
        if (setorDestino == null) {
            throw new IllegalStateException(
                    "RN10: compra direta sem setor solicitante — impossível repassar.");
        }

        movimentacaoService.registrarCompraDireta(
                setorDestino,
                "Compra Direta #" + compra.getId(),
                compra.getFornecedor(),
                compra.getNumeroNotaFiscal(),
                "Repasse direto da Compra #" + compra.getId(),
                linhasDe(compra));
    }

    /**
     * Mapeia os itens da compra para as linhas do MovimentacaoService.
     * (getId() no proxy lazy de Material não inicializa a entidade.)
     */
    private static List<MovimentacaoService.LinhaItem> linhasDe(Compra compra) {
        return compra.getItens().stream()
                .map(it -> new MovimentacaoService.LinhaItem(
                        it.getMaterial().getId(), it.getQuantidade()))
                .toList();
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

    /** Mesmo que {@link #anexar(Compra, MultipartFile, TipoAnexoCompra)}, para conteúdo já em memória. */
    @Transactional
    public AnexoCompra anexar(Compra compra, byte[] conteudo, String nomeArquivo, String contentType,
                              TipoAnexoCompra tipo) {
        var meta = anexoStorageService.salvar(conteudo, nomeArquivo, contentType, "compras/" + compra.getId());

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

    /** RF15 - usada pela tela "Aguardando Compra" (FIFO: dataSolicitacao asc). */
    public Page<Compra> listarAguardandoCompra(Pageable pageable) {
        Page<Compra> page = compraRepository.findByStatus(StatusCompra.AGUARDANDO_COMPRA,
                comOrdenacaoPadrao(pageable, Sort.by(Sort.Direction.ASC, "dataSolicitacao")));
        carregarItens(page);
        return page;
    }

    /** Lista geral (todas as compras, mais novas primeiro). */
    public Page<Compra> listarTodas(Pageable pageable) {
        Page<Compra> page = compraRepository.findAll(
                comOrdenacaoPadrao(pageable, Sort.by(Sort.Direction.DESC, "dataSolicitacao")));
        carregarItens(page);
        return page;
    }

    /**
     * open-in-view=false: a lista.html mostra a contagem de itens por linha
     * ({@code #lists.size(c.itens)}), e essa coleção lazy tem de estar
     * inicializada antes de a transação fechar, senão a listagem quebra no
     * render (era exatamente o que travava o módulo de compras em produção).
     */
    private void carregarItens(Page<Compra> page) {
        if (!page.isEmpty()) {
            compraRepository.carregarItens(page.getContent());
        }
    }

    /**
     * Listagem para a REST API ({@code GET /api/compras}): filtro opcional por
     * status e DTO montado dentro da transação read-only (itens já hidratados).
     */
    public Page<CompraResumoDTO> listarResumo(StatusCompra status, Pageable pageable) {
        Pageable p = comOrdenacaoPadrao(pageable, Sort.by(Sort.Direction.DESC, "dataSolicitacao"));
        Page<Compra> page = status == null
                ? compraRepository.findAll(p)
                : compraRepository.findByStatus(status, p);
        carregarItens(page);
        return page.map(CompraResumoDTO::from);
    }

    /** Detalhe para a REST API ({@code GET /api/compras/{id}}) com itens e anexos. */
    public CompraDetalheDTO buscarDetalhe(Long id) {
        Compra compra = compraRepository.findByIdComItens(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Compra", id));
        compraRepository.findByIdComAnexos(id);
        return CompraDetalheDTO.from(compra);
    }

    /** Garante a ordenação padrão quando o chamador não pede nenhuma. */
    private static Pageable comOrdenacaoPadrao(Pageable pageable, Sort padrao) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), padrao);
    }

    public Compra buscarPorId(Long id) {
        Compra compra = compraRepository.findByIdComItens(id)
                .orElseThrow(() -> new IllegalArgumentException("Compra id=" + id + " não encontrada."));
        // Segunda query, mesma sessão: hidrata compra.anexos na mesma instância
        // (ver o porquê no Javadoc de findByIdComAnexos). detalhes.html lê
        // itens E anexos; ambos precisam estar prontos antes da sessão fechar.
        compraRepository.findByIdComAnexos(id);
        return compra;
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
