package com.fundacao.aualmoxarifado.service.report.exporter;

import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exporta para CSV (UTF-8 com BOM, para abrir corretamente no Excel/PT-BR).
 * Usa separador {@code ;} (padrão brasileiro).
 */
@Component
public class CsvExporter implements Exporter {

    private static final String SEP = ";";

    @Override public String contentType()    { return "text/csv;charset=UTF-8"; }
    @Override public String fileExtension()  { return "csv"; }

    @Override
    public void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out) {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // BOM UTF-8 para Excel
            pw.write('﻿');
            pw.println(String.join(SEP, cabecalhos.stream().map(CsvExporter::escapar).toList()));
            for (List<String> linha : linhas) {
                pw.println(String.join(SEP, linha.stream().map(CsvExporter::escapar).toList()));
            }
        }
    }

    private static String escapar(String s) {
        if (s == null) return "";
        boolean precisa = s.contains(SEP) || s.contains("\"") || s.contains("\n");
        String v = s.replace("\"", "\"\"");
        return precisa ? "\"" + v + "\"" : v;
    }
}
