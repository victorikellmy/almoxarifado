package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão: com {@code spring.jpa.open-in-view=false}, as telas que exibem
 * {@code material.subcategoria.area} (etiqueta, edição de material) e
 * {@code subcategoria.area} (edição de subcategoria) estouravam
 * LazyInitializationException e o navegador ficava com a tela vazia.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class TelasComCategoriaTest {

    @Autowired WebApplicationContext contexto;
    @Autowired AreaRepository areaRepository;
    @Autowired SubcategoriaRepository subcategoriaRepository;
    @Autowired MaterialService materialService;

    private MockMvc mvc;
    private Subcategoria sub;
    private Material material;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        String sufixo = String.valueOf(System.nanoTime() % 100000);
        Area area = areaRepository.save(Area.builder()
                .nome("Área Teste " + sufixo).sigla("T" + sufixo.substring(0, Math.min(4, sufixo.length())))
                .build());
        sub = subcategoriaRepository.save(Subcategoria.builder()
                .area(area).nome("Sub Teste").sigla("SUB").proximoSequencial(1).build());
        material = materialService.salvar(Material.builder()
                .nome("Material de teste").unidadeMedida("Unidade")
                .estoqueAtual(0).estoqueMinimo(1).valorUnitario(BigDecimal.ONE)
                .subcategoria(sub).build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void etiquetaDoMaterial_renderiza() throws Exception {
        mvc.perform(get("/materiais/{id}/etiqueta", material.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(material.getCodigoSku())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void edicaoDoMaterial_renderiza() throws Exception {
        mvc.perform(get("/materiais/{id}/editar", material.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void edicaoDaSubcategoria_renderiza() throws Exception {
        mvc.perform(get("/subcategorias/{id}/editar", sub.getId()))
                .andExpect(status().isOk());
    }
}
