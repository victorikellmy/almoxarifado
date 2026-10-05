package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.Organizacao;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.repository.CompraRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Setores por organização (FPTO x FA-Saúde): cadastro sem pessoas, homônimos
 * permitidos entre organizações e bloqueados na mesma, listagem ordenada e API.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class SetoresOrganizacaoTest {

    @Autowired WebApplicationContext contexto;
    @Autowired SetorRepository setorRepository;
    @Autowired MovimentacaoRepository movimentacaoRepository;
    @Autowired CompraRepository compraRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        movimentacaoRepository.deleteAll();
        compraRepository.deleteAll();
        setorRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cadastraSetorSemResponsavel_eHomonimoSoEmOutraOrganizacao() throws Exception {
        mvc.perform(post("/setores").with(csrf())
                        .param("organizacao", "FPTO").param("nome", "Financeiro").param("responsavel", "")
                        .param("codigoCentroCusto", "GRP_Financeiro"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setores"));

        // mesmo nome no FA-Saúde é permitido
        mvc.perform(post("/setores").with(csrf())
                        .param("organizacao", "FA_SAUDE").param("nome", "Financeiro")
                        .param("codigoCentroCusto", "grp_financeiro"))
                .andExpect(status().is3xxRedirection());

        // repetir na mesma organização é bloqueado
        mvc.perform(post("/setores").with(csrf())
                        .param("organizacao", "FPTO").param("nome", "financeiro"))
                .andExpect(status().isOk())
                .andExpect(view().name("setores/lista"))
                .andExpect(content().string(containsString("Já existe o setor")));

        assertThat(setorRepository.count()).isEqualTo(2);
        Setor fpto = setorRepository.findByOrganizacaoOrderByNomeAsc(Organizacao.FPTO).get(0);
        assertThat(fpto.getResponsavel()).isNull();
        assertThat(fpto.getNomeCompleto()).isEqualTo("Financeiro — FPTO");
        Setor fa = setorRepository.findByOrganizacaoOrderByNomeAsc(Organizacao.FA_SAUDE).get(0);
        assertThat(fa.getNomeCompleto()).isEqualTo("Financeiro — FA-Saúde");

        // listagem ordenada: FPTO antes de FA-Saúde
        assertThat(setorRepository.listarOrdenados()).extracting(Setor::getOrganizacao)
                .containsExactly(Organizacao.FPTO, Organizacao.FA_SAUDE);

        mvc.perform(get("/setores"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FA-Saúde")));
    }

    @Test
    @WithMockUser(roles = "PADRAO")
    void api_setores_informaOrganizacaoENomeCompleto() throws Exception {
        setorRepository.save(Setor.builder().nome("Atendimento").organizacao(Organizacao.FA_SAUDE).build());
        setorRepository.save(Setor.builder().nome("Compras e Almoxarifado").build()); // default FPTO

        mvc.perform(get("/api/setores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizacao").value("FPTO"))
                .andExpect(jsonPath("$[0].nomeCompleto").value("Compras e Almoxarifado — FPTO"))
                .andExpect(jsonPath("$[1].organizacao").value("FA_SAUDE"))
                .andExpect(jsonPath("$[1].nome").value("Atendimento"))
                .andExpect(jsonPath("$[1].codigoCentroCusto").value(org.hamcrest.Matchers.nullValue()));
    }
}
