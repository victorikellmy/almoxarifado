package com.fundacao.aualmoxarifado.service.importer;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.SkuGeneratorService;
import com.fundacao.aualmoxarifado.service.SkuGeneratorService.BlocoSku;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExcelImportServiceTest {

    @Mock AreaRepository areaRepository;
    @Mock SubcategoriaRepository subcategoriaRepository;
    @Mock MaterialRepository materialRepository;
    @Mock SkuGeneratorService skuGeneratorService;

    @InjectMocks ExcelImportService service;

    private Subcategoria subConsumo;

    @BeforeEach
    void setUp() {
        Area odo = Area.builder().id(1L).sigla("ODO").nome("Odontologia").build();
        subConsumo = Subcategoria.builder()
                .id(5L).sigla("CON").nome("Consumo").area(odo).proximoSequencial(7)
                .build();

        when(areaRepository.findAll()).thenReturn(List.of(odo));
        when(subcategoriaRepository.findAll()).thenReturn(List.of(subConsumo));
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Monta um .xlsx em memória com header + linhas (colunas A..G do layout). */
    private InputStream planilha(Object[][] linhas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("import");
            Row header = sheet.createRow(0);
            String[] cab = {"nome", "unidade", "estoque", "minimo", "valor", "area", "sub"};
            for (int c = 0; c < cab.length; c++) header.createCell(c).setCellValue(cab[c]);

            int r = 1;
            for (Object[] l : linhas) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < l.length; c++) {
                    if (l[c] == null) continue;
                    if (l[c] instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
                    else row.createCell(c).setCellValue(l[c].toString());
                }
            }
            wb.write(bos);
            return new ByteArrayInputStream(bos.toByteArray());
        }
    }

    @Test
    void importa_linhasValidas_comSkusDoBlocoReservado() throws Exception {
        when(skuGeneratorService.reservarBloco(5L, 2)).thenReturn(new BlocoSku("ODO", "CON", 7));

        ImportResult result = service.importarMateriais(planilha(new Object[][]{
                {"Resina A2", "Unidade", 10, 5, 25.90, "ODO", "CON"},
                {"Adesivo 5ml", "Frasco", 3, 2, 90.00, "ODO", "CON"},
        }));

        assertThat(result.getTotalLinhas()).isEqualTo(2);
        assertThat(result.getImportados()).isEqualTo(2);
        assertThat(result.getIgnorados()).isZero();
        assertThat(result.getErros()).isEmpty();

        // Um único bloco reservado para a subcategoria (e não 1 lock por linha).
        verify(skuGeneratorService, times(1)).reservarBloco(5L, 2);

        ArgumentCaptor<Material> captor = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository, times(2)).save(captor.capture());
        List<Material> salvos = captor.getAllValues();
        assertThat(salvos.get(0).getCodigoSku()).isEqualTo("ODO-CON-00007");
        assertThat(salvos.get(1).getCodigoSku()).isEqualTo("ODO-CON-00008");
        assertThat(salvos.get(0).getNome()).isEqualTo("Resina A2");
        assertThat(salvos.get(0).getEstoqueAtual()).isEqualTo(10);
        assertThat(salvos.get(0).getSubcategoria()).isSameAs(subConsumo);
    }

    @Test
    void ignoraLinhas_semNome_areaOuSubcategoriaDesconhecida_comAvisos() throws Exception {
        when(skuGeneratorService.reservarBloco(anyLong(), anyInt()))
                .thenReturn(new BlocoSku("ODO", "CON", 7));

        ImportResult result = service.importarMateriais(planilha(new Object[][]{
                {null, "Unidade", 1, 1, null, "ODO", "CON"},          // sem nome
                {"Item X", "Unidade", 1, 1, null, "XXX", "CON"},      // área inexistente
                {"Item Y", "Unidade", 1, 1, null, "ODO", "ZZZ"},      // sub inexistente
                {"Item OK", "Unidade", 1, 1, null, "ODO", "CON"},     // válida
        }));

        assertThat(result.getTotalLinhas()).isEqualTo(4);
        assertThat(result.getImportados()).isEqualTo(1);
        assertThat(result.getIgnorados()).isEqualTo(3);
        assertThat(result.getAvisos())
                .anySatisfy(a -> assertThat(a).contains("nome em branco"))
                .anySatisfy(a -> assertThat(a).contains("XXX"))
                .anySatisfy(a -> assertThat(a).contains("ZZZ"));

        verify(skuGeneratorService, times(1)).reservarBloco(5L, 1);
    }

    @Test
    void planilhaSemLinhasValidas_naoReservaBlocoNemSalva() throws Exception {
        ImportResult result = service.importarMateriais(planilha(new Object[][]{
                {null, null, null, null, null, null, null},
        }));

        assertThat(result.getImportados()).isZero();
        verify(skuGeneratorService, never()).reservarBloco(anyLong(), anyInt());
        verify(materialRepository, never()).save(any());
    }

    @Test
    void siglasComMinusculasEAcentos_saoNormalizadasParaLookup() throws Exception {
        when(skuGeneratorService.reservarBloco(5L, 1)).thenReturn(new BlocoSku("ODO", "CON", 1));

        ImportResult result = service.importarMateriais(planilha(new Object[][]{
                {"Item Normalizado", "Unidade", 1, 1, null, "odo", "con"},
        }));

        assertThat(result.getImportados()).isEqualTo(1);
    }
}
