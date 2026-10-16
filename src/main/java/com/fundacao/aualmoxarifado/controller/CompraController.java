package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import org.springframework.data.jpa.domain.Specification;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.AnexoStorageService;
import com.fundacao.aualmoxarifado.service.CompraService;
import com.fundacao.aualmoxarifado.service.LeituraParteCompraService;
import com.fundacao.aualmoxarifado.service.extracao.ExtracaoIaService;
import com.fundacao.aualmoxarifado.service.integracao.PatrimonioIntegracaoService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller MVC do Módulo de Compras (RF14/RF15/RF16).
 *
 * Mapeia 3 telas principais:
 *   - GET  /compras                 → listagem geral
 *   - GET  /compras/aguardando      → fila "Aguardando Compra" (RF15)
 *   - GET  /compras/nova            → formulário de pré-compra
 *   - POST /compras                 → cria a pré-compra (RF14)
 *   - GET  /compras/{id}            → detalhes
 *   - GET  /compras/{id}/baixa      → tela de recebimento da NF
 *   - POST /compras/{id}/baixa      → efetiva a baixa (RF15/RN09/RN10)
 *   - POST /compras/{id}/cancelar   → cancela uma pré-compra
 *   - GET  /compras/{id}/anexos/{anexoId}/download → serve o PDF
 */
@Controller
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;
    private final MaterialRepository materialRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final AreaRepository areaRepository;
    private final SetorRepository setorRepository;
    private final AnexoStorageService anexoStorageService;
    private final LeituraParteCompraService leituraParteCompraService;
    private final ExtracaoIaService extracaoIaService;
    private final PatrimonioIntegracaoService patrimonioIntegracaoService;

    /** Prefixo da chave de sessão onde o PDF lido fica até a pré-compra ser salva. */
    private static final String SESSAO_LEITURA = "compras.leitura.";

    // =====================================================================
    // LISTAGENS
    // =====================================================================

    @GetMapping
    public String listar(@PageableDefault(size = 20, sort = "dataSolicitacao",
                                          direction = Sort.Direction.DESC) Pageable pageable,
                         Model model) {
        Page<Compra> page = compraService.listarTodas(pageable);
        model.addAttribute("page", page);
        model.addAttribute("compras", page.getContent());
        model.addAttribute("titulo", "Histórico de Compras");
        model.addAttribute("filtro", "todas");
        return "compras/lista";
    }

    /** RF15 - tela da fila de pré-compras pendentes (FIFO). */
    @GetMapping("/aguardando")
    public String listarAguardando(@PageableDefault(size = 20, sort = "dataSolicitacao",
                                                    direction = Sort.Direction.ASC) Pageable pageable,
                                   Model model) {
        Page<Compra> page = compraService.listarAguardandoCompra(pageable);
        model.addAttribute("page", page);
        model.addAttribute("compras", page.getContent());
        model.addAttribute("titulo", "Fila — Aguardando Compra");
        model.addAttribute("filtro", "aguardando");
        return "compras/lista";
    }

    // =====================================================================
    // PRÉ-COMPRA (criação)
    // =====================================================================

    @GetMapping("/nova")
    public String novaPreCompra(Model model) {
        prepararFormulario(model, new Compra());
        return "compras/form";
    }

    /**
     * Leitura automática da Parte/Ofício: o colaborador envia o PDF, o sistema
     * extrai os dados e devolve o MESMO formulário de pré-compra já preenchido
     * (tipo, setor, fornecedor, valor, dados do documento e itens casados com o
     * catálogo). O PDF fica na sessão e vira o anexo SOLICITACAO ao salvar.
     */
    @PostMapping("/ler-anexo")
    public String lerAnexo(@RequestParam("pdfParte") MultipartFile pdfParte,
                           HttpSession session, Model model) {
        try {
            var leitura = leituraParteCompraService.ler(pdfParte);
            session.setAttribute(SESSAO_LEITURA + leitura.leituraId(), leitura.arquivo());

            prepararFormulario(model, leitura.compra());
            model.addAttribute("leitura", leitura);
            model.addAttribute("leituraId", leitura.leituraId());
            model.addAttribute("setorSugeridoId", leitura.setorSugeridoId());
            model.addAttribute("itensSugeridos", leitura.itens());
            model.addAttribute("avisos", leitura.avisos());
            return "compras/form";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            prepararFormulario(model, new Compra());
            return "compras/form";
        }
    }

    /**
     * Recebe o submit do form. Os itens vêm como arrays paralelos
     * (itemMaterialId[i], itemQuantidade[i], itemValorUnitario[i]) — formato
     * mais simples para o Thymeleaf gerenciar inputs adicionados via JS.
     * {@code leituraId} identifica o PDF lido previamente (guardado na sessão).
     */
    @PostMapping
    public String salvarPreCompra(@ModelAttribute Compra compra,
                                  @RequestParam(required = false) Long setorSolicitanteId,
                                  @RequestParam(name = "itemMaterialId", required = false) List<String> materiaisIds,
                                  @RequestParam(name = "itemDescricao", required = false) List<String> descricoes,
                                  @RequestParam(name = "itemSubcategoriaId", required = false) List<String> subcategoriasIds,
                                  @RequestParam(name = "itemQuantidade", required = false) List<Integer> quantidades,
                                  @RequestParam(name = "itemValorUnitario", required = false) List<BigDecimal> valoresUnit,
                                  @RequestParam(name = "pdfSolicitacao", required = false) MultipartFile pdfSolicitacao,
                                  @RequestParam(required = false) String leituraId,
                                  HttpSession session,
                                  Model model) {
        try {
            // Wiring do setor: o form envia o id; transformamos numa referência.
            if (setorSolicitanteId != null) {
                Setor s = new Setor();
                s.setId(setorSolicitanteId);
                compra.setSetorSolicitante(s);
            }

            LeituraParteCompraService.ArquivoLido pdfLido = null;
            if (leituraId != null && !leituraId.isBlank()) {
                Object guardado = session.getAttribute(SESSAO_LEITURA + leituraId);
                if (guardado instanceof LeituraParteCompraService.ArquivoLido a) {
                    pdfLido = a;
                }
            }

            List<ItemCompra> itens = montarItens(materiaisIds, descricoes, subcategoriasIds, quantidades, valoresUnit);
            compraService.criarPreCompra(compra, itens, pdfSolicitacao, pdfLido);

            if (leituraId != null && !leituraId.isBlank()) {
                session.removeAttribute(SESSAO_LEITURA + leituraId);
            }
            return "redirect:/compras/aguardando";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            prepararFormulario(model, compra);
            if (leituraId != null && !leituraId.isBlank()) {
                // mantém o PDF lido para o usuário não precisar reenviar
                model.addAttribute("leituraId", leituraId);
                Object guardado = session.getAttribute(SESSAO_LEITURA + leituraId);
                if (guardado instanceof LeituraParteCompraService.ArquivoLido a) {
                    model.addAttribute("anexoPendenteNome", a.nomeOriginal());
                }
            }
            return "compras/form";
        }
    }

    // =====================================================================
    // BAIXA (recebimento da NF)
    // =====================================================================

    /** Tela de recebimento — NF, valor real, PDF, quem retirou/setor e "será patrimoniado". */
    @GetMapping("/{id}/baixa")
    public String telaBaixa(@PathVariable Long id, Model model) {
        Compra compra = compraService.buscarPorId(id);
        model.addAttribute("compra", compra);
        model.addAttribute("setores", setorRepository.listarOrdenados());
        model.addAttribute("patrimonioConfigurado", patrimonioIntegracaoService.isConfigurado());
        return "compras/recebimento";
    }

    @PostMapping("/{id}/baixa")
    public String efetivarBaixa(@PathVariable Long id,
                                @RequestParam String numeroNF,
                                @RequestParam BigDecimal valorRealFinal,
                                @RequestParam(required = false) MultipartFile pdfNotaFiscal,
                                @RequestParam(required = false) String retiradoPor,
                                @RequestParam(required = false) Long setorEntregaId,
                                @RequestParam(required = false, defaultValue = "false") boolean enviarPatrimonio,
                                Authentication authentication,
                                RedirectAttributes ra,
                                Model model) {
        try {
            var dados = new CompraService.DadosRecebimento(retiradoPor, setorEntregaId, enviarPatrimonio,
                    authentication != null ? authentication.getName() : null);
            Compra baixada = compraService.darBaixa(id, numeroNF, valorRealFinal, pdfNotaFiscal, dados);

            if (Boolean.TRUE.equals(baixada.getEnviarPatrimonio())) {
                // Fora da transação da baixa: falha de rede não desfaz o recebimento.
                var envio = patrimonioIntegracaoService.enviar(id);
                ra.addFlashAttribute(envio.getStatus() == StatusEnvioPatrimonio.ENVIADO ? "sucesso" : "aviso",
                        envio.getStatus() == StatusEnvioPatrimonio.ENVIADO
                                ? "Compra recebida e enviada ao Patrimônio para tombamento."
                                : "Compra recebida. Envio ao Patrimônio pendente: " + envio.getUltimoErro());
            } else {
                ra.addFlashAttribute("sucesso", "Compra recebida e baixada.");
            }
            return "redirect:/compras/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("compra", compraService.buscarPorId(id));
            model.addAttribute("setores", setorRepository.listarOrdenados());
            model.addAttribute("patrimonioConfigurado", patrimonioIntegracaoService.isConfigurado());
            return "compras/recebimento";
        }
    }

    // =====================================================================
    // DECISÃO DA DIRETORIA (perfil COMPRAS/ADMIN)
    // =====================================================================

    @PostMapping("/{id}/autorizacao")
    public String registrarAutorizacao(@PathVariable Long id,
                                       @RequestParam AutorizacaoDiretoria decisao,
                                       @RequestParam(required = false) String parecer,
                                       Authentication authentication,
                                       RedirectAttributes ra) {
        try {
            compraService.registrarAutorizacao(id, decisao, parecer,
                    authentication != null ? authentication.getName() : null);
            ra.addFlashAttribute("sucesso", decisao == AutorizacaoDiretoria.AUTORIZADA
                    ? "Autorização da Diretoria registrada. A compra pode ser recebida."
                    : "Não autorização registrada: a pré-compra foi cancelada.");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/compras/" + id;
    }

    /** Reenvio manual ao Patrimônio (quando o envio automático falhou). */
    @PostMapping("/{id}/patrimonio/reenviar")
    public String reenviarPatrimonio(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var envio = patrimonioIntegracaoService.enviar(id);
            if (envio.getStatus() == StatusEnvioPatrimonio.ENVIADO) {
                ra.addFlashAttribute("sucesso", "Recebimento enviado ao Patrimônio.");
            } else {
                ra.addFlashAttribute("erro", "Envio ao Patrimônio não concluído: " + envio.getUltimoErro());
            }
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/compras/" + id;
    }

    // =====================================================================
    // DETALHES / AÇÕES
    // =====================================================================

    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        model.addAttribute("compra", compraService.buscarPorId(id));
        model.addAttribute("envioPatrimonio", patrimonioIntegracaoService.envioDa(id).orElse(null));
        return "compras/detalhes";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id) {
        compraService.cancelar(id);
        return "redirect:/compras";
    }

    /**
     * RF16 - download do PDF anexado. Aplica os headers corretos e devolve
     * o arquivo via streaming (sem carregá-lo todo em memória).
     */
    @GetMapping("/{id}/anexos/{anexoId}/download")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable Long id, @PathVariable Long anexoId) {
        Compra compra = compraService.buscarPorId(id);
        AnexoCompra anexo = compra.getAnexos().stream()
                .filter(a -> a.getId().equals(anexoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado nesta compra."));

        Resource recurso = anexoStorageService.carregar(anexo.getCaminhoArmazenado());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(anexo.getNomeOriginal(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(anexo.getContentType() != null
                        ? MediaType.parseMediaType(anexo.getContentType())
                        : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(recurso);
    }

    // =====================================================================
    // HELPERS PRIVADOS
    // =====================================================================

    private static Long parseId(String valor) {
        return (valor == null || valor.isBlank()) ? null : Long.valueOf(valor.trim());
    }

    private void prepararFormulario(Model model, Compra compra) {
        model.addAttribute("compra", compra);
        // Catálogo com subcategoria/área carregadas (open-in-view=false): alimenta o
        // seletor com busca dos itens de ESTOQUE e o preenchimento de grupo/subgrupo.
        List<Material> materiais = materialRepository
                .findAll((Specification<Material>) (root, q, cb) -> cb.conjunction(), Pageable.unpaged(Sort.by("nome")))
                .getContent();
        model.addAttribute("materiais", materiais);
        // Versão enxuta do catálogo, serializada no HTML (th:inline) para o seletor com busca.
        List<Map<String, Object>> opcoes = new ArrayList<>(materiais.size());
        for (Material m : materiais) {
            Subcategoria sc = m.getSubcategoria();
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("nome", m.getNome());
            o.put("sku", m.getCodigoSku());
            o.put("unidade", m.getUnidadeMedida());
            o.put("estoque", m.getEstoqueAtual());
            o.put("minimo", m.getEstoqueMinimo());
            o.put("subId", sc != null ? sc.getId() : null);
            o.put("subgrupo", sc != null ? sc.getNome() : null);
            o.put("areaId", sc != null && sc.getArea() != null ? sc.getArea().getId() : null);
            o.put("grupo", sc != null && sc.getArea() != null ? sc.getArea().getNome() : null);
            opcoes.add(o);
        }
        model.addAttribute("materiaisOpcoes", opcoes);
        model.addAttribute("subcategorias", subcategoriaRepository.findAll().stream()
                .sorted(Comparator.comparing((Subcategoria sc) -> sc.getArea().getNome(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Subcategoria::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList());
        model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
        model.addAttribute("setores", setorRepository.listarOrdenados());
        model.addAttribute("tipos", TipoCompra.values());
        model.addAttribute("iaHabilitada", extracaoIaService.isHabilitada());
    }

    /**
     * Converte os arrays paralelos do form (material, descrição, qtd, valor) em uma
     * lista de {@link ItemCompra} ainda não persistidos. Em compras PATRIMONIAIS a
     * linha pode vir sem material (só descrição). A validação fina (qtd > 0,
     * material existente, descrição obrigatória) acontece no Service.
     */
    private List<ItemCompra> montarItens(List<String> materiaisIds,
                                         List<String> descricoes,
                                         List<String> subcategoriasIds,
                                         List<Integer> quantidades,
                                         List<BigDecimal> valoresUnit) {
        List<ItemCompra> itens = new ArrayList<>();
        int linhas = Math.max(materiaisIds == null ? 0 : materiaisIds.size(),
                              descricoes == null ? 0 : descricoes.size());
        for (int i = 0; i < linhas; i++) {
            String matIdStr = materiaisIds != null && i < materiaisIds.size() ? materiaisIds.get(i) : null;
            String descricao = descricoes != null && i < descricoes.size() ? descricoes.get(i) : null;
            String subIdStr = subcategoriasIds != null && i < subcategoriasIds.size() ? subcategoriasIds.get(i) : null;
            Long matId = parseId(matIdStr);
            Long subId = parseId(subIdStr);
            if (matId == null && (descricao == null || descricao.isBlank())) {
                continue; // linha vazia — usuário removeu ou não preencheu
            }
            Material m = null;
            if (matId != null) {
                m = new Material();
                m.setId(matId);
            }
            Subcategoria sub = null;
            if (subId != null) {
                sub = new Subcategoria();
                sub.setId(subId);
            }

            Integer qtd = (quantidades != null && i < quantidades.size()) ? quantidades.get(i) : null;
            BigDecimal valor = (valoresUnit != null && i < valoresUnit.size())
                    ? valoresUnit.get(i)
                    : BigDecimal.ZERO;

            itens.add(ItemCompra.builder()
                    .material(m)
                    .subcategoria(sub)
                    .descricao(descricao == null || descricao.isBlank() ? null : descricao.trim())
                    .quantidade(qtd)
                    .valorUnitario(valor != null ? valor : BigDecimal.ZERO)
                    .build());
        }
        return itens;
    }
}
