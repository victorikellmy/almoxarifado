package com.fundacao.aualmoxarifado.service.report;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.service.report.exporter.CsvExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.Exporter;
import com.fundacao.aualmoxarifado.service.report.exporter.PdfExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.XlsxExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Geração de relatórios (estoque atual, alertas, consumo por setor)
 * em CSV, XLSX ou PDF.
 *
 * <p>Trabalha sobre a hierarquia <b>Area → Subcategoria → Material</b>
 * (RF17). O agrupamento por área/sub aparece nas colunas do estoque atual
 * e do relatório de alertas.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final MaterialRepository materialRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final CsvExporter  csvExporter;
    private final XlsxExporter xlsxExporter;
    private final PdfExporter  pdfExporter;

    public enum Formato { CSV, XLSX, PDF }

    public record Arquivo(byte[] conteudo, String contentType, String nome) {}

    // ---------- Relatório 1: estoque geral ----------
    public Arquivo estoqueAtual(Formato formato) {
        List<String> cabecalhos = List.of("SKU", "Material", "Área", "Subcategoria",
                "Unidade", "Estoque atual", "Estoque mínimo", "Valor unitário");
        List<List<String>> linhas = new ArrayList<>();
        for (Material m : materialRepository.findAll()) {
            linhas.add(List.of(
                    nv(m.getCodigoSku()),
                    nv(m.getNome()),
                    nomeArea(m),
                    nomeSubcategoria(m),
                    nv(m.getUnidadeMedida()),
                    String.valueOf(m.getEstoqueAtual()),
                    String.valueOf(m.getEstoqueMinimo()),
                    fmtMoeda(m.getValorUnitario())
            ));
        }
        return gerar("Estoque atual", cabecalhos, linhas, formato);
    }

    // ---------- Relatório 2: alertas de estoque mínimo ----------
    public Arquivo alertasEstoque(Formato formato) {
        List<String> cabecalhos = List.of("SKU", "Material", "Área", "Subcategoria",
                "Estoque atual", "Estoque mínimo", "Faltam (mín - atual)");
        List<List<String>> linhas = new ArrayList<>();
        for (Material m : materialRepository.findEmAlertaDeEstoque()) {
            int falta = Math.max(0, m.getEstoqueMinimo() - m.getEstoqueAtual());
            linhas.add(List.of(
                    nv(m.getCodigoSku()),
                    nv(m.getNome()),
                    nomeArea(m),
                    nomeSubcategoria(m),
                    String.valueOf(m.getEstoqueAtual()),
                    String.valueOf(m.getEstoqueMinimo()),
                    String.valueOf(falta)
            ));
        }
        return gerar("Alertas de estoque", cabecalhos, linhas, formato);
    }

    // ---------- Relatório 3: consumo por setor ----------
    public Arquivo consumoPorSetor(LocalDateTime inicio, LocalDateTime fim, Formato formato) {
        List<String> cabecalhos = List.of("Setor", "Centro de custo", "Total consumido");
        List<List<String>> linhas = new ArrayList<>();
        List<ConsumoSetorDTO> dados = movimentacaoRepository.consumoPorSetor(inicio, fim);
        for (ConsumoSetorDTO c : dados) {
            linhas.add(List.of(
                    nv(c.nomeSetor()),
                    nv(c.codigoCentroCusto()),
                    Objects.toString(c.totalConsumido(), "0")
            ));
        }
        return gerar("Consumo por setor", cabecalhos, linhas, formato);
    }

    // ---------- helpers ----------
    private Arquivo gerar(String titulo, List<String> cabecalhos,
                          List<List<String>> linhas, Formato formato) {
        Exporter exp = switch (formato) {
            case CSV  -> csvExporter;
            case XLSX -> xlsxExporter;
            case PDF  -> pdfExporter;
        };
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            exp.exportar(titulo, cabecalhos, linhas, bos);
            String nome = titulo.toLowerCase().replace(' ', '-') + "." + exp.fileExtension();
            return new Arquivo(bos.toByteArray(), exp.contentType(), nome);
        } catch (Exception e) {
            throw new RegraDeNegocioException("Falha ao gerar relatório: " + e.getMessage());
        }
    }

    private static String nomeSubcategoria(Material m) {
        return m.getSubcategoria() != null ? nv(m.getSubcategoria().getNome()) : "";
    }

    private static String nomeArea(Material m) {
        return (m.getSubcategoria() != null && m.getSubcategoria().getArea() != null)
                ? nv(m.getSubcategoria().getArea().getNome())
                : "";
    }

    private static String nv(String s) { return s == null ? "" : s; }

    private static String fmtMoeda(BigDecimal v) {
        if (v == null) return "";
        return "R$ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
