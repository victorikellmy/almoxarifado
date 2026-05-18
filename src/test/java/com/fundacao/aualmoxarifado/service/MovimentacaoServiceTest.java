package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.MovimentacaoItem;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovimentacaoServiceTest {

    @Mock MovimentacaoRepository movimentacaoRepository;
    @Mock MaterialRepository materialRepository;
    @Mock AuditoriaService auditoriaService;

    @InjectMocks MovimentacaoService service;

    private Setor setor;
    private Material caneta;

    @BeforeEach
    void setUp() {
        setor = Setor.builder().id(10L).nome("TI").build();
        caneta = Material.builder()
                .id(1L)
                .nome("Caneta Azul")
                .codigoSku("PAP-ESC-00001")
                .estoqueAtual(50)
                .estoqueMinimo(10)
                .build();

        // save() devolve o próprio argumento — comportamento padrão de JPA.
        when(movimentacaoRepository.save(any(Movimentacao.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ============= registrarSaida =============

    @Test
    void registrarSaida_comSaldoSuficiente_criaMovimentacaoPendente() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(caneta));

        Movimentacao mov = service.registrarSaida(
                setor, "joao.silva", null,
                List.of(new LinhaItem(1L, 5)));

        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(mov.getStatus())
                .as("RN04: saída nasce PENDENTE_APROVACAO; estoque não é debitado ainda")
                .isEqualTo(StatusMovimentacao.PENDENTE_APROVACAO);
        assertThat(mov.getSetorDestino()).isEqualTo(setor);
        assertThat(mov.getItens()).hasSize(1);
        assertThat(mov.getItens().get(0).getQuantidade()).isEqualTo(5);
        assertThat(caneta.getEstoqueAtual())
                .as("estoque NÃO debita no momento da saída — só após aprovação")
                .isEqualTo(50);

        verify(auditoriaService).registrarSaida(mov);
    }

    @Test
    void registrarSaida_comEstoqueInsuficiente_lancaRegraDeNegocio_eNaoAudita() {
        caneta.setEstoqueAtual(3);
        when(materialRepository.findById(1L)).thenReturn(Optional.of(caneta));

        assertThatThrownBy(() -> service.registrarSaida(
                setor, "joao.silva", null,
                List.of(new LinhaItem(1L, 10))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Estoque insuficiente")
                .hasMessageContaining("Caneta Azul");

        verify(movimentacaoRepository, never()).save(any());
        verify(auditoriaService, never()).registrarSaida(any());
    }

    @Test
    void registrarSaida_semSetor_lancaRegraDeNegocio_RN03() {
        assertThatThrownBy(() -> service.registrarSaida(
                null, "joao.silva", null,
                List.of(new LinhaItem(1L, 1))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("RN03");

        verify(movimentacaoRepository, never()).save(any());
        verify(auditoriaService, never()).registrarSaida(any());
    }

    @Test
    void registrarSaida_semItens_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.registrarSaida(
                setor, "joao.silva", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos um item");
    }

    @Test
    void registrarSaida_comQuantidadeZeroOuNegativa_rejeita() {
        when(materialRepository.findById(1L)).thenReturn(Optional.of(caneta));

        assertThatThrownBy(() -> service.registrarSaida(
                setor, "joao.silva", null,
                List.of(new LinhaItem(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que zero");
    }

    // ============= alterarStatus =============

    @Test
    void alterarStatus_pendenteParaAprovado_debitaEstoque_eAudita() {
        Movimentacao saida = saidaPendenteCom(caneta, 5);
        when(movimentacaoRepository.findById(99L)).thenReturn(Optional.of(saida));

        Movimentacao resultado = service.alterarStatus(99L, StatusMovimentacao.APROVADO);

        assertThat(resultado.getStatus()).isEqualTo(StatusMovimentacao.APROVADO);
        assertThat(caneta.getEstoqueAtual())
                .as("transição PENDENTE → APROVADO debita o estoque (RN04)")
                .isEqualTo(45);
        verify(materialRepository).save(caneta);
        verify(auditoriaService).alterarStatus(resultado);
    }

    @Test
    void alterarStatus_idempotente_naoDebitaDuasVezes() {
        Movimentacao saida = saidaPendenteCom(caneta, 5);
        when(movimentacaoRepository.findById(99L)).thenReturn(Optional.of(saida));

        // Primeira transição: PENDENTE → APROVADO. Debita.
        service.alterarStatus(99L, StatusMovimentacao.APROVADO);
        assertThat(caneta.getEstoqueAtual()).isEqualTo(45);

        // Segunda chamada com o mesmo status: no-op.
        service.alterarStatus(99L, StatusMovimentacao.APROVADO);
        assertThat(caneta.getEstoqueAtual())
                .as("aprovação repetida não pode debitar de novo")
                .isEqualTo(45);

        // Transição APROVADO → ENTREGUE: já não está pendente, não debita.
        service.alterarStatus(99L, StatusMovimentacao.ENTREGUE);
        assertThat(caneta.getEstoqueAtual())
                .as("APROVADO → ENTREGUE não debita (estoque já foi)")
                .isEqualTo(45);

        verify(materialRepository, times(1)).save(caneta);
    }

    @Test
    void alterarStatus_quandoEstoqueSumiuEntreRegistroEAprovacao_falha() {
        Movimentacao saida = saidaPendenteCom(caneta, 5);
        caneta.setEstoqueAtual(2); // outra operação consumiu o estoque depois
        when(movimentacaoRepository.findById(99L)).thenReturn(Optional.of(saida));

        assertThatThrownBy(() ->
                service.alterarStatus(99L, StatusMovimentacao.APROVADO))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("estoque insuficiente");

        assertThat(caneta.getEstoqueAtual()).isEqualTo(2);
        verify(auditoriaService, never()).alterarStatus(any());
    }

    // ---------- helpers ----------

    private Movimentacao saidaPendenteCom(Material material, int qtd) {
        Movimentacao mov = Movimentacao.builder()
                .id(99L)
                .tipo(TipoMovimentacao.SAIDA)
                .status(StatusMovimentacao.PENDENTE_APROVACAO)
                .setorDestino(setor)
                .build();
        MovimentacaoItem item = MovimentacaoItem.builder()
                .material(material)
                .quantidade(qtd)
                .build();
        mov.adicionarItem(item);
        return mov;
    }

    @SuppressWarnings("unused")
    private ArgumentCaptor<Movimentacao> captureSave() {
        ArgumentCaptor<Movimentacao> captor = ArgumentCaptor.forClass(Movimentacao.class);
        verify(movimentacaoRepository).save(captor.capture());
        return captor;
    }
}
