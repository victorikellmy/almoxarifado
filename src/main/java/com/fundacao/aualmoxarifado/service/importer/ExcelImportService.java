package com.fundacao.aualmoxarifado.service.importer;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;

/**
 * Importação em lote de materiais a partir de planilha XLSX.
 *
 * <p>Layout esperado (linha 1 = cabeçalho, ignorada):</p>
 * <pre>
 *   A: nome              (obrigatório)
 *   B: unidade           (ex.: Caixa, Unidade)
 *   C: estoque atual     (numérico, default 0)
 *   D: estoque mínimo    (numérico, default 0)
 *   E: valor unitário    (decimal, opcional)
 *   F: sigla da Área     (obrigatório — Área já deve existir)
 *   G: sigla da Subcategoria (obrigatório — Subcategoria já deve existir dentro da Área)
 * </pre>
 *
 * <p>O <b>SKU</b> é gerado automaticamente pelo {@code MaterialService.salvar()}
 * (RF18 — formato {@code AREA-SUB-NNNNN}). Não vem da planilha.</p>
 *
 * <p>Linhas sem nome ou sem Área/Subcategoria válida são ignoradas com aviso.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final MaterialService materialService;

    @Transactional
    public ImportResult importarMateriais(InputStream input) {
        ImportResult result = new ImportResult();
        try (Workbook wb = new XSSFWorkbook(input)) {
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                result.contarLinha();
                processarLinha(row, i + 1, result);
            }
        } catch (Exception e) {
            log.error("Falha ao importar planilha", e);
            throw new RegraDeNegocioException("Falha ao ler planilha: " + e.getMessage());
        }
        return result;
    }

    private void processarLinha(Row row, int numLinha, ImportResult result) {
        String nome = CellReader.string(row, 0);
        if (nome == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": nome em branco — ignorada.");
            return;
        }

        String unidade   = CellReader.string(row, 1);
        Integer eAtual   = CellReader.integer(row, 2);
        Integer eMinimo  = CellReader.integer(row, 3);
        BigDecimal valor = CellReader.decimal(row, 4);
        String siglaArea = Normalizadores.upperSemAcento(CellReader.string(row, 5));
        String siglaSub  = Normalizadores.upperSemAcento(CellReader.string(row, 6));

        if (siglaArea == null || siglaSub == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": sigla da Área ou Subcategoria em branco — ignorada.");
            return;
        }

        Area area = areaRepository.findBySigla(siglaArea).orElse(null);
        if (area == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": Área '" + siglaArea + "' não encontrada — ignorada.");
            return;
        }

        Subcategoria sub = subcategoriaRepository.findByAreaAndSigla(area, siglaSub).orElse(null);
        if (sub == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": Subcategoria '" + siglaArea + "-" + siglaSub
                    + "' não encontrada — ignorada.");
            return;
        }

        Material novo = Material.builder()
                .nome(nome)
                .unidadeMedida(unidade)
                .estoqueAtual(eAtual  != null ? eAtual  : 0)
                .estoqueMinimo(eMinimo != null ? eMinimo : 0)
                .valorUnitario(valor)
                .subcategoria(sub)
                .build();

        try {
            materialService.salvar(novo);   // gera SKU automaticamente
            result.contarImportado();
        } catch (RuntimeException ex) {
            result.contarIgnorado();
            result.erro("Linha " + numLinha + ": " + ex.getMessage());
        }
    }
}
