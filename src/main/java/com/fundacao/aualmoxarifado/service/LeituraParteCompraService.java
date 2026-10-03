package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Compra;
import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.domain.TipoCompra;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Natureza;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.extracao.ExtracaoIaService;
import com.fundacao.aualmoxarifado.service.extracao.ParteCompraParser;
import com.fundacao.aualmoxarifado.service.extracao.PdfTextoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Leitura do anexo (Parte / Ofício em PDF) na tela "Nova pré-compra".
 *
 * Lê o texto do PDF ({@link PdfTextoService}), interpreta os campos
 * ({@link ParteCompraParser}, opcionalmente complementado pela IA) e traduz o
 * resultado para o formulário de {@link Compra}: tipo, setor solicitante,
 * fornecedor, valor, dados do documento e itens casados com o catálogo de
 * {@link Material}. O colaborador só revisa e salva.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeituraParteCompraService {

    private static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    private static final Pattern P_UNIDADE_NUM = Pattern.compile("(\\d{1,2})\\s*[ºª°oa]\\s*(BPM|CIPM)", Pattern.CASE_INSENSITIVE);

    private final PdfTextoService pdfTextoService;
    private final ParteCompraParser parser;
    private final ExtracaoIaService extracaoIaService;
    private final SetorRepository setorRepository;
    private final MaterialRepository materialRepository;

    /** PDF lido, guardado na sessão até o colaborador salvar a pré-compra. */
    public record ArquivoLido(byte[] conteudo, String nomeOriginal, String contentType) implements Serializable {}

    /** Linha de item sugerida para o formulário (material casado ou não). */
    public record ItemSugerido(String descricao, Integer quantidade, String unidade,
                               BigDecimal valorUnitario, Long materialId, String materialNome) {
        public boolean isCasado() { return materialId != null; }
    }

    /** Tudo que a tela precisa para abrir o formulário já preenchido. */
    public record Preenchimento(String leituraId, ArquivoLido arquivo, ExtracaoDocumentoCompra extracao,
                                Compra compra, Long setorSugeridoId, List<ItemSugerido> itens,
                                List<String> avisos) {}

    public Preenchimento ler(MultipartFile arquivo) {
        validar(arquivo);
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Falha ao ler o arquivo enviado.", e);
        }
        String nome = arquivo.getOriginalFilename() == null || arquivo.getOriginalFilename().isBlank()
                ? "parte.pdf" : arquivo.getOriginalFilename();
        ArquivoLido lido = new ArquivoLido(bytes, nome,
                arquivo.getContentType() == null ? "application/pdf" : arquivo.getContentType());

        ExtracaoDocumentoCompra ex = extrair(bytes, nome);
        return montar(UUID.randomUUID().toString(), lido, ex);
    }

    // ------------------------------------------------------------------ extração

    private ExtracaoDocumentoCompra extrair(byte[] pdf, String nome) {
        PdfTextoService.TextoPdf texto = pdfTextoService.extrair(pdf);
        ExtracaoDocumentoCompra ex = parser.parse(texto.texto());
        ex.setPaginas(texto.paginas());
        if (texto.provavelmenteEscaneado()) {
            ex.aviso("O PDF parece conter páginas escaneadas (imagem) sem texto legível"
                    + (extracaoIaService.isHabilitada()
                        ? "; a leitura por IA foi usada para complementar."
                        : ". Confira os campos com atenção."));
        }
        extracaoIaService.extrair(pdf, nome).ifPresent(ex::mesclar);
        return ex;
    }

    // ------------------------------------------------------------------ tradução para o formulário

    private Preenchimento montar(String leituraId, ArquivoLido arquivo, ExtracaoDocumentoCompra ex) {
        List<String> avisos = new ArrayList<>(ex.getAvisos());

        Compra compra = new Compra();
        // Parte interna de setor da Fundação repõe estoque; ofício de unidade/pagamento é compra direta.
        compra.setTipo(ex.getNatureza() == Natureza.SOLICITACAO_SETOR ? TipoCompra.ESTOQUE : TipoCompra.DIRETA);
        compra.setFornecedor(truncar(ex.getFornecedor(), 150));
        compra.setValorEstimado(ex.getValorEstimado() != null ? ex.getValorEstimado() : BigDecimal.ZERO);
        compra.setNumeroDocumento(truncar(ex.getNumeroDocumento(), 30));
        compra.setNumeroSgd(truncar(ex.getNumeroSgd(), 40));
        compra.setDataDocumento(ex.getDataDocumento());
        compra.setAssunto(truncar(ex.getAssunto(), 255));
        compra.setSolicitanteDocumento(truncar(ex.getRemetente(), 255));
        compra.setObservacao(truncar(montarObservacao(ex), 200));

        Long setorId = sugerirSetor(ex).map(Setor::getId).orElse(null);
        if (setorId == null && compra.getTipo() == TipoCompra.DIRETA) {
            avisos.add("Setor solicitante não identificado automaticamente"
                    + (ex.getUnidadeSolicitante() != null ? " (documento cita \"" + ex.getUnidadeSolicitante() + "\")" : "")
                    + ": selecione o setor.");
        }

        List<ItemSugerido> itens = casarItens(ex, avisos);

        return new Preenchimento(leituraId, arquivo, ex, compra, setorId, itens, avisos);
    }

    /** Observação curta (coluna de 200 caracteres) com o que não tem campo próprio. */
    private static String montarObservacao(ExtracaoDocumentoCompra ex) {
        List<String> partes = new ArrayList<>();
        if (ex.getUnidadeSolicitante() != null) partes.add(ex.getUnidadeSolicitante());
        if (ex.getNatureza() == Natureza.PAGAMENTO_NOTA_FISCAL && ex.getNumeroNotaFiscal() != null) {
            partes.add("NF " + ex.getNumeroNotaFiscal() + " já emitida (pagamento)");
        }
        if (ex.getCnpjFornecedor() != null) partes.add("CNPJ " + ex.getCnpjFornecedor());
        if (!ex.getOrcamentos().isEmpty()) partes.add(ex.getOrcamentos().size() + " orçamento(s) no documento");
        if (ex.getDadosBancarios() != null) partes.add("Dados bancários: " + ex.getDadosBancarios());
        return partes.isEmpty() ? null : String.join(" · ", partes);
    }

    /**
     * Procura o Setor cadastrado que corresponde à unidade do documento
     * ("5º BPM", "Almoxarifado", "Núcleo de Saúde"...), comparando nomes sem acento.
     */
    private Optional<Setor> sugerirSetor(ExtracaoDocumentoCompra ex) {
        List<Setor> setores = setorRepository.findAll();
        if (setores.isEmpty()) return Optional.empty();

        List<String> pistas = new ArrayList<>();
        if (ex.getUnidadeSolicitante() != null) pistas.add(ex.getUnidadeSolicitante());
        if (ex.getOrigemDocumento() != null) pistas.add(ex.getOrigemDocumento());
        if (ex.getRemetente() != null) pistas.add(ex.getRemetente());

        for (String pista : pistas) {
            String p = normalizar(pista);
            // 1) unidade numerada: "5 bpm" casa com setor cujo nome tem "5" e "bpm"
            var m = P_UNIDADE_NUM.matcher(pista);
            if (m.find()) {
                String num = String.valueOf(Integer.parseInt(m.group(1)));
                String sigla = m.group(2).toLowerCase(Locale.ROOT);
                Optional<Setor> s = setores.stream()
                        .filter(x -> {
                            String n = normalizar(x.getNome());
                            return n.contains(sigla) && n.matches(".*\\b" + num + "\\b.*");
                        })
                        .findFirst();
                if (s.isPresent()) return s;
            }
            // 2) nome do setor contido na pista ou pista contida no nome do setor
            Optional<Setor> s = setores.stream()
                    .filter(x -> {
                        String n = normalizar(x.getNome());
                        return n.length() >= 3 && (p.contains(n) || n.contains(p));
                    })
                    .findFirst();
            if (s.isPresent()) return s;
        }
        return Optional.empty();
    }

    /** Casa cada item lido com um Material do catálogo por semelhança de nome. */
    private List<ItemSugerido> casarItens(ExtracaoDocumentoCompra ex, List<String> avisos) {
        if (ex.getItens().isEmpty()) return List.of();
        List<Material> catalogo = materialRepository.findAll();
        List<ItemSugerido> out = new ArrayList<>();
        for (ExtracaoDocumentoCompra.Item it : ex.getItens()) {
            Material m = melhorMaterial(it.getDescricao(), catalogo);
            Integer qtd = it.getQuantidade() == null ? 1
                    : Math.max(1, it.getQuantidade().setScale(0, RoundingMode.HALF_UP).intValue());
            out.add(new ItemSugerido(it.getDescricao(), qtd, it.getUnidade(),
                    it.getValorUnitario() != null ? it.getValorUnitario() : BigDecimal.ZERO,
                    m == null ? null : m.getId(), m == null ? null : m.getNome()));
            if (m == null) {
                avisos.add("Item \"" + it.getDescricao() + "\" não encontrado no catálogo de materiais: selecione o material correspondente ou cadastre-o.");
            }
        }
        return out;
    }

    /** Melhor material por proporção de palavras (≥ 3 letras) da descrição presentes no nome. */
    static Material melhorMaterial(String descricao, List<Material> catalogo) {
        if (descricao == null || catalogo.isEmpty()) return null;
        Set<String> tokens = tokens(descricao);
        if (tokens.isEmpty()) return null;
        Material melhor = null;
        double melhorScore = 0;
        for (Material m : catalogo) {
            Set<String> nome = tokens(m.getNome());
            if (nome.isEmpty()) continue;
            long comuns = tokens.stream().filter(nome::contains).count();
            double score = (double) comuns / Math.max(tokens.size(), nome.size());
            // nome do material inteiramente contido na descrição também vale
            if (comuns == nome.size()) score = Math.max(score, 0.75);
            if (score > melhorScore) { melhorScore = score; melhor = m; }
        }
        return melhorScore >= 0.5 ? melhor : null;
    }

    private static Set<String> tokens(String s) {
        Set<String> t = new LinkedHashSet<>();
        for (String w : normalizar(s).split("[^a-z0-9]+")) {
            if (w.length() >= 3) t.add(w);
        }
        return t;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[º°ª]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String truncar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione o PDF da Parte/Ofício para leitura.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Arquivo maior que 10 MB.");
        }
        String nome = arquivo.getOriginalFilename() == null ? "" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        String tipo = arquivo.getContentType() == null ? "" : arquivo.getContentType().toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".pdf") && !tipo.contains("pdf")) {
            throw new IllegalArgumentException("Somente arquivos PDF são aceitos.");
        }
    }
}
