package com.fundacao.aualmoxarifado.service.report;

import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.ConsumoMaterialDTO;
import com.fundacao.aualmoxarifado.dto.GastoSetorDTO;
import com.fundacao.aualmoxarifado.dto.LinhaMesTipoDTO;
import com.fundacao.aualmoxarifado.dto.ResumoTipoDTO;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.service.report.RelatorioService.Arquivo;
import com.fundacao.aualmoxarifado.service.report.RelatorioService.DadosMensal;
import com.fundacao.aualmoxarifado.service.report.RelatorioService.DadosTrimestral;
import com.fundacao.aualmoxarifado.service.report.exporter.CsvExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.PdfExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.XlsxExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelatorioServiceTest {

    @Mock MaterialRepository materialRepository;
    @Mock MovimentacaoRepository movimentacaoRepository;

    private RelatorioService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioService(materialRepository, movimentacaoRepository,
                new CsvExporter(), new XlsxExporter(), new PdfExporter());
        // Em teste unitário não há proxy do Spring: a auto-referência usada
        // pelos exports para passar pelo cache aponta para a própria instância.
        ReflectionTestUtils.setField(service, "self", service);

        when(movimentacaoRepository.resumoPorTipo(any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.gastoPorSetor(any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.agregadoPorMesTipo(any(), any())).thenReturn(List.of());
        when(movimentacaoRepository.topMateriais(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void dadosRelatorioTrimestral_pivotaMesesComZeroFill_eCalculaTotais() {
        when(movimentacaoRepository.agregadoPorMesTipo(any(), any())).thenReturn(List.of(
                new LinhaMesTipoDTO(2026, 7, TipoMovimentacao.ENTRADA, 1L, 8L, new BigDecimal("2312.00")),
                new LinhaMesTipoDTO(2026, 7, TipoMovimentacao.SAIDA, 23L, 361L, new BigDecimal("12128.20"))
        ));

        DadosTrimestral d = service.dadosRelatorioTrimestral(2026, 3);

        // 3º trimestre = jul/ago/set — meses sem dados preenchidos com zeros
        assertThat(d.meses()).hasSize(3);
        assertThat(d.meses().get(0).mes()).isEqualTo(7);
        assertThat(d.meses().get(0).qtdEntradas()).isEqualTo(1);
        assertThat(d.meses().get(0).qtdSaidas()).isEqualTo(23);
        assertThat(d.meses().get(1).qtdTotalMovimentacoes()).isZero();
        assertThat(d.meses().get(2).valorTotal()).isEqualByComparingTo(BigDecimal.ZERO);

        // Totais pré-calculados (consumidos pelo tfoot dos templates)
        assertThat(d.totaisMeses().qtdEntradas()).isEqualTo(1);
        assertThat(d.totaisMeses().qtdSaidas()).isEqualTo(23);
        assertThat(d.totaisMeses().qtdTotalMovimentacoes()).isEqualTo(24);
        assertThat(d.totaisMeses().totalItens()).isEqualTo(369);
        assertThat(d.totaisMeses().valorTotal()).isEqualByComparingTo(new BigDecimal("14440.20"));
    }

    @Test
    void dadosRelatorioTrimestral_trimestreInvalido_lancaRegraDeNegocio() {
        assertThatThrownBy(() -> service.dadosRelatorioTrimestral(2026, 0))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.dadosRelatorioTrimestral(2026, 5))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void dadosRelatorioMensal_limitaTop10NoBanco_viaPageable() {
        when(movimentacaoRepository.topMateriais(any(), any(), any())).thenReturn(List.of(
                new ConsumoMaterialDTO(1L, "ODO-CON-00001", "Resina", "Unidade",
                        50L, new BigDecimal("100.00"))
        ));

        DadosMensal d = service.dadosRelatorioMensal(2026, 7);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(movimentacaoRepository).topMateriais(
                any(LocalDateTime.class), any(LocalDateTime.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize())
                .as("o LIMIT 10 deve descer para o banco, não ser aplicado em Java")
                .isEqualTo(10);
        assertThat(d.topMateriais()).hasSize(1);
        assertThat(d.nomeMes()).isEqualTo("Julho");
    }

    @Test
    void somarTipos_agregaResumoNoTotalGeral() {
        when(movimentacaoRepository.resumoPorTipo(any(), any())).thenReturn(List.of(
                new ResumoTipoDTO(TipoMovimentacao.ENTRADA, 2L, 10L, new BigDecimal("100.00")),
                new ResumoTipoDTO(TipoMovimentacao.SAIDA, 3L, 5L, new BigDecimal("50.50"))
        ));

        DadosMensal d = service.dadosRelatorioMensal(2026, 7);

        assertThat(d.totalGeral().qtdMovimentacoes()).isEqualTo(5);
        assertThat(d.totalGeral().totalItens()).isEqualTo(15);
        assertThat(d.totalGeral().valorTotal()).isEqualByComparingTo(new BigDecimal("150.50"));
    }

    @Test
    void relatorioMensal_geraArquivoCsvComNomeEContentType() {
        when(movimentacaoRepository.gastoPorSetor(any(), any())).thenReturn(List.of(
                new GastoSetorDTO(1L, "TI", "CC-020", 34L, new BigDecimal("1231.00"))
        ));

        Arquivo arq = service.relatorioMensal(2026, 7, RelatorioService.Formato.CSV);

        assertThat(arq.nome()).isEqualTo("relatorio-mensal-2026-07.csv");
        assertThat(arq.contentType()).startsWith("text/csv");
        // Arquivo agora expõe um writer (streaming) em vez de byte[]: materializa
        // num buffer só no teste para inspecionar o conteúdo.
        var bos = new java.io.ByteArrayOutputStream();
        try {
            arq.writer().writeTo(bos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String csv = bos.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Resumo por tipo");
        assertThat(csv).contains("TI [CC CC-020]");
    }
}
