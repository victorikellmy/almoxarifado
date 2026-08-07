package com.fundacao.aualmoxarifado.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/**
 * Lê planilhas de importação (.xlsx, .xls e .csv) e devolve uma estrutura
 * neutra: uma lista de linhas, cada uma sendo um mapa
 * {@code cabeçalho normalizado -> valor em texto}.
 *
 * Este serviço NÃO conhece Material, Área ou Subcategoria — ele só resolve os
 * problemas chatos de arquivo:
 *
 *  - Excel novo (.xlsx) e antigo (.xls) via Apache POI;
 *  - CSV com separador ";" (padrão do Excel em português), "," ou TAB —
 *    detectado automaticamente;
 *  - CSV salvo em UTF-8 ou em Windows-1252 (ANSI) — também detectado, para
 *    que "Papel Sulfite A4" não vire "Papel Sulfite A4" na tela;
 *  - células numéricas e de fórmula convertidas para texto sem notação
 *    científica ("1.0E7" viraria lixo no cadastro);
 *  - normalização dos cabeçalhos, para que "Estoque Mínimo", "ESTOQUE MINIMO"
 *    e "estoque_minimo" sejam a mesma coluna.
 */
@Slf4j
@Service
public class LeitorPlanilhaService {

    /** Teto de segurança: evita que um arquivo gigante derrube a aplicação. */
    public static final int MAX_LINHAS = 5_000;

    /** Uma linha da planilha, já normalizada. */
    public record LinhaPlanilha(int numero, Map<String, String> valores) {

        /** Linha totalmente vazia — o importador pula sem reclamar. */
        public boolean isVazia() {
            return valores.values().stream().allMatch(v -> v == null || v.isBlank());
        }
    }

    /** Arquivo inteiro: cabeçalhos encontrados + linhas de dados. */
    public record PlanilhaLida(List<String> cabecalhos, List<LinhaPlanilha> linhas) { }

    // =====================================================================
    // API pública
    // =====================================================================

    public PlanilhaLida ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo para importar.");
        }

        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo enviado.", e);
        }

        if (nome.endsWith(".csv") || nome.endsWith(".txt")) {
            return lerCsv(bytes);
        }
        if (nome.endsWith(".xlsx") || nome.endsWith(".xls") || nome.endsWith(".xlsm")) {
            return lerExcel(bytes);
        }
        throw new IllegalArgumentException(
                "Formato não suportado. Envie um arquivo .xlsx, .xls ou .csv.");
    }

    // =====================================================================
    // Excel
    // =====================================================================

    private PlanilhaLida lerExcel(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {

            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("A planilha enviada não possui nenhuma aba.");
            }

            FormulaEvaluator avaliador = workbook.getCreationHelper().createFormulaEvaluator();

            // A primeira linha COM ALGUM CONTEÚDO é o cabeçalho. Isso tolera
            // planilhas que começam com uma linha em branco ou com um título.
            int indiceCabecalho = -1;
            for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && temAlgumValor(row, avaliador)) {
                    indiceCabecalho = i;
                    break;
                }
            }
            if (indiceCabecalho < 0) {
                throw new IllegalArgumentException("A planilha está vazia.");
            }

            Row linhaCabecalho = sheet.getRow(indiceCabecalho);
            List<String> cabecalhos = new ArrayList<>();
            for (int c = 0; c < linhaCabecalho.getLastCellNum(); c++) {
                cabecalhos.add(normalizarCabecalho(
                        valorDaCelula(linhaCabecalho.getCell(c), avaliador)));
            }

            List<LinhaPlanilha> linhas = new ArrayList<>();
            for (int i = indiceCabecalho + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                Map<String, String> valores = new LinkedHashMap<>();
                for (int c = 0; c < cabecalhos.size(); c++) {
                    String chave = cabecalhos.get(c);
                    if (chave.isBlank()) {
                        continue;
                    }
                    String valor = row == null ? null : valorDaCelula(row.getCell(c), avaliador);
                    valores.put(chave, valor);
                }
                // +1 porque o usuário conta a partir de 1 na tela do Excel.
                LinhaPlanilha linha = new LinhaPlanilha(i + 1, valores);
                if (!linha.isVazia()) {
                    linhas.add(linha);
                }
                if (linhas.size() > MAX_LINHAS) {
                    throw new IllegalArgumentException(
                            "A planilha tem mais de " + MAX_LINHAS + " linhas. "
                          + "Divida o arquivo em partes menores.");
                }
            }

            return new PlanilhaLida(cabecalhos, linhas);

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Não foi possível abrir a planilha. O arquivo pode estar corrompido "
                  + "ou protegido por senha.", e);
        }
    }

    private boolean temAlgumValor(Row row, FormulaEvaluator avaliador) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String v = valorDaCelula(row.getCell(c), avaliador);
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converte uma célula para texto de forma previsível.
     *
     * O ponto delicado é a célula NUMÉRICA: {@code getNumericCellValue()}
     * devolve double, e um "10000000" viraria "1.0E7" com {@code toString()}.
     * Por isso passamos por {@link BigDecimal} e usamos {@code toPlainString()}.
     */
    private String valorDaCelula(Cell cell, FormulaEvaluator avaliador) {
        if (cell == null) {
            return null;
        }
        CellType tipo = cell.getCellType();
        if (tipo == CellType.FORMULA) {
            try {
                tipo = avaliador.evaluateFormulaCell(cell);
            } catch (RuntimeException ex) {
                // Fórmula que o POI não sabe calcular: usa o último valor salvo.
                log.debug("Fórmula não avaliada na célula {}: {}", cell.getAddress(), ex.getMessage());
                tipo = CellType.STRING;
            }
        }

        return switch (tipo) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue())
                                .stripTrailingZeros()
                                .toPlainString();
            }
            default -> null;
        };
    }

    // =====================================================================
    // CSV
    // =====================================================================

    private PlanilhaLida lerCsv(byte[] bytes) {
        String conteudo = decodificar(bytes);
        List<String> linhasTexto = conteudo.lines().toList();

        int indiceCabecalho = -1;
        for (int i = 0; i < linhasTexto.size(); i++) {
            if (!linhasTexto.get(i).isBlank()) {
                indiceCabecalho = i;
                break;
            }
        }
        if (indiceCabecalho < 0) {
            throw new IllegalArgumentException("O arquivo CSV está vazio.");
        }

        char separador = detectarSeparador(linhasTexto.get(indiceCabecalho));

        List<String> cabecalhos = separarCampos(linhasTexto.get(indiceCabecalho), separador)
                .stream().map(LeitorPlanilhaService::normalizarCabecalho).toList();

        List<LinhaPlanilha> linhas = new ArrayList<>();
        for (int i = indiceCabecalho + 1; i < linhasTexto.size(); i++) {
            String bruta = linhasTexto.get(i);
            if (bruta.isBlank()) {
                continue;
            }
            List<String> campos = separarCampos(bruta, separador);
            Map<String, String> valores = new LinkedHashMap<>();
            for (int c = 0; c < cabecalhos.size(); c++) {
                String chave = cabecalhos.get(c);
                if (chave.isBlank()) {
                    continue;
                }
                valores.put(chave, c < campos.size() ? campos.get(c) : null);
            }
            LinhaPlanilha linha = new LinhaPlanilha(i + 1, valores);
            if (!linha.isVazia()) {
                linhas.add(linha);
            }
            if (linhas.size() > MAX_LINHAS) {
                throw new IllegalArgumentException(
                        "O arquivo tem mais de " + MAX_LINHAS + " linhas. "
                      + "Divida-o em partes menores.");
            }
        }

        return new PlanilhaLida(cabecalhos, linhas);
    }

    /**
     * CSV exportado pelo Excel em português quase sempre vem em Windows-1252
     * (ANSI); já um CSV gerado por sistemas web vem em UTF-8. Tentamos decodificar
     * como UTF-8 em modo ESTRITO — se estourar, é sinal de que não é UTF-8 e
     * caímos para Windows-1252, que aceita qualquer byte.
     */
    private String decodificar(byte[] bytes) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return removerBom(texto);
        } catch (CharacterCodingException e) {
            log.debug("CSV não é UTF-8 válido; assumindo Windows-1252.");
            return removerBom(new String(bytes, Charset.forName("windows-1252")));
        }
    }

    private String removerBom(String texto) {
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }

    /** Escolhe entre ";", "," e TAB contando ocorrências FORA de aspas no cabeçalho. */
    private char detectarSeparador(String cabecalho) {
        char[] candidatos = {';', ',', '\t'};
        char escolhido = ';';
        int melhor = -1;
        for (char c : candidatos) {
            int qtd = contarFora(cabecalho, c);
            if (qtd > melhor) {
                melhor = qtd;
                escolhido = c;
            }
        }
        return escolhido;
    }

    private int contarFora(String linha, char alvo) {
        int total = 0;
        boolean dentroDeAspas = false;
        for (char c : linha.toCharArray()) {
            if (c == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (c == alvo && !dentroDeAspas) {
                total++;
            }
        }
        return total;
    }

    /**
     * Parser CSV mínimo, porém correto para o caso de uso: respeita campos
     * entre aspas (que podem conter o separador) e aspas duplicadas ("") como
     * escape de uma aspa literal.
     */
    private List<String> separarCampos(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (dentroDeAspas) {
                if (c == '"') {
                    if (i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                        atual.append('"');
                        i++;                    // consome a segunda aspa do escape
                    } else {
                        dentroDeAspas = false;
                    }
                } else {
                    atual.append(c);
                }
            } else if (c == '"') {
                dentroDeAspas = true;
            } else if (c == separador) {
                campos.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos;
    }

    // =====================================================================
    // Utilitários de normalização e conversão (usados pelo importador)
    // =====================================================================

    /**
     * "Estoque Mínimo (un.)" → "estoque_minimo_un"
     *
     * Minúsculas, sem acentos, tudo que não é letra/dígito vira "_" e os "_"
     * das pontas são removidos. É o que permite casar apelidos de coluna.
     */
    public static String normalizarCabecalho(String bruto) {
        if (bruto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(bruto.trim(), Normalizer.Form.NFD)
                                     .replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
    }

    /**
     * Converte texto em número decimal aceitando os formatos que aparecem na
     * prática: "1234", "1234.56", "1.234,56", "1234,56", "R$ 12,50".
     *
     * Regra para o ponto sozinho: se houver EXATAMENTE 3 dígitos depois dele
     * (ex.: "1.500"), ele é tratado como separador de milhar — é o que um
     * usuário brasileiro quer dizer. Com 1 ou 2 dígitos ("12.50") é decimal.
     *
     * @return {@code null} quando o texto está vazio
     * @throws IllegalArgumentException se o texto não for um número
     */
    public static BigDecimal parseDecimal(String texto, String nomeCampo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.replaceAll("[^0-9,.\\-]", "").trim();
        if (limpo.isEmpty() || limpo.equals("-")) {
            throw new IllegalArgumentException(
                    "Valor inválido em \"" + nomeCampo + "\": " + texto);
        }

        boolean temPonto = limpo.indexOf('.') >= 0;
        boolean temVirgula = limpo.indexOf(',') >= 0;

        if (temPonto && temVirgula) {
            // O separador decimal é o que aparece POR ÚLTIMO. "1.234,56" ou "1,234.56".
            if (limpo.lastIndexOf(',') > limpo.lastIndexOf('.')) {
                limpo = limpo.replace(".", "").replace(',', '.');
            } else {
                limpo = limpo.replace(",", "");
            }
        } else if (temVirgula) {
            limpo = limpo.replace(',', '.');
        } else if (temPonto) {
            int ultimo = limpo.lastIndexOf('.');
            int digitosDepois = limpo.length() - ultimo - 1;
            boolean pontoDeMilhar = limpo.indexOf('.') != ultimo || digitosDepois == 3;
            if (pontoDeMilhar) {
                limpo = limpo.replace(".", "");
            }
        }

        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Valor inválido em \"" + nomeCampo + "\": " + texto, e);
        }
    }

    /**
     * Converte texto em inteiro não-negativo. Aceita "10", "10,0" e "10.00"
     * (o Excel costuma devolver quantidades como decimais).
     *
     * @return {@code null} quando o texto está vazio
     * @throws IllegalArgumentException se não for inteiro ou for negativo
     */
    public static Integer parseInteiro(String texto, String nomeCampo) {
        BigDecimal valor = parseDecimal(texto, nomeCampo);
        if (valor == null) {
            return null;
        }
        if (valor.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "\"" + nomeCampo + "\" deve ser um número inteiro (recebido: " + texto + ").");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(
                    "\"" + nomeCampo + "\" não pode ser negativo (recebido: " + texto + ").");
        }
        return valor.intValueExact();
    }
}
