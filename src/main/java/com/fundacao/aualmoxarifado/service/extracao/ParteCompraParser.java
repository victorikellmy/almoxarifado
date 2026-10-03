package com.fundacao.aualmoxarifado.service.extracao;

import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Item;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Natureza;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Orcamento;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.TipoDocumento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor heurístico (expressões regulares) do texto de uma Parte / Ofício de compra da FPTO.
 *
 * Reconhece o padrão dos documentos oficiais da Fundação e das unidades da PMTO/CBMTO:
 * cabeçalho "PARTE nº 001/2026 - Setor" ou "OFÍCIO nº 136/2025 - Unidade", número SGD,
 * "Palmas - TO, 15 de Janeiro de 2026", remetente ("Da/De ..."), destinatário ("Ao ..."),
 * "Assunto:", corpo do pedido, tabela de orçamentos, dados bancários e, quando houver
 * nota fiscal anexa (NFS-e), prestador, CNPJ, número e valor da nota.
 *
 * Funciona apenas sobre PDFs com camada de texto. Para PDFs escaneados (imagem) é
 * necessária a leitura assistida por IA ({@link ExtracaoIaService}).
 */
@Component
public class ParteCompraParser {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    /** "PARTE nº 001/2026 - Compras/Almoxarifado" / "Ofício n° 001/2026 - NÚCLEO DE SAÚDE". */
    private static final Pattern P_CABECALHO = Pattern.compile(
            "\\b(PARTE|OF[ÍI]CIO)\\s*n\\s*[ºo°\\.]*\\s*(\\d{1,5})\\s*/\\s*(\\d{4})\\s*(?:[-–]\\s*(.+?))?\\s*$",
            FLAGS | Pattern.MULTILINE);

    private static final Pattern P_SGD = Pattern.compile(
            "\\bSGD\\s*(?:N\\s*[ºo°\\.]*)?\\s*:?\\s*(\\d{4}/\\d{3,6}/\\d{3,8}|\\d{4,})", FLAGS);

    /** "Palmas - TO, 15 de Janeiro de 2026" / "Porto Nacional/TO, 05 de janeiro de 2026". */
    private static final Pattern P_DATA = Pattern.compile(
            "([\\p{L}][\\p{L} \\.]{1,60}?)\\s*[-–/]\\s*TO\\s*,\\s*(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})", FLAGS);

    private static final Pattern P_ASSUNTO = Pattern.compile("^\\s*Assunto\\s*:\\s*(.+?)\\s*$", FLAGS | Pattern.MULTILINE);

    /** "Da MAJ ..." / "DA: CEL ..." — sensível a maiúsculas para não confundir com "de nº ..." no corpo do texto. */
    private static final Pattern P_REMETENTE = Pattern.compile("^\\s*(?-i:(?:Da|DA|De|DE))\\s*:?\\s+((?-i:\\p{Lu}).+?)\\s*$", FLAGS | Pattern.MULTILINE);

    private static final Pattern P_DESTINATARIO = Pattern.compile(
            "^\\s*(?:Ao|À|A)\\s*:?\\s+((?:Sr\\.?|Sra\\.?|Senhor|Senhora|Exm[oa]\\.?|Ilm[oa]\\.?)\\b.*?)\\s*$", FLAGS | Pattern.MULTILINE);

    private static final Pattern P_BPM = Pattern.compile("(\\d{1,2})\\s*[ºo°]\\s*BPM", FLAGS);
    private static final Pattern P_CIPM = Pattern.compile("(\\d{1,2})\\s*[ªa]\\s*CIPM", FLAGS);

    private static final Pattern P_NF = Pattern.compile(
            "nota\\s+fiscal(?:\\s*-?\\s*e)?\\s*(?:de\\s*)?n\\s*[ºo°\\.]*\\s*(\\d{3,})", FLAGS);

    private static final Pattern P_CNPJ = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final String CNPJ_FPTO = "17.670.141/0001-14";

    private static final Pattern P_RS = Pattern.compile("R\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})");

    /** Linha de orçamento: "Tupy Gás   120,00   0800 646 1818". */
    private static final Pattern P_ORCAMENTO = Pattern.compile(
            "^\\s*(.+?)\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})\\s+([\\(\\)\\d\\s\\-\\.]{8,})\\s*$");

    private static final Pattern P_EMPRESA = Pattern.compile(
            "^\\s*([\\p{L}\\d][\\p{L}\\d &\\.'\\-]{2,}?\\s*(?:LTDA|L\\.T\\.D\\.A|ME|EPP|EIRELI|S/A|S\\.A\\.|MEI)\\.?)\\s*$", FLAGS | Pattern.MULTILINE);

    private static final String UNIDADES = "un|und|unid|unidades?|cx|caixas?|pct|pacotes?|kg|g|l|lt|litros?|ml|p[çc]|pcs?|pe[çc]as?|par|pares|rolos?|fr|frascos?|gl|gal[ãa]o|gal[õo]es|resmas?|m|metros?|kit|kits|sc|sacos?|fardos?|tubos?|bisnagas?|envelopes?|blocos?";
    private static final Pattern P_ITEM_QTD_PRIMEIRO = Pattern.compile(
            "^\\s*(?:\\d{1,3}\\s*[-\\.)]\\s*)?(\\d{1,5}(?:,\\d{1,3})?)\\s+(" + UNIDADES + ")\\.?\\s+(?:de\\s+)?([\\p{L}\\d].{2,}?)(?:\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}))?\\s*$", FLAGS);
    private static final Pattern P_ITEM_QTD_DEPOIS = Pattern.compile(
            "^\\s*(?:\\d{1,3}\\s*[-\\.)]\\s*)?([\\p{L}][\\p{L}\\d ,\\./\\-()]{2,}?)\\s+(\\d{1,5}(?:,\\d{1,3})?)\\s*(" + UNIDADES + ")\\.?(?:\\s+(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}))?\\s*$", FLAGS);

    /** "REPARO DE PLACA FONTE ..................R$320,00" (descrição de serviço em NFS-e). */
    private static final Pattern P_SERVICO_PONTILHADO = Pattern.compile(
            "^\\s*(.+?)\\s*\\.{2,}\\s*(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+,\\d{2})\\s*$");

    private static final Pattern P_SAUDACAO = Pattern.compile(
            "^\\s*(?:Senhor|Senhora|Sr\\.?|Sra\\.?|Excelent[íi]ssimo|Ilustr[íi]ssimo|Prezad[oa])\\b.*[,:]\\s*$", FLAGS);
    private static final Pattern P_FIM_CORPO = Pattern.compile(
            "^\\s*(Respeitosamente|Atenciosamente|Cordialmente|Abaixo\\s+or[çc]amento|Dados\\s+banc[áa]rios|Segue\\s+(?:a\\s+)?rela[çc][ãa]o)\\b", FLAGS);
    private static final Pattern P_RODAPE = Pattern.compile(
            "^\\s*(ASSINADO POR|Verifique a autenticidade|P[ÁA]GINA\\s+\\d+|E-mail:|Site:|Tel\\.?:|Fone:|CEP:|Av\\.|Avenida|Rua\\s|Q\\s\\d|_{5,})", FLAGS);

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("janeiro", 1), Map.entry("fevereiro", 2), Map.entry("marco", 3), Map.entry("março", 3),
            Map.entry("abril", 4), Map.entry("maio", 5), Map.entry("junho", 6), Map.entry("julho", 7),
            Map.entry("agosto", 8), Map.entry("setembro", 9), Map.entry("outubro", 10),
            Map.entry("novembro", 11), Map.entry("dezembro", 12));

    public ExtracaoDocumentoCompra parse(String textoBruto) {
        ExtracaoDocumentoCompra r = new ExtracaoDocumentoCompra();
        if (textoBruto == null || textoBruto.isBlank()) {
            r.aviso("O PDF não possui texto legível (provavelmente é uma imagem escaneada). Preencha os campos manualmente ou habilite a leitura por IA.");
            return r;
        }

        String texto = normalizar(textoBruto);
        r.setTextoExtraido(texto);
        List<String> linhas = Arrays.asList(texto.split("\n"));

        lerCabecalho(texto, r);
        lerSgd(texto, r);
        lerData(texto, r);
        lerRemetente(linhas, r);
        lerDestinatario(linhas, r);
        lerAssunto(texto, r);
        lerCorpo(linhas, r);
        lerUnidade(texto, r);
        lerNotaFiscal(texto, linhas, r);
        lerOrcamentos(linhas, r);
        lerDadosBancarios(linhas, r);
        lerFornecedor(texto, r);
        lerItens(linhas, r);
        lerValor(texto, r);
        classificar(texto, r);
        gerarAvisos(texto, r);
        return r;
    }

    // ------------------------------------------------------------------ cabeçalho

    private void lerCabecalho(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = P_CABECALHO.matcher(texto);
        if (!m.find()) {
            r.setTipoDocumento(TipoDocumento.OUTRO);
            return;
        }
        String tipo = m.group(1).toUpperCase(Locale.ROOT);
        r.setTipoDocumento(tipo.startsWith("PARTE") ? TipoDocumento.PARTE : TipoDocumento.OFICIO);
        r.setNumeroDocumento(String.format("%03d/%s", Integer.parseInt(m.group(2)), m.group(3)));
        if (m.group(4) != null) {
            String origem = limpar(m.group(4)).replaceAll("[\\.\\s]+$", "");
            if (!origem.isBlank()) r.setOrigemDocumento(origem);
        }
    }

    private void lerSgd(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = P_SGD.matcher(texto);
        if (m.find()) r.setNumeroSgd(m.group(1));
    }

    private void lerData(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = P_DATA.matcher(texto);
        if (!m.find()) return;
        String local = limpar(m.group(1));
        String[] partes = local.split("\\s{2,}");
        local = partes[partes.length - 1].trim();
        r.setLocalDocumento(local + " - TO");
        Integer mes = MESES.get(semAcento(m.group(3)).toLowerCase(Locale.ROOT));
        if (mes != null) {
            try {
                r.setDataDocumento(LocalDate.of(Integer.parseInt(m.group(4)), mes, Integer.parseInt(m.group(2))));
            } catch (Exception ignored) {
                // data inválida no documento: deixa em branco para o colaborador informar
            }
        }
    }

    private void lerRemetente(List<String> linhas, ExtracaoDocumentoCompra r) {
        for (String l : linhas) {
            Matcher m = P_REMETENTE.matcher(l);
            if (m.matches()) {
                String cand = limpar(m.group(1)).replaceAll("[\\.\\s]+$", "");
                if (cand.length() > 6 && !cand.toLowerCase(Locale.ROOT).startsWith("dados")) {
                    r.setRemetente(cand);
                    return;
                }
            }
        }
        // Fallback: bloco de assinatura após "Respeitosamente"
        int i = indiceDe(linhas, Pattern.compile("^\\s*(Respeitosamente|Atenciosamente|Cordialmente)", FLAGS), 0);
        if (i < 0) return;
        String nome = null, cargo = null;
        for (int j = i + 1; j < Math.min(linhas.size(), i + 8); j++) {
            String l = limpar(linhas.get(j));
            if (l.isBlank() || l.matches("(?i)assinatura\\s+digital") || P_RODAPE.matcher(l).find()) continue;
            if (nome == null) { nome = l; continue; }
            cargo = l;
            break;
        }
        if (nome != null) r.setRemetente(cargo != null ? nome + " - " + cargo : nome);
    }

    private void lerDestinatario(List<String> linhas, ExtracaoDocumentoCompra r) {
        for (int i = 0; i < linhas.size(); i++) {
            Matcher m = P_DESTINATARIO.matcher(linhas.get(i));
            if (!m.matches()) continue;
            String dest = limpar(m.group(1)).replaceAll("[\\.\\s]+$", "");
            if (dest.matches("(?i)(Sr\\.?|Senhor|Senhora|Sra\\.?)")) {
                List<String> extra = new ArrayList<>();
                for (int j = i + 1; j < Math.min(linhas.size(), i + 4); j++) {
                    String l = limpar(linhas.get(j));
                    if (l.isBlank() || l.matches("(?i).*\\b-\\s*TO\\.?$") || l.matches("(?i)^(Palmas|Assunto).*")) break;
                    extra.add(l.replaceAll("[\\.\\s]+$", ""));
                }
                if (!extra.isEmpty()) dest = String.join(", ", extra);
            }
            r.setDestinatario(dest);
            return;
        }
    }

    private void lerAssunto(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = P_ASSUNTO.matcher(texto);
        if (m.find()) r.setAssunto(limpar(m.group(1)).replaceAll("[\\.\\s]+$", ""));
    }

    /** Corpo do pedido: da saudação (ou do "Assunto:") até "Respeitosamente"/orçamentos/dados bancários. */
    private void lerCorpo(List<String> linhas, ExtracaoDocumentoCompra r) {
        int inicio = indiceDe(linhas, P_SAUDACAO, 0);
        if (inicio < 0) {
            inicio = indiceDe(linhas, Pattern.compile("^\\s*Assunto\\s*:", FLAGS), 0);
            if (inicio >= 0) {
                while (inicio + 1 < linhas.size() && linhas.get(inicio + 1).matches("(?i)^\\s*Anexos?\\s*:.*")) inicio++;
            }
        }
        if (inicio < 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = inicio + 1; i < linhas.size(); i++) {
            String l = linhas.get(i);
            if (P_FIM_CORPO.matcher(l).find()) break;
            if (P_RODAPE.matcher(l).find()) continue;
            String t = limpar(l);
            if (t.isBlank()) { if (sb.length() > 0) sb.append('\n'); continue; }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append(' ');
            sb.append(t);
        }
        String corpo = sb.toString().replaceAll("\n{2,}", "\n").trim();
        if (!corpo.isBlank()) r.setDescricao(corpo);
    }

    private void lerUnidade(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = P_BPM.matcher(texto);
        if (m.find()) { r.setUnidadeSolicitante(Integer.parseInt(m.group(1)) + "º BPM"); return; }
        m = P_CIPM.matcher(texto);
        if (m.find()) { r.setUnidadeSolicitante(Integer.parseInt(m.group(1)) + "ª CIPM"); return; }
        if (texto.matches("(?is).*\\bAlmoxarifado\\b.*")) { r.setUnidadeSolicitante("Almoxarifado"); return; }
        if (r.getOrigemDocumento() != null) r.setUnidadeSolicitante(r.getOrigemDocumento());
    }

    // ------------------------------------------------------------------ fiscal / financeiro

    private void lerNotaFiscal(String texto, List<String> linhas, ExtracaoDocumentoCompra r) {
        Matcher m = P_NF.matcher(texto);
        if (m.find()) r.setNumeroNotaFiscal(m.group(1));

        Matcher c = P_CNPJ.matcher(texto);
        while (c.find()) {
            if (!CNPJ_FPTO.equals(c.group())) { r.setCnpjFornecedor(c.group()); break; }
        }

        int prest = indiceDe(linhas, Pattern.compile("PRESTADOR\\s+DE\\s+SERVI[ÇC]OS", FLAGS), 0);
        if (prest >= 0) {
            int rz = indiceDe(linhas, Pattern.compile("^\\s*Raz[ãa]o\\s+Social\\s*$", FLAGS), prest);
            if (rz >= 0) {
                String nome = proximaLinhaNaoVazia(linhas, rz);
                if (nome != null) r.setFornecedor(nome);
            }
            int ds = indiceDe(linhas, Pattern.compile("DESCRI[ÇC][ÃA]O\\s+DOS\\s+SERVI[ÇC]OS", FLAGS), prest);
            if (ds >= 0) {
                for (int i = ds + 1; i < Math.min(linhas.size(), ds + 8); i++) {
                    Matcher s = P_SERVICO_PONTILHADO.matcher(linhas.get(i));
                    if (s.matches()) {
                        r.getItens().add(item(limpar(s.group(1)), BigDecimal.ONE, "serviço", valor(s.group(2))));
                    }
                }
            }
        }
    }

    private void lerOrcamentos(List<String> linhas, ExtracaoDocumentoCompra r) {
        int header = indiceDe(linhas, Pattern.compile("^\\s*Empresa\\s+Valor\\b", FLAGS), 0);
        int inicio = header >= 0 ? header + 1 : 0;
        int vazias = 0;
        for (int i = inicio; i < linhas.size(); i++) {
            String l = linhas.get(i);
            if (header >= 0) {
                if (l.isBlank()) { if (++vazias > 2 && !r.getOrcamentos().isEmpty()) break; continue; }
                if (P_FIM_CORPO.matcher(l).find()) break;
            }
            Matcher m = P_ORCAMENTO.matcher(l);
            if (!m.matches()) continue;
            String empresa = limpar(m.group(1));
            if (empresa.length() < 2 || empresa.matches("(?i).*\\b(valor|total|ag|c/c|conta)\\b.*")) continue;
            Orcamento o = new Orcamento();
            o.setEmpresa(empresa);
            o.setValor(valor(m.group(2)));
            o.setTelefone(limpar(m.group(3)));
            r.getOrcamentos().add(o);
        }
    }

    private void lerDadosBancarios(List<String> linhas, ExtracaoDocumentoCompra r) {
        int i = indiceDe(linhas, Pattern.compile("^\\s*(Dados\\s+banc[áa]rios|BANCO\\s+\\p{L}|Banco\\s*:|Ag[êe]ncia\\s*:?|Ag\\.?\\s*:?\\s*\\d)", FLAGS), 0);
        if (i < 0) return;
        List<String> bloco = new ArrayList<>();
        String primeira = limpar(linhas.get(i));
        if (!primeira.matches("(?i)dados\\s+banc[áa]rios\\s*:?")) bloco.add(primeira);
        for (int j = i + 1; j < Math.min(linhas.size(), i + 9); j++) {
            String l = limpar(linhas.get(j));
            if (l.isBlank()) { if (bloco.size() >= 2) break; else continue; }
            if (P_FIM_CORPO.matcher(l).find() || P_RODAPE.matcher(l).find() || l.matches("(?i)^RETEN[ÇC][ÕO]ES.*")) break;
            bloco.add(l.replaceAll("\\s*:\\s*", ": "));
        }
        if (!bloco.isEmpty()) r.setDadosBancarios(String.join(" | ", bloco));
    }

    private void lerFornecedor(String texto, ExtracaoDocumentoCompra r) {
        if (r.getFornecedor() != null) return;
        Matcher m = P_EMPRESA.matcher(texto);
        if (m.find()) {
            r.setFornecedor(limpar(m.group(1)));
            return;
        }
        r.getOrcamentos().stream()
                .filter(o -> o.getValor() != null)
                .min(Comparator.comparing(Orcamento::getValor))
                .ifPresent(o -> {
                    r.setFornecedor(o.getEmpresa());
                    r.aviso("Fornecedor sugerido pelo orçamento de menor valor (" + o.getEmpresa() + "). Confirme a escolha.");
                });
    }

    private void lerItens(List<String> linhas, ExtracaoDocumentoCompra r) {
        if (!r.getItens().isEmpty()) return;
        for (String l : linhas) {
            if (P_RODAPE.matcher(l).find() || P_ORCAMENTO.matcher(l).matches()) continue;
            Matcher m = P_ITEM_QTD_PRIMEIRO.matcher(l);
            if (m.matches()) {
                r.getItens().add(item(limpar(m.group(3)), valor(m.group(1)), m.group(2).toLowerCase(Locale.ROOT),
                        m.group(4) != null ? valor(m.group(4)) : null));
                continue;
            }
            m = P_ITEM_QTD_DEPOIS.matcher(l);
            if (m.matches() && !m.group(1).matches("(?i).*\\b(SGD|CEP|Tel|Fone|Ag|C/C)\\b.*")) {
                r.getItens().add(item(limpar(m.group(1)), valor(m.group(2)), m.group(3).toLowerCase(Locale.ROOT),
                        m.group(4) != null ? valor(m.group(4)) : null));
            }
        }
        // Compra direta de um único bem descrito no assunto (ex.: "Gás de cozinha (solicita)")
        if (r.getItens().isEmpty() && !r.getOrcamentos().isEmpty() && r.getAssunto() != null) {
            String desc = r.getAssunto().replaceAll("(?i)\\s*\\((solicita|solicitação|pedido)[^)]*\\)", "").trim();
            BigDecimal menor = r.getOrcamentos().stream().map(Orcamento::getValor)
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            r.getItens().add(item(desc, BigDecimal.ONE, "un", menor));
        }
    }

    private void lerValor(String texto, ExtracaoDocumentoCompra r) {
        Matcher m = Pattern.compile("Valor\\s+Total\\s+da\\s+Nota.*?\\n(?:.*\\n){0,3}?.*?(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$", FLAGS | Pattern.MULTILINE).matcher(texto);
        if (m.find() && !"0,00".equals(m.group(1))) { r.setValorEstimado(valor(m.group(1))); }
        if (r.getValorEstimado() == null) {
            Matcher rs = P_RS.matcher(texto);
            while (rs.find()) {
                BigDecimal v = valor(rs.group(1));
                if (v != null && v.signum() > 0) { r.setValorEstimado(v); break; }
            }
        }
        if (r.getValorEstimado() == null && !r.getOrcamentos().isEmpty()) {
            r.getOrcamentos().stream().map(Orcamento::getValor).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).ifPresent(r::setValorEstimado);
        }
        if (r.getValorEstimado() == null && !r.getItens().isEmpty()) {
            BigDecimal soma = r.getItens().stream()
                    .filter(i -> i.getQuantidade() != null && i.getValorUnitario() != null)
                    .map(i -> i.getQuantidade().multiply(i.getValorUnitario()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (soma.signum() > 0) r.setValorEstimado(soma);
        }
    }

    private void classificar(String texto, ExtracaoDocumentoCompra r) {
        String base = ((r.getAssunto() == null ? "" : r.getAssunto()) + " " + (r.getDescricao() == null ? "" : r.getDescricao()))
                .toLowerCase(Locale.ROOT);
        boolean temNf = r.getNumeroNotaFiscal() != null || texto.matches("(?is).*\\bNFS-?e\\b.*");
        if (base.contains("pagamento") && temNf) {
            r.setNatureza(Natureza.PAGAMENTO_NOTA_FISCAL);
        } else if (r.getTipoDocumento() == TipoDocumento.OFICIO || !r.getOrcamentos().isEmpty()) {
            r.setNatureza(Natureza.COMPRA_DIRETA);
        } else {
            r.setNatureza(Natureza.SOLICITACAO_SETOR);
        }
    }

    private void gerarAvisos(String texto, ExtracaoDocumentoCompra r) {
        if (r.getNumeroDocumento() == null) r.aviso("Número da Parte/Ofício não localizado.");
        if (r.getDataDocumento() == null) r.aviso("Data do documento não localizada.");
        if (r.getRemetente() == null) r.aviso("Remetente (solicitante) não localizado.");
        if (r.getAssunto() == null) r.aviso("Assunto não localizado.");
        if (r.getValorEstimado() == null) r.aviso("Valor não localizado: informe o valor estimado.");
        if (r.getItens().isEmpty()) {
            if (texto.matches("(?is).*rela[çc][ãa]o\\s+anexa.*") || texto.matches("(?is).*or[çc]amentos?\\s+(e\\s+autoriza[çc][õo]es\\s+)?(de\\s+compra\\s+)?anexos?.*")) {
                r.aviso("A relação de materiais/orçamentos consta em anexo separado: adicione os itens manualmente.");
            } else {
                r.aviso("Nenhum item identificado no documento: adicione os itens manualmente.");
            }
        }
        if (r.getNatureza() == Natureza.PAGAMENTO_NOTA_FISCAL) {
            r.aviso("Documento encaminha nota fiscal já emitida: confira fornecedor, CNPJ, nº da nota e dados bancários.");
        }
    }

    // ------------------------------------------------------------------ utilitários

    private static Item item(String descricao, BigDecimal qtd, String unidade, BigDecimal valorUnitario) {
        Item i = new Item();
        i.setDescricao(descricao);
        i.setQuantidade(qtd);
        i.setUnidade(unidade);
        i.setValorUnitario(valorUnitario);
        return i;
    }

    static String normalizar(String t) {
        return t.replace('\u00A0', ' ')
                .replace("\r\n", "\n").replace('\r', '\n')
                .replace('\f', '\n').replace('\u000B', '\n')
                .replace('\u2013', '-').replace('\u2014', '-')
                .replaceAll("[\\p{Cc}\\p{Co}\\uFFFD&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+\\n", "\n");
    }

    static String limpar(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Converte "8.367,20" → 8367.20. */
    static BigDecimal valor(String br) {
        if (br == null) return null;
        String n = br.trim().replace(".", "").replace(',', '.');
        try { return new BigDecimal(n); } catch (NumberFormatException e) { return null; }
    }

    private static int indiceDe(List<String> linhas, Pattern p, int apartirDe) {
        for (int i = Math.max(0, apartirDe); i < linhas.size(); i++) {
            if (p.matcher(linhas.get(i)).find()) return i;
        }
        return -1;
    }

    private static String proximaLinhaNaoVazia(List<String> linhas, int i) {
        for (int j = i + 1; j < Math.min(linhas.size(), i + 4); j++) {
            String l = limpar(linhas.get(j));
            if (!l.isBlank()) return l;
        }
        return null;
    }
}
