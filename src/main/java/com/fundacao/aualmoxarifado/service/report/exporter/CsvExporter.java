package com.fundacao.aualmoxarifado.service.report.exporter;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exporta para CSV (UTF-8 com BOM, para abrir corretamente no Excel/PT-BR).
 * Usa separador {@code ;} (padrão brasileiro).
 */
@Component
public class CsvExporter implements Exporter {

    private static final char SEP = ';';

    @Override public String contentType()    { return "text/csv;charset=UTF-8"; }
    @Override public String fileExtension()  { return "csv"; }

    @Override
    public void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out)
            throws IOException {
        // Não fecha o stream: o contrato do Exporter diz que o chamador fecha —
        // essencial para permitir escrita direta no OutputStream da resposta HTTP.
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        // BOM UTF-8 para Excel
        w.write('﻿');

        // StringBuilder reutilizado: evita 1 Stream + 1 List + 1 String de join
        // por linha (pressão de GC pura em relatórios grandes).
        StringBuilder sb = new StringBuilder(256);
        escreverLinha(w, sb, cabecalhos);
        for (List<String> linha : linhas) {
            escreverLinha(w, sb, linha);
        }
        w.flush();
    }

    private static void escreverLinha(BufferedWriter w, StringBuilder sb, List<String> celulas)
            throws IOException {
        sb.setLength(0);
        for (int i = 0; i < celulas.size(); i++) {
            if (i > 0) sb.append(SEP);
            escapar(sb, celulas.get(i));
        }
        sb.append('\n');
        w.write(sb.toString());
    }

    private static void escapar(StringBuilder sb, String s) {
        if (s == null || s.isEmpty()) return;
        boolean precisa = s.indexOf(SEP) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0;
        if (!precisa) {
            sb.append(s);
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append('"');
            sb.append(c);
        }
        sb.append('"');
    }
}
