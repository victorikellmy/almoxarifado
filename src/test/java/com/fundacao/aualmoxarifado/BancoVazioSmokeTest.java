package com.fundacao.aualmoxarifado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test do cenário de PRODUÇÃO: banco recém-criado, VAZIO (sem o
 * DataSeeder do perfil dev — só o admin do UsuarioSeeder).
 *
 * <p>O {@code CompatibilidadeIntegracaoTest} roda com massa de dados; este
 * garante que as telas não explodem com listas vazias e agregações nulas,
 * que é exatamente o estado do primeiro acesso após o deploy.</p>
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class BancoVazioSmokeTest {

    @Autowired WebApplicationContext contexto;
    private MockMvc mvc;

    @BeforeEach
    void montarMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void telasPrincipais_renderizamComBancoVazio() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/materiais")).andExpect(status().isOk());
        mvc.perform(get("/materiais/importar")).andExpect(status().isOk());
        mvc.perform(get("/movimentacoes")).andExpect(status().isOk());
        mvc.perform(get("/movimentacoes/saida/nova")).andExpect(status().isOk());
        mvc.perform(get("/compras")).andExpect(status().isOk());
        mvc.perform(get("/compras/aguardando")).andExpect(status().isOk());
        mvc.perform(get("/subcategorias")).andExpect(status().isOk());
        mvc.perform(get("/areas")).andExpect(status().isOk());
        mvc.perform(get("/setores")).andExpect(status().isOk());
        mvc.perform(get("/usuarios")).andExpect(status().isOk());
        mvc.perform(get("/auditoria")).andExpect(status().isOk());
        mvc.perform(get("/relatorios/mensal")).andExpect(status().isOk());
        mvc.perform(get("/relatorios/trimestral")).andExpect(status().isOk());
        mvc.perform(get("/relatorios/anual")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    /**
     * Regressão do bug do /error?continue: /error precisa ser público. Se ele
     * exigir login, um 404 qualquer antes da autenticação (ex.: favicon
     * inexistente) vira a requisição salva do Spring Security e o usuário cai
     * em /error?continue (status 999) depois de logar.
     */
    @Test
    void errorDispatch_ehPublico_naoRedirecionaParaLogin() throws Exception {
        mvc.perform(get("/error"))
                .andExpect(status().is(org.hamcrest.Matchers.not(302)));
    }
}
