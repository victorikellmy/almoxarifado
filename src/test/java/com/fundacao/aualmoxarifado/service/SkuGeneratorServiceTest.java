package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.SkuGeneratorService.BlocoSku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkuGeneratorServiceTest {

    @Mock SubcategoriaRepository subcategoriaRepository;
    @InjectMocks SkuGeneratorService service;

    private Subcategoria sub;

    @BeforeEach
    void setUp() {
        Area area = Area.builder().id(1L).sigla("ODO").nome("Odontologia").build();
        sub = Subcategoria.builder()
                .id(5L).sigla("CON").nome("Consumo")
                .area(area).proximoSequencial(7)
                .build();
    }

    @Test
    void gerarParaSubcategoria_formataComZerosEIncrementaContador() {
        when(subcategoriaRepository.findByIdComLock(5L)).thenReturn(Optional.of(sub));
        when(subcategoriaRepository.saveAndFlush(sub)).thenReturn(sub);

        String sku = service.gerarParaSubcategoria(5L);

        assertThat(sku).isEqualTo("ODO-CON-00007");
        assertThat(sub.getProximoSequencial()).isEqualTo(8);
        verify(subcategoriaRepository).saveAndFlush(sub);
    }

    @Test
    void reservarBloco_reservaNSequenciaisNumaUnicaOperacao() {
        when(subcategoriaRepository.findByIdComLock(5L)).thenReturn(Optional.of(sub));
        when(subcategoriaRepository.saveAndFlush(sub)).thenReturn(sub);

        BlocoSku bloco = service.reservarBloco(5L, 10);

        assertThat(bloco.inicio()).isEqualTo(7);
        assertThat(bloco.sku(0)).isEqualTo("ODO-CON-00007");
        assertThat(bloco.sku(9)).isEqualTo("ODO-CON-00016");
        // O contador avança pelo bloco inteiro numa transação só — a próxima
        // reserva (ou geração unitária) começa depois do bloco.
        assertThat(sub.getProximoSequencial()).isEqualTo(17);
        verify(subcategoriaRepository).saveAndFlush(sub);
    }

    @Test
    void reservarBloco_quantidadeInvalida_rejeitaSemTocarNoBanco() {
        assertThatThrownBy(() -> service.reservarBloco(5L, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reservarBloco(5L, -3))
                .isInstanceOf(IllegalArgumentException.class);
        verify(subcategoriaRepository, never()).findByIdComLock(anyLong());
        verify(subcategoriaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservarBloco_subcategoriaInexistente_lancaIllegalArgument() {
        when(subcategoriaRepository.findByIdComLock(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservarBloco(99L, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }
}
