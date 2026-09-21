package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.CompraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão do bug em produção: cadastrar uma pré-compra e depois visitar o
 * módulo de Compras derrubava a tela com
 * {@code LazyInitializationException: Compra.itens (no session)}.
 *
 * <p>Causa: {@code open-in-view=false} fecha a sessão do Hibernate antes do
 * Thymeleaf renderizar; {@code compras/lista.html} lê
 * {@code #lists.size(c.itens)} por linha e {@code compras/detalhes.html} lê
 * {@code i.material.nome}, e nenhuma das consultas carregava essas coleções.
 * Só reproduz com pelo menos UMA compra com item no banco — por isso não foi
 * pego pelo {@code BancoVazioSmokeTest} (banco vazio nunca itera a coleção).</p>
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class CompraListagemLazyTest {

    @Autowired WebApplicationContext contexto;
    @Autowired CompraService compraService;
    @Autowired AreaRepository areaRepository;
    @Autowired SubcategoriaRepository subcategoriaRepository;
    @Autowired MaterialRepository materialRepository;
    @Autowired CompraRepository compraRepository;

    private MockMvc mvc;
    private Long compraId;

    @BeforeEach
    void montarCenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Limpeza explícita: o contexto Spring (e o H2 dele) é reaproveitado
        // entre classes de teste com a mesma configuração, então cada método
        // aqui recriaria "Escritório"/"ESC" e colidiria com a UNIQUE constraint
        // se o cenário anterior não fosse removido primeiro.
        compraRepository.deleteAll();
        materialRepository.deleteAll();
        subcategoriaRepository.deleteAll();
        areaRepository.deleteAll();

        Area area = areaRepository.save(Area.builder().nome("Escritório CompraTest").sigla("ESCCT").build());
        Subcategoria sub = subcategoriaRepository.save(Subcategoria.builder()
                .nome("Papelaria CompraTest").sigla("PAPCT").area(area).proximoSequencial(1).build());
        Material material = materialRepository.save(Material.builder()
                .nome("Papel A4").estoqueAtual(0).estoqueMinimo(5).subcategoria(sub).build());

        // Mesmo caminho que o usuário usa na tela "Nova compra".
        Compra compra = Compra.builder()
                .dataSolicitacao(LocalDateTime.now())
                .tipo(TipoCompra.ESTOQUE)
                .valorEstimado(BigDecimal.valueOf(100))
                .build();
        ItemCompra item = ItemCompra.builder()
                .material(material)
                .quantidade(10)
                .valorUnitario(BigDecimal.TEN)
                .build();

        Compra salva = compraService.criarPreCompra(compra, List.of(item), null);
        compraId = salva.getId();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listagemGeral_naoQuebraComCompraTendoItens() throws Exception {
        mvc.perform(get("/compras"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("LazyInitializationException"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void filaAguardando_naoQuebraComCompraTendoItens() throws Exception {
        mvc.perform(get("/compras/aguardando")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detalhes_naoQuebraELeItemComMaterial() throws Exception {
        mvc.perform(get("/compras/" + compraId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Papel A4")));
    }

    @Test
    void repositorio_carregaItensDaPaginaSemLazyException() {
        var page = compraRepository.findAll(PageRequest.of(0, 20));
        var comItens = compraRepository.carregarItens(page.getContent());
        assertThat(comItens).isNotEmpty();
        assertThat(comItens.get(0).getItens()).isNotEmpty();
        assertThat(comItens.get(0).getItens().get(0).getMaterial().getNome()).isEqualTo("Papel A4");
    }
}
