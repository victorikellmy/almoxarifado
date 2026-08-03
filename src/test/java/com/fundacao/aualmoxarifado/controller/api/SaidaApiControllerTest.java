package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.ItemSaidaDTO;
import com.fundacao.aualmoxarifado.dto.SaidaRequestDTO;
import com.fundacao.aualmoxarifado.dto.SaidaResponseDTO;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.exception.SkuFormatoInvalidoException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.IdempotencyService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa o controller sem subir Spring MVC — exercita o método como uma
 * função pura. Cobertura: sanitização de SKU, mapeamento de erros, fluxo
 * de sucesso, e a integração com {@link IdempotencyService} (replay).
 */
@ExtendWith(MockitoExtension.class)
class SaidaApiControllerTest {

    @Mock MovimentacaoService movimentacaoService;
    @Mock MaterialRepository materialRepository;
    @Mock SetorRepository setorRepository;

    SaidaApiController controller;

    private Setor setor;
    private Material caneta;

    @BeforeEach
    void setUp() {
        // IdempotencyService real — queremos testar o comportamento de replay.
        controller = new SaidaApiController(
                movimentacaoService, materialRepository, setorRepository,
                new IdempotencyService());

        setor = Setor.builder().id(10L).nome("TI").build();
        caneta = Material.builder()
                .id(1L)
                .nome("Caneta Azul")
                .codigoSku("PAP-ESC-00001")
                .estoqueAtual(50)
                .estoqueMinimo(10)
                .build();
    }

    @Test
    void registrarSaida_fluxoFeliz_retorna200ComMovimentacaoId() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("PAP-ESC-00001"))).thenReturn(List.of(caneta));
        when(movimentacaoService.registrarSaida(any(), any(), any(), anyList()))
                .thenReturn(Movimentacao.builder()
                        .id(123L)
                        .tipo(TipoMovimentacao.SAIDA)
                        .status(StatusMovimentacao.PENDENTE_APROVACAO)
                        .data(LocalDateTime.now())
                        .setorDestino(setor)
                        .build());

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("PAP-ESC-00001", 5)));

        ResponseEntity<SaidaResponseDTO> resp = controller.registrarSaida(req, "key-001");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().movimentacaoIds()).containsExactly(123L);
        assertThat(resp.getBody().nomeSetor()).isEqualTo("TI");
        assertThat(resp.getBody().totalItens()).isEqualTo(1);
    }

    @Test
    void registrarSaida_skuMinusculo_eUpcaseParaLookup() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("PAP-ESC-00001"))).thenReturn(List.of(caneta));
        when(movimentacaoService.registrarSaida(any(), any(), any(), anyList()))
                .thenReturn(movimentacaoDummy());

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("  pap-esc-00001  ", 5)));

        controller.registrarSaida(req, null);

        verify(materialRepository).findByCodigoSkuIn(List.of("PAP-ESC-00001"));
    }

    @Test
    void registrarSaida_skuComEspacosInternos_lancaSkuFormatoInvalido() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("BAD SKU", 1)));

        assertThatThrownBy(() -> controller.registrarSaida(req, null))
                .isInstanceOf(SkuFormatoInvalidoException.class)
                .hasMessageContaining("BAD SKU");
    }

    @Test
    void registrarSaida_setorInexistente_lancaRecursoNaoEncontrado() {
        when(setorRepository.findById(99L)).thenReturn(Optional.empty());

        SaidaRequestDTO req = new SaidaRequestDTO(99L, "joao",
                List.of(new ItemSaidaDTO("PAP-ESC-00001", 1)));

        assertThatThrownBy(() -> controller.registrarSaida(req, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void registrarSaida_skuNaoCadastrado_lancaRecursoNaoEncontrado() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("ZZZ-ZZZ-99999"))).thenReturn(List.of());

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("ZZZ-ZZZ-99999", 1)));

        assertThatThrownBy(() -> controller.registrarSaida(req, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("ZZZ-ZZZ-99999");
    }

    @Test
    void registrarSaida_propagaRegraDeNegocioDoService() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("PAP-ESC-00001"))).thenReturn(List.of(caneta));
        when(movimentacaoService.registrarSaida(any(), any(), any(), anyList()))
                .thenThrow(new RegraDeNegocioException("Estoque insuficiente para \"Caneta Azul\""));

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("PAP-ESC-00001", 5)));

        assertThatThrownBy(() -> controller.registrarSaida(req, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Estoque insuficiente");
    }

    @Test
    void registrarSaida_replayComMesmaChave_naoReexecutaService() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("PAP-ESC-00001"))).thenReturn(List.of(caneta));
        when(movimentacaoService.registrarSaida(any(), any(), any(), anyList()))
                .thenReturn(Movimentacao.builder()
                        .id(777L)
                        .data(LocalDateTime.now())
                        .setorDestino(setor)
                        .build());

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("PAP-ESC-00001", 5)));

        ResponseEntity<SaidaResponseDTO> primeira = controller.registrarSaida(req, "dedup-XYZ");
        ResponseEntity<SaidaResponseDTO> replay   = controller.registrarSaida(req, "dedup-XYZ");

        assertThat(primeira.getBody().movimentacaoIds()).containsExactly(777L);
        assertThat(replay.getBody()).isSameAs(primeira.getBody());

        verify(movimentacaoService, times(1))
                .registrarSaida(any(Setor.class), any(), any(), anyList());
    }

    @Test
    void registrarSaida_semChave_executaSempre() {
        when(setorRepository.findById(10L)).thenReturn(Optional.of(setor));
        when(materialRepository.findByCodigoSkuIn(List.of("PAP-ESC-00001"))).thenReturn(List.of(caneta));
        when(movimentacaoService.registrarSaida(any(), any(), any(), anyList()))
                .thenReturn(movimentacaoDummy());

        SaidaRequestDTO req = new SaidaRequestDTO(10L, "joao",
                List.of(new ItemSaidaDTO("PAP-ESC-00001", 5)));

        controller.registrarSaida(req, null);
        controller.registrarSaida(req, null);
        controller.registrarSaida(req, "  ");

        verify(movimentacaoService, times(3))
                .registrarSaida(any(Setor.class), any(), any(), anyList());
    }

    // ---------- helpers ----------

    private Movimentacao movimentacaoDummy() {
        return Movimentacao.builder()
                .id(1L)
                .tipo(TipoMovimentacao.SAIDA)
                .status(StatusMovimentacao.PENDENTE_APROVACAO)
                .data(LocalDateTime.now())
                .setorDestino(setor)
                .build();
    }
}
