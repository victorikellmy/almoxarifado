package com.fundacao.aualmoxarifado.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Gera a planilha-modelo (.xlsx) da importação de materiais e expõe a
 * ESPECIFICAÇÃO das colunas.
 *
 * A lista {@link #COLUNAS} é a fonte única da verdade: dela saem tanto as
 * abas do arquivo baixado quanto a tabela de referência exibida na tela de
 * importação. Assim a documentação nunca fica dessincronizada do modelo.
 *
 * O arquivo gerado tem três abas:
 *   1. "Materiais"  — só o cabeçalho, pronta para o usuário preencher;
 *   2. "Exemplo"    — as mesmas colunas já preenchidas, para consulta;
 *   3. "Instruções" — o que cada coluna significa.
 *
 * O importador lê SEMPRE a primeira aba — por isso os exemplos ficam separados,
 * para ninguém importar "Papel Sulfite A4" sem querer.
 */
@Service
public class ModeloPlanilhaMaterialService {

    /** Uma coluna do modelo, com o texto de ajuda mostrado ao usuário. */
    public record EspecificacaoColuna(String nome,
                                      String obrigatoriedade,
                                      String descricao,
                                      String exemplo) {

        public boolean isObrigatoria() {
            return obrigatoriedade.startsWith("Sim");
        }
    }

    public static final List<EspecificacaoColuna> COLUNAS = List.of(
            new EspecificacaoColuna("nome", "Sim",
                    "Nome do material como aparecerá no catálogo.",
                    "Papel Sulfite A4 75g"),

            new EspecificacaoColuna("area", "Sim*",
                    "Área do material (nível 1). Aceita o nome completo ou a sigla de uma área já cadastrada.",
                    "Escritório"),

            new EspecificacaoColuna("area_sigla", "Não",
                    "Sigla de 2 a 5 letras/dígitos usada no início do SKU. "
                  + "Só é usada quando a área precisa ser criada; em branco, o sistema deriva do nome.",
                    "ESC"),

            new EspecificacaoColuna("subcategoria", "Sim*",
                    "Subcategoria dentro da área (nível 2). Aceita o nome ou a sigla.",
                    "Papelaria"),

            new EspecificacaoColuna("subcategoria_sigla", "Não",
                    "Sigla de 2 a 5 letras/dígitos usada no meio do SKU. Mesma regra da sigla de área.",
                    "PAP"),

            new EspecificacaoColuna("unidade", "Não",
                    "Unidade de medida do item.",
                    "RESMA"),

            new EspecificacaoColuna("estoque_atual", "Não",
                    "Quantidade em número inteiro. O efeito depende do modo de estoque escolhido no formulário.",
                    "50"),

            new EspecificacaoColuna("estoque_minimo", "Não",
                    "Ponto de reposição: abaixo disso o material entra no alerta de estoque baixo.",
                    "10"),

            new EspecificacaoColuna("valor_unitario", "Não",
                    "Valor unitário em reais. Aceita \"24,90\" ou \"24.90\".",
                    "24,90"),

            new EspecificacaoColuna("sku", "Não",
                    "Preencha SOMENTE para atualizar um material que já existe. "
                  + "Em branco, o sistema cadastra um item novo e gera o SKU sozinho.",
                    "ESC-PAP-00001")
    );

    /** Linhas de exemplo da aba "Exemplo" — mesma ordem de {@link #COLUNAS}. */
    public static final List<List<String>> LINHAS_EXEMPLO = List.of(
            List.of("Papel Sulfite A4 75g",       "Escritório",  "ESC", "Papelaria",     "PAP", "RESMA", "50",  "10", "24,90", ""),
            List.of("Caneta Esferográfica Azul",  "Escritório",  "ESC", "Papelaria",     "PAP", "UN",    "200", "50", "1,80",  ""),
            List.of("Luva de Procedimento M",     "Odontologia", "ODO", "Descartáveis",  "DES", "CX",    "30",  "8",  "32,50", ""),
            List.of("Álcool 70% 1L",              "Limpeza",     "LIM", "Higienização",  "HIG", "UN",    "24",  "6",  "9,75",  ""),
            List.of("Papel Sulfite A4 75g",       "",            "",    "",              "",    "",      "100", "20", "25,40", "ESC-PAP-00001")
    );

    private static final String NOTA_EXEMPLO =
            "A última linha mostra uma ATUALIZAÇÃO: com o SKU preenchido, o sistema encontra o "
          + "material existente e só altera os campos informados (área e subcategoria podem ficar em branco).";

    public byte[] gerar() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle estiloCabecalho = estiloCabecalho(wb);
            CellStyle estiloObrigatorio = estiloCabecalho(wb);
            estiloObrigatorio.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());

            criarAbaDePreenchimento(wb, estiloCabecalho, estiloObrigatorio);
            criarAbaDeExemplo(wb, estiloCabecalho);
            criarAbaDeInstrucoes(wb, estiloCabecalho);

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar a planilha-modelo.", e);
        }
    }

    // =====================================================================

    private void criarAbaDePreenchimento(Workbook wb, CellStyle padrao, CellStyle obrigatorio) {
        Sheet sheet = wb.createSheet("Materiais");
        Row cabecalho = sheet.createRow(0);

        for (int i = 0; i < COLUNAS.size(); i++) {
            EspecificacaoColuna col = COLUNAS.get(i);
            Cell cell = cabecalho.createCell(i);
            cell.setCellValue(col.nome());
            cell.setCellStyle(col.isObrigatoria() ? obrigatorio : padrao);
        }

        // Congela o cabeçalho: em uma planilha de 500 itens isso faz diferença.
        sheet.createFreezePane(0, 1);
        ajustarLarguras(sheet, COLUNAS.size());
    }

    private void criarAbaDeExemplo(Workbook wb, CellStyle padrao) {
        Sheet sheet = wb.createSheet("Exemplo");

        Row cabecalho = sheet.createRow(0);
        for (int i = 0; i < COLUNAS.size(); i++) {
            Cell cell = cabecalho.createCell(i);
            cell.setCellValue(COLUNAS.get(i).nome());
            cell.setCellStyle(padrao);
        }

        int linha = 1;
        for (List<String> exemplo : LINHAS_EXEMPLO) {
            Row row = sheet.createRow(linha++);
            for (int i = 0; i < exemplo.size(); i++) {
                row.createCell(i).setCellValue(exemplo.get(i));
            }
        }

        Row nota = sheet.createRow(linha + 1);
        Cell celulaNota = nota.createCell(0);
        celulaNota.setCellValue(NOTA_EXEMPLO);
        CellStyle estiloNota = wb.createCellStyle();
        estiloNota.setWrapText(true);
        celulaNota.setCellStyle(estiloNota);
        sheet.addMergedRegion(new CellRangeAddress(nota.getRowNum(), nota.getRowNum(), 0, COLUNAS.size() - 1));

        sheet.createFreezePane(0, 1);
        ajustarLarguras(sheet, COLUNAS.size());
    }

    private void criarAbaDeInstrucoes(Workbook wb, CellStyle padrao) {
        Sheet sheet = wb.createSheet("Instruções");

        CellStyle quebraLinha = wb.createCellStyle();
        quebraLinha.setWrapText(true);
        quebraLinha.setVerticalAlignment(VerticalAlignment.TOP);

        String[] titulos = {"Coluna", "Obrigatória", "O que preencher", "Exemplo"};
        Row cabecalho = sheet.createRow(0);
        for (int i = 0; i < titulos.length; i++) {
            Cell cell = cabecalho.createCell(i);
            cell.setCellValue(titulos[i]);
            cell.setCellStyle(padrao);
        }

        int linha = 1;
        for (EspecificacaoColuna col : COLUNAS) {
            Row row = sheet.createRow(linha++);
            row.createCell(0).setCellValue(col.nome());
            row.createCell(1).setCellValue(col.obrigatoriedade());
            Cell descricao = row.createCell(2);
            descricao.setCellValue(col.descricao());
            descricao.setCellStyle(quebraLinha);
            row.createCell(3).setCellValue(col.exemplo());
        }

        linha++;
        for (String texto : List.of(
                "* \"area\" e \"subcategoria\" são obrigatórias apenas quando a coluna \"sku\" está em branco (cadastro novo).",
                "Preencha apenas a primeira aba (\"Materiais\") — é ela que o sistema lê.",
                "A ordem das colunas não importa; o sistema se orienta pelos títulos da primeira linha.",
                "Colunas a mais são ignoradas sem quebrar a importação.",
                "Antes de importar de verdade, use o botão \"Simular\" para conferir o resultado sem gravar nada.")) {
            sheet.createRow(linha++).createCell(0).setCellValue(texto);
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 13 * 256);
        sheet.setColumnWidth(2, 85 * 256);
        sheet.setColumnWidth(3, 22 * 256);
    }

    private CellStyle estiloCabecalho(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setBold(true);

        CellStyle estilo = wb.createCellStyle();
        estilo.setFont(fonte);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }

    private void ajustarLarguras(Sheet sheet, int colunas) {
        for (int i = 0; i < colunas; i++) {
            sheet.autoSizeColumn(i);
            // autoSizeColumn às vezes deixa a coluna colada no texto; uma folga
            // de ~3 caracteres evita o "####" no Excel.
            sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 3 * 256, 60 * 256));
        }
    }
}
