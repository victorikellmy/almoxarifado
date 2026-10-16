package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.CompraService;
import com.fundacao.aualmoxarifado.service.CompraService.DadosRecebimento;
import com.fundacao.aualmoxarifado.service.integracao.PatrimonioIntegracaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Itens da pré-compra por tipo:
 *  - ESTOQUE: só material do catálogo (repõe o almoxarifado na baixa);
 *  - DIRETA/PATRIMONIAL: descrição livre + grupo/subgrupo escolhidos na compra,
 *    sem vínculo com o estoque (a baixa não gera movimentação).
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class ItensCompraPorTipoTest {

    @Autowired WebApplicationContext contexto;
    @Autowired CompraService compraService;
    @Autowired PatrimonioIntegracaoService integracao;
    @Autowired CompraRepository compraRepository;
    @Autowired EnvioPatrimonioRepository envioRepository;
    @Autowired MovimentacaoRepository movimentacaoRepository;
    @Autowired MaterialRepository materialRepository;
    @Autowired SubcategoriaRepository subcategoriaRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired SetorRepository setorRepository;

    private MockMvc mvc;
    private Setor ti;
    private Material toner;
    private Subcategoria suprimentos;

    /** O contexto/H2 é compartilhado entre classes: não deixar envio_patrimonio apontando para compras
        que outra classe vai apagar com compraRepository.deleteAll(). */
    @AfterEach
    void limparEnvios() {
        envioRepository.deleteAll();
    }

    @BeforeEach
    void cenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        envioRepository.deleteAll();
        movimentacaoRepository.deleteAll();
        compraRepository.deleteAll();
        materialRepository.deleteAll();
        subcategoriaRepository.deleteAll();
        areaRepository.deleteAll();
        setorRepository.deleteAll();

        Area area = areaRepository.save(Area.builder().nome("Informática ItensTest").sigla("INFIT").build());
        suprimentos = subcategoriaRepository.save(Subcategoria.builder()
                .nome("Suprimentos ItensTest").sigla("SUPIT").area(area).proximoSequencial(1).build());
        toner = materialRepository.save(Material.builder().nome("Toner HP 85A").codigoSku("INF-SUP-00001")
                .unidadeMedida("UN").estoqueAtual(2).estoqueMinimo(1).subcategoria(suprimentos).build());
        ti = setorRepository.save(Setor.builder().nome("T.I. ItensTest").build());
    }

    @Test
    @WithMockUser(username = "delva.maria", roles = "COMPRAS")
    void compraDeEstoque_exigeMaterialDoCatalogo() throws Exception {
        mvc.perform(post("/compras").with(csrf())
                        .param("tipo", "ESTOQUE")
                        .param("valorEstimado", "0")
                        .param("itemDescricao", "Toner qualquer")          // descrição livre não vale para ESTOQUE
                        .param("itemSubcategoriaId", suprimentos.getId().toString())
                        .param("itemQuantidade", "1")
                        .param("itemValorUnitario", "100.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("compras/form"))
                .andExpect(model().attribute("erro", containsString("material do catálogo")));
        assertThat(compraRepository.count()).isZero();

        // com material do catálogo passa, e o item herda grupo/subgrupo do material
        mvc.perform(post("/compras").with(csrf())
                        .param("tipo", "ESTOQUE")
                        .param("valorEstimado", "0")
                        .param("itemMaterialId", toner.getId().toString())
                        .param("itemQuantidade", "3")
                        .param("itemValorUnitario", "100.00"))
                .andExpect(status().is3xxRedirection());
        Compra c = compraRepository.findByIdComItens(compraRepository.findAll().get(0).getId()).orElseThrow();
        ItemCompra item = c.getItens().get(0);
        assertThat(item.getMaterial().getId()).isEqualTo(toner.getId());
        assertThat(item.getSubcategoria()).isNull();
        assertThat(item.getGrupo()).isEqualTo("Informática ItensTest");
        assertThat(item.getSubgrupo()).isEqualTo("Suprimentos ItensTest");
    }

    @Test
    @WithMockUser(username = "sarah.luz", roles = "COMPRAS")
    void compraDireta_itemLivreComGrupoESubgrupo_naoMovimentaEstoqueNaBaixa() throws Exception {
        // sem grupo/subgrupo é barrada
        mvc.perform(post("/compras").with(csrf())
                        .param("tipo", "DIRETA")
                        .param("setorSolicitanteId", ti.getId().toString())
                        .param("valorEstimado", "0")
                        .param("itemDescricao", "Cabo HDMI 2m")
                        .param("itemQuantidade", "2")
                        .param("itemValorUnitario", "35.00"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erro", containsString("grupo e o subgrupo")));
        assertThat(compraRepository.count()).isZero();

        mvc.perform(post("/compras").with(csrf())
                        .param("tipo", "DIRETA")
                        .param("setorSolicitanteId", ti.getId().toString())
                        .param("fornecedor", "InfoSupri")
                        .param("valorEstimado", "0")
                        .param("itemDescricao", "Cabo HDMI 2m")
                        .param("itemSubcategoriaId", suprimentos.getId().toString())
                        .param("itemQuantidade", "2")
                        .param("itemValorUnitario", "35.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compras/aguardando"));

        Compra compra = compraRepository.findAll().get(0);
        Compra comItens = compraRepository.findByIdComItens(compra.getId()).orElseThrow();
        ItemCompra item = comItens.getItens().get(0);
        assertThat(item.getMaterial()).isNull();
        assertThat(item.getDescricao()).isEqualTo("Cabo HDMI 2m");
        assertThat(item.getSubcategoria().getId()).isEqualTo(suprimentos.getId());
        assertThat(comItens.getValorEstimado()).isEqualByComparingTo("70.00");

        // Diretoria autoriza e a baixa acontece sem gerar movimentação de estoque
        compraService.registrarAutorizacao(compra.getId(), AutorizacaoDiretoria.AUTORIZADA, "Ok", "delva.maria");
        long movsAntes = movimentacaoRepository.count();
        int estoqueAntes = materialRepository.findById(toner.getId()).orElseThrow().getEstoqueAtual();
        compraService.darBaixa(compra.getId(), "NF-1", new BigDecimal("70.00"), null,
                new DadosRecebimento("Samuel", null, false, "sarah.luz"));
        assertThat(movimentacaoRepository.count()).isEqualTo(movsAntes);
        assertThat(materialRepository.findById(toner.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(estoqueAntes);
        assertThat(compraRepository.findById(compra.getId()).orElseThrow().getStatus()).isEqualTo(StatusCompra.COMPRA_REALIZADA);

        // detalhes exibem grupo › subgrupo do item
        mvc.perform(get("/compras/" + compra.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cabo HDMI 2m")))
                .andExpect(content().string(containsString("Informática ItensTest › Suprimentos ItensTest")));

        // API mobile expõe descrição, grupo e subgrupo
        mvc.perform(get("/api/compras/" + compra.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].materialId").isEmpty())
                .andExpect(jsonPath("$.itens[0].materialNome").value("Cabo HDMI 2m"))
                .andExpect(jsonPath("$.itens[0].descricao").value("Cabo HDMI 2m"))
                .andExpect(jsonPath("$.itens[0].grupo").value("Informática ItensTest"))
                .andExpect(jsonPath("$.itens[0].subgrupo").value("Suprimentos ItensTest"));
    }

    @Test
    void payloadDoPatrimonio_levaGrupoESubgrupoDoItem() {
        Compra c = Compra.builder().tipo(TipoCompra.PATRIMONIAL).setorSolicitante(ti).fornecedor("Magazine").build();
        ItemCompra livre = ItemCompra.builder().descricao("Cadeira giratória")
                .subcategoria(Subcategoria.builder().id(suprimentos.getId()).build())
                .quantidade(1).valorUnitario(new BigDecimal("690.00")).build();
        Compra salva = compraService.criarPreCompra(c, List.of(livre), null);
        compraService.registrarAutorizacao(salva.getId(), AutorizacaoDiretoria.AUTORIZADA, "Ok", "delva.maria");
        compraService.darBaixa(salva.getId(), "NF-9", new BigDecimal("690.00"), null,
                new DadosRecebimento("Carlos", ti.getId(), true, "daisy.dias"));

        var payload = integracao.montarPayloadDaCompra(salva.getId());
        assertThat(payload.itens()).hasSize(1);
        assertThat(payload.itens().get(0).descricao()).isEqualTo("Cadeira giratória");
        assertThat(payload.itens().get(0).codigoSku()).isNull();
        assertThat(payload.itens().get(0).grupo()).isEqualTo("Informática ItensTest");
        assertThat(payload.itens().get(0).subgrupo()).isEqualTo("Suprimentos ItensTest");
    }
}
