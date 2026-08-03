package com.fundacao.aualmoxarifado.service.importer;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.SkuGeneratorService;
import com.fundacao.aualmoxarifado.service.SkuGeneratorService.BlocoSku;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * <p>O <b>SKU</b> é gerado automaticamente (RF18 — formato {@code AREA-SUB-NNNNN}).
 * Não vem da planilha.</p>
 *
 * <p>Linhas sem nome ou sem Área/Subcategoria válida são ignoradas com aviso.</p>
 *
 * <p><b>Performance:</b> Áreas e Subcategorias são pré-carregadas em mapas
 * (2 queries no total, contra 2 por linha), e os SKUs são reservados em bloco
 * — uma única transação com lock por subcategoria distinta, contra uma
 * transação {@code REQUIRES_NEW} + {@code SELECT FOR UPDATE} por linha, que
 * serializava a importação inteira no lock da subcategoria.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final MaterialRepository materialRepository;
    private final SkuGeneratorService skuGeneratorService;

    /** Linha validada da planilha, aguardando SKU e persistência. */
    private record LinhaValida(int numLinha, Material material, Long subcategoriaId) {}

    @Transactional
    public ImportResult importarMateriais(InputStream input) {
        ImportResult result = new ImportResult();
        List<LinhaValida> validas = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(input)) {
            // Lookup O(1) em memória: catálogos pequenos, consultados por linha.
            Map<String, Area> areasPorSigla = carregarAreas();
            Map<String, Subcategoria> subsPorChave = carregarSubcategorias();

            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                result.contarLinha();
                LinhaValida linha = validarLinha(row, i + 1, result, areasPorSigla, subsPorChave);
                if (linha != null) {
                    validas.add(linha);
                }
            }
        } catch (Exception e) {
            log.error("Falha ao importar planilha", e);
            throw new RegraDeNegocioException("Falha ao ler planilha: " + e.getMessage(), e);
        }

        persistirComSkusEmBloco(validas, result);
        return result;
    }

    private Map<String, Area> carregarAreas() {
        Map<String, Area> mapa = new HashMap<>();
        for (Area a : areaRepository.findAll()) {
            mapa.put(Normalizadores.upperSemAcento(a.getSigla()), a);
        }
        return mapa;
    }

    private Map<String, Subcategoria> carregarSubcategorias() {
        Map<String, Subcategoria> mapa = new HashMap<>();
        for (Subcategoria s : subcategoriaRepository.findAll()) {
            // getId() no proxy lazy da área não dispara SELECT.
            mapa.put(chaveSub(s.getArea().getId(), Normalizadores.upperSemAcento(s.getSigla())), s);
        }
        return mapa;
    }

    private static String chaveSub(Long areaId, String siglaSub) {
        return areaId + "|" + siglaSub;
    }

    private LinhaValida validarLinha(Row row, int numLinha, ImportResult result,
                                     Map<String, Area> areasPorSigla,
                                     Map<String, Subcategoria> subsPorChave) {
        String nome = CellReader.string(row, 0);
        if (nome == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": nome em branco — ignorada.");
            return null;
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
            return null;
        }

        Area area = areasPorSigla.get(siglaArea);
        if (area == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": Área '" + siglaArea + "' não encontrada — ignorada.");
            return null;
        }

        Subcategoria sub = subsPorChave.get(chaveSub(area.getId(), siglaSub));
        if (sub == null) {
            result.contarIgnorado();
            result.aviso("Linha " + numLinha + ": Subcategoria '" + siglaArea + "-" + siglaSub
                    + "' não encontrada — ignorada.");
            return null;
        }

        Material novo = Material.builder()
                .nome(nome)
                .unidadeMedida(unidade)
                .estoqueAtual(eAtual  != null ? eAtual  : 0)
                .estoqueMinimo(eMinimo != null ? eMinimo : 0)
                .valorUnitario(valor)
                .subcategoria(sub)
                .build();

        return new LinhaValida(numLinha, novo, sub.getId());
    }

    /**
     * Reserva os sequenciais de SKU em bloco (1 transação lockada por
     * subcategoria distinta) e persiste os materiais.
     */
    private void persistirComSkusEmBloco(List<LinhaValida> validas, ImportResult result) {
        if (validas.isEmpty()) return;

        // Quantidade de SKUs necessária por subcategoria (ordem estável de chegada).
        Map<Long, Integer> qtdPorSub = new LinkedHashMap<>();
        for (LinhaValida l : validas) {
            qtdPorSub.merge(l.subcategoriaId(), 1, Integer::sum);
        }

        Map<Long, BlocoSku> blocos = new HashMap<>();
        Map<Long, Integer> offsets = new HashMap<>();
        for (Map.Entry<Long, Integer> e : qtdPorSub.entrySet()) {
            blocos.put(e.getKey(), skuGeneratorService.reservarBloco(e.getKey(), e.getValue()));
            offsets.put(e.getKey(), 0);
        }

        for (LinhaValida l : validas) {
            int offset = offsets.get(l.subcategoriaId());
            offsets.put(l.subcategoriaId(), offset + 1);
            l.material().setCodigoSku(blocos.get(l.subcategoriaId()).sku(offset));
            try {
                materialRepository.save(l.material());
                result.contarImportado();
            } catch (RuntimeException ex) {
                result.contarIgnorado();
                result.erro("Linha " + l.numLinha() + ": " + ex.getMessage());
            }
        }
    }
}
