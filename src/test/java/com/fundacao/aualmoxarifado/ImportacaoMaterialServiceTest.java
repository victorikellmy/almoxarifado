package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.ModoEstoqueImportacao;
import com.fundacao.aualmoxarifado.dto.ResultadoImportacao;
import com.fundacao.aualmoxarifado.dto.ResultadoLinhaImportacao;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.ImportacaoMaterialService;
import com.fundacao.aualmoxarifado.service.ModeloPlanilhaMaterialService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes de integração do importador de materiais por planilha.
 *
 * Roda com o perfil "test" para que o {@code DataSeeder} (que é @Profile("dev"))
 * não polua o banco em memória.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.show-sql=false"
})
class ImportacaoMaterialServiceTest {

    private static final String[] CABECALHO = {
            "nome", "area", "area_sigla", "subcategoria", "subcategoria_sigla",
            "unidade", "estoque_atual", "estoque_minimo", "valor_unitario", "sku"
    };

    @Autowired private ImportacaoMaterialService importacao;
    @Autowired private ModeloPlanilhaMaterialService modelo;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private SubcategoriaRepository subcategoriaRepository;
    @Autowired private MovimentacaoRepository movimentacaoRepository;

    @BeforeEach
    void limpar() {
        movimentacaoRepository.deleteAll();
        materialRepository.deleteAll();
        subcategoriaRepository.deleteAll();
        areaRepository.deleteAll();
    }

    // =====================================================================
    // Simulação
    // =====================================================================

    @Test
    void simulacaoNaoGravaNada() {
        MockMultipartFile arquivo = xlsx(List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "50", "10", "24,90", "")
        ));

        ResultadoImportacao r = importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, true);

        assertThat(r.isSimulacao()).isTrue();
        assertThat(r.getTotalCriados()).isEqualTo(1);
        assertThat(r.getTotalErros()).isZero();

        assertThat(materialRepository.count()).isZero();
        assertThat(areaRepository.count()).isZero();
        assertThat(subcategoriaRepository.count()).isZero();
    }

    // =====================================================================
    // Criação
    // =====================================================================

    @Test
    void criaHierarquiaMaterialESkuNaImportacaoReal() {
        MockMultipartFile arquivo = xlsx(List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "50", "10", "24,90", ""),
                List.of("Caneta Azul",      "Escritório", "ESC", "Papelaria", "PAP", "UN",    "200", "50", "1,80", "")
        ));

        ResultadoImportacao r = importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalErros()).isZero();
        assertThat(r.getTotalCriados()).isEqualTo(2);

        // Área e subcategoria criadas UMA vez só, reaproveitadas pela 2ª linha.
        assertThat(areaRepository.count()).isEqualTo(1);
        assertThat(subcategoriaRepository.count()).isEqualTo(1);

        Material papel = materialRepository.findByCodigoSku("ESC-PAP-00001").orElseThrow();
        assertThat(papel.getNome()).isEqualTo("Papel Sulfite A4");
        assertThat(papel.getUnidadeMedida()).isEqualTo("RESMA");
        assertThat(papel.getEstoqueMinimo()).isEqualTo(10);
        assertThat(papel.getValorUnitario()).isEqualByComparingTo(new BigDecimal("24.90"));
        assertThat(papel.getEstoqueAtual()).isEqualTo(50);

        assertThat(materialRepository.findByCodigoSku("ESC-PAP-00002")).isPresent();

        // Modo SOMAR gera histórico de ENTRADA (RN05).
        List<Movimentacao> entradas = movimentacaoRepository.findAll();
        assertThat(entradas).hasSize(2)
                .allSatisfy(m -> assertThat(m.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA));
    }

    @Test
    void derivaSiglaQuandoNaoInformada() {
        MockMultipartFile arquivo = xlsx(List.of(
                List.of("Luva M", "Odontologia", "", "Descartáveis", "", "CX", "30", "8", "32,50", "")
        ));

        importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(areaRepository.findBySiglaIgnoreCase("ODO")).isPresent();
        assertThat(materialRepository.findByCodigoSku("ODO-DES-00001")).isPresent();
    }

    @Test
    void recusaAreaDesconhecidaQuandoNaoAutorizadoCriar() {
        MockMultipartFile arquivo = xlsx(List.of(
                List.of("Papel A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "10", "2", "20,00", "")
        ));

        ResultadoImportacao r = importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, false, false);

        assertThat(r.getTotalErros()).isEqualTo(1);
        assertThat(r.getLinhasComErro().getFirst().getMensagem()).contains("não está cadastrada");
        assertThat(materialRepository.count()).isZero();
    }

    // =====================================================================
    // Atualização / idempotência
    // =====================================================================

    @Test
    void reenviarOMesmoArquivoAtualizaEmVezDeDuplicar() {
        List<List<String>> linhas = List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "50", "10", "24,90", "")
        );

        importacao.importar(xlsx(linhas), ModoEstoqueImportacao.SOMAR, true, false);
        ResultadoImportacao segunda = importacao.importar(xlsx(linhas), ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(segunda.getTotalAtualizados()).isEqualTo(1);
        assertThat(segunda.getTotalCriados()).isZero();
        assertThat(materialRepository.count()).isEqualTo(1);

        // SOMAR acumulou: 50 + 50.
        assertThat(materialRepository.findByCodigoSku("ESC-PAP-00001").orElseThrow()
                .getEstoqueAtual()).isEqualTo(100);
    }

    @Test
    void modoDefinirSobrescreveSaldoSemGerarMovimentacao() {
        importacao.importar(xlsx(List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "50", "10", "24,90", "")
        )), ModoEstoqueImportacao.SOMAR, true, false);

        long movimentacoesAntes = movimentacaoRepository.count();

        importacao.importar(xlsx(List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "12", "10", "24,90", "")
        )), ModoEstoqueImportacao.DEFINIR, true, false);

        assertThat(materialRepository.findByCodigoSku("ESC-PAP-00001").orElseThrow()
                .getEstoqueAtual()).isEqualTo(12);
        assertThat(movimentacaoRepository.count()).isEqualTo(movimentacoesAntes);
    }

    @Test
    void atualizaPorSkuESoAlteraCamposPreenchidos() {
        importacao.importar(xlsx(List.of(
                List.of("Papel Sulfite A4", "Escritório", "ESC", "Papelaria", "PAP", "RESMA", "50", "10", "24,90", "")
        )), ModoEstoqueImportacao.SOMAR, true, false);

        // Só estoque e valor; unidade e mínimo ficam em branco de propósito.
        ResultadoImportacao r = importacao.importar(xlsx(List.of(
                List.of("", "", "", "", "", "", "5", "", "25,40", "ESC-PAP-00001")
        )), ModoEstoqueImportacao.SOMAR, false, false);

        assertThat(r.getTotalAtualizados()).isEqualTo(1);

        Material papel = materialRepository.findByCodigoSku("ESC-PAP-00001").orElseThrow();
        assertThat(papel.getEstoqueAtual()).isEqualTo(55);
        assertThat(papel.getValorUnitario()).isEqualByComparingTo(new BigDecimal("25.40"));
        assertThat(papel.getNome()).isEqualTo("Papel Sulfite A4");   // preservado
        assertThat(papel.getUnidadeMedida()).isEqualTo("RESMA");     // preservado
        assertThat(papel.getEstoqueMinimo()).isEqualTo(10);          // preservado
    }

    @Test
    void skuInexistenteViraErroDaLinha() {
        ResultadoImportacao r = importacao.importar(xlsx(List.of(
                List.of("Qualquer", "", "", "", "", "", "5", "", "", "XXX-YYY-99999")
        )), ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalErros()).isEqualTo(1);
        assertThat(r.getLinhasComErro().getFirst().getMensagem()).contains("não existe no sistema");
    }

    // =====================================================================
    // Isolamento de erros
    // =====================================================================

    @Test
    void linhaInvalidaNaoImpedeAsDemais() {
        ResultadoImportacao r = importacao.importar(xlsx(List.of(
                List.of("Item bom 1",  "Escritório", "ESC", "Papelaria", "PAP", "UN", "10",    "1", "2,00", ""),
                List.of("Item ruim",   "Escritório", "ESC", "Papelaria", "PAP", "UN", "dez",   "1", "2,00", ""),
                List.of("",            "Escritório", "ESC", "Papelaria", "PAP", "UN", "5",     "1", "2,00", ""),
                List.of("Item bom 2",  "Escritório", "ESC", "Papelaria", "PAP", "UN", "20",    "1", "3,00", "")
        )), ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalLinhas()).isEqualTo(4);
        assertThat(r.getTotalCriados()).isEqualTo(2);
        assertThat(r.getTotalErros()).isEqualTo(2);
        assertThat(materialRepository.count()).isEqualTo(2);

        List<String> mensagens = r.getLinhasComErro().stream()
                .map(ResultadoLinhaImportacao::getMensagem).toList();
        assertThat(mensagens).anySatisfy(m -> assertThat(m).contains("estoque_atual"));
        assertThat(mensagens).anySatisfy(m -> assertThat(m).contains("\"nome\" é obrigatória"));
    }

    @Test
    void avisaSobreLinhaDuplicadaNoArquivo() {
        ResultadoImportacao r = importacao.importar(xlsx(List.of(
                List.of("Papel A4", "Escritório", "ESC", "Papelaria", "PAP", "UN", "10", "1", "2,00", ""),
                List.of("Papel A4", "Escritório", "ESC", "Papelaria", "PAP", "UN", "15", "1", "2,00", "")
        )), ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(materialRepository.count()).isEqualTo(1);
        assertThat(r.getLinhas().get(1).getAvisos())
                .anySatisfy(a -> assertThat(a).contains("repetido no arquivo"));
        assertThat(materialRepository.findByCodigoSku("ESC-PAP-00001").orElseThrow()
                .getEstoqueAtual()).isEqualTo(25);
    }

    // =====================================================================
    // CSV
    // =====================================================================

    @Test
    void leCsvComPontoEVirgulaEAcentoEmAnsi() {
        String csv = """
                nome;area;subcategoria;unidade;estoque_atual;estoque_minimo;valor_unitario
                Álcool 70% 1L;Limpeza;Higienização;UN;24;6;9,75
                """;
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "estoque.csv", "text/csv", csv.getBytes(Charset.forName("windows-1252")));

        ResultadoImportacao r = importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalErros()).isZero();
        Material alcool = materialRepository.findAll().getFirst();
        assertThat(alcool.getNome()).isEqualTo("Álcool 70% 1L");
        assertThat(alcool.getEstoqueAtual()).isEqualTo(24);
        assertThat(alcool.getValorUnitario()).isEqualByComparingTo(new BigDecimal("9.75"));
    }

    @Test
    void aceitaCabecalhosComAcentoMaiusculaEApelidos() {
        String csv = """
                DESCRIÇÃO,Categoria,Sub Categoria,Unidade de Medida,QTD,Qtd Mínima,Preço Unitário,Observação
                Máscara N95,EPI,Proteção Respiratória,CX,40,10,"1.234,56",comprado em maio
                """;
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "estoque.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ResultadoImportacao r = importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalErros()).isZero();
        assertThat(r.getColunasIgnoradas()).contains("observacao");

        Material mascara = materialRepository.findAll().getFirst();
        assertThat(mascara.getNome()).isEqualTo("Máscara N95");
        assertThat(mascara.getEstoqueAtual()).isEqualTo(40);
        assertThat(mascara.getValorUnitario()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void rejeitaArquivoSemColunaNome() {
        String csv = "coisa;outra\nabc;def\n";
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "x.csv", "text/csv", csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coluna \"nome\"");
    }

    @Test
    void rejeitaExtensaoNaoSuportada() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "arquivo", "lista.pdf", "application/pdf", "conteudo".getBytes());

        assertThatThrownBy(() -> importacao.importar(arquivo, ModoEstoqueImportacao.SOMAR, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato não suportado");
    }

    // =====================================================================
    // Planilha-modelo
    // =====================================================================

    @Test
    void planilhaModeloEhImportavelDepoisDePreenchida() throws IOException {
        // A aba 1 ("Materiais") vem só com o cabeçalho — importá-la deve
        // reclamar de "sem linhas", provando que os exemplos não vazam.
        MockMultipartFile modeloVazio = new MockMultipartFile(
                "arquivo", "modelo.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                modelo.gerar());

        assertThatThrownBy(() -> importacao.importar(modeloVazio, ModoEstoqueImportacao.SOMAR, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nenhuma linha de dados");

        // Agora preenchendo a primeira aba com as linhas de exemplo do próprio
        // modelo: elas precisam importar sem erro (a última é uma atualização).
        byte[] preenchido = preencherPrimeiraAba(modelo.gerar(),
                ModeloPlanilhaMaterialService.LINHAS_EXEMPLO);

        ResultadoImportacao r = importacao.importar(new MockMultipartFile(
                        "arquivo", "modelo.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", preenchido),
                ModoEstoqueImportacao.SOMAR, true, false);

        assertThat(r.getTotalErros()).isZero();
        assertThat(r.getTotalCriados()).isEqualTo(4);
        assertThat(r.getTotalAtualizados()).isEqualTo(1);

        Optional<Material> papel = materialRepository.findByCodigoSku("ESC-PAP-00001");
        assertThat(papel).isPresent();
        assertThat(papel.get().getEstoqueAtual()).isEqualTo(150);   // 50 + 100 da linha de atualização
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Monta um .xlsx em memória com o cabeçalho padrão e as linhas informadas. */
    private MockMultipartFile xlsx(List<List<String>> linhas) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Materiais");
            Row cabecalho = sheet.createRow(0);
            for (int i = 0; i < CABECALHO.length; i++) {
                cabecalho.createCell(i).setCellValue(CABECALHO[i]);
            }
            int n = 1;
            for (List<String> linha : linhas) {
                Row row = sheet.createRow(n++);
                for (int i = 0; i < linha.size(); i++) {
                    row.createCell(i).setCellValue(linha.get(i));
                }
            }
            wb.write(out);
            return new MockMultipartFile("arquivo", "materiais.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] preencherPrimeiraAba(byte[] planilha, List<List<String>> linhas) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(planilha));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheetAt(0);
            int n = 1;
            for (List<String> linha : linhas) {
                Row row = sheet.createRow(n++);
                for (int i = 0; i < linha.size(); i++) {
                    row.createCell(i).setCellValue(linha.get(i));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
