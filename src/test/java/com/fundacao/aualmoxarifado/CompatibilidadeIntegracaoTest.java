package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.domain.StatusCompra;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.repository.CompraRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de COMPATIBILIDADE: sobe o contexto completo (perfil dev — H2 +
 * DataSeeder) e verifica que:
 *
 * <ol>
 *   <li>as queries customizadas (JPQL com CAST/GROUP BY, EntityGraph, projeções,
 *       derived queries novas) executam no dialeto H2 sem erro;</li>
 *   <li>todas as telas Thymeleaf renderizam (um erro de expressão/fragment vira
 *       500 aqui — foi assim que dois bugs pré-existentes se manifestaram);</li>
 *   <li>os exports e a API REST respondem com os content-types corretos;</li>
 *   <li>o cache de estáticos configurado está ativo.</li>
 * </ol>
 */
@SpringBootTest
class CompatibilidadeIntegracaoTest {

    // MockMvc montado manualmente: no Boot 4 o @AutoConfigureMockMvc vive num
    // artefato separado (spring-boot-webmvc-test-autoconfigure) que não está
    // no classpath; a construção explícita evita a dependência extra.
    @Autowired WebApplicationContext contexto;
    private MockMvc mvc;

    @BeforeEach
    void montarMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Autowired MovimentacaoRepository movimentacaoRepository;
    @Autowired MaterialRepository materialRepository;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private static final int ANO = Year.now().getValue();

    // =====================================================================
    // 1. Queries customizadas × dialeto H2
    // =====================================================================

    @Test
    @Transactional(readOnly = true)
    void queriesCustomizadas_executamNoH2ComDadosDoSeeder() {
        LocalDateTime ini = LocalDateTime.of(ANO, 1, 1, 0, 0);
        LocalDateTime fim = LocalDateTime.of(ANO, 12, 31, 23, 59);

        // Agregação com CAST(EXTRACT(...)) no GROUP BY — o bug de dialeto H2
        // corrigido nesta sessão; se regredir, esta linha lança exceção.
        assertThat(movimentacaoRepository.agregadoPorMesTipo(ini, fim)).isNotEmpty();

        assertThat(movimentacaoRepository.resumoPorTipo(ini, fim)).isNotEmpty();
        assertThat(movimentacaoRepository.gastoPorSetor(ini, fim)).isNotEmpty();
        assertThat(movimentacaoRepository.consumoPorSetor(ini, fim)).isNotEmpty();
        assertThat(movimentacaoRepository.topMateriais(ini, fim, PageRequest.of(0, 10)))
                .hasSizeLessThanOrEqualTo(10);
        assertThat(movimentacaoRepository.findEfetivasNoIntervalo(ini, fim)).isNotEmpty();
        assertThat(movimentacaoRepository.countByStatus(StatusMovimentacao.ENTREGUE))
                .isGreaterThan(0);

        // Fetch join usado pela aprovação/detalhes — itens e materiais vêm juntos.
        Movimentacao qualquer = movimentacaoRepository.findAll().get(0);
        Movimentacao comItens = movimentacaoRepository.findByIdComItens(qualquer.getId()).orElseThrow();
        assertThat(comItens.getItens()).isNotEmpty();
        assertThat(comItens.getItens().get(0).getMaterial().getNome()).isNotBlank();

        // Projeção do relatório de estoque: 1 linha por material.
        assertThat(materialRepository.linhasRelatorioEstoque())
                .hasSize((int) materialRepository.count());
        assertThat(materialRepository.countEmAlertaDeEstoque()).isGreaterThanOrEqualTo(0);

        // Lookup em lote da bipagem.
        String sku = materialRepository.findAll().get(0).getCodigoSku();
        assertThat(materialRepository.findByCodigoSkuIn(List.of(sku))).hasSize(1);

        // Paginação nova de compras + derived queries novas.
        assertThat(compraRepository.findByStatus(StatusCompra.AGUARDANDO_COMPRA,
                PageRequest.of(0, 5))).isNotNull();
        assertThat(compraRepository.countByStatus(StatusCompra.AGUARDANDO_COMPRA))
                .isGreaterThanOrEqualTo(0);
        assertThat(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMINISTRADOR)).isTrue();
    }

    // =====================================================================
    // 2. Telas Thymeleaf renderizam sem erro de template
    // =====================================================================

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void telasPrincipais_renderizamSemErroDeTemplate() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")));
        mvc.perform(get("/materiais")).andExpect(status().isOk());
        mvc.perform(get("/movimentacoes")).andExpect(status().isOk());
        mvc.perform(get("/compras")).andExpect(status().isOk());
        mvc.perform(get("/compras/aguardando")).andExpect(status().isOk());
        mvc.perform(get("/subcategorias")).andExpect(status().isOk());
        mvc.perform(get("/areas")).andExpect(status().isOk());
        mvc.perform(get("/setores")).andExpect(status().isOk());
        mvc.perform(get("/usuarios")).andExpect(status().isOk());
        mvc.perform(get("/auditoria")).andExpect(status().isOk());
        mvc.perform(get("/movimentacoes/saida/nova")).andExpect(status().isOk());
        mvc.perform(get("/compras/nova")).andExpect(status().isOk());
        mvc.perform(get("/materiais/novo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void detalheDeMovimentacao_renderiza_incluindoSubtotalCalculado() throws Exception {
        Long id = movimentacaoRepository.findAll().get(0).getId();

        mvc.perform(get("/movimentacoes/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Itens enviados")));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void relatorios_mensalTrimestralAnual_renderizamComFragmentsETotais() throws Exception {
        mvc.perform(get("/relatorios/mensal").param("ano", String.valueOf(ANO)).param("mes", "7"))
                .andExpect(status().isOk());
        mvc.perform(get("/relatorios/trimestral").param("ano", String.valueOf(ANO)).param("trimestre", "3"))
                .andExpect(status().isOk());
        mvc.perform(get("/relatorios/anual").param("ano", String.valueOf(ANO)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TOTAL " + ANO)));
    }

    // =====================================================================
    // 3. Exports e API REST
    // =====================================================================

    @Test
    @WithMockUser
    void exports_devolvemContentTypeCorretoNosTresFormatos() throws Exception {
        mvc.perform(get("/api/relatorios/anual/export")
                        .param("ano", String.valueOf(ANO)).param("formato", "CSV"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));

        mvc.perform(get("/api/relatorios/anual/export")
                        .param("ano", String.valueOf(ANO)).param("formato", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("spreadsheetml")));

        mvc.perform(get("/api/relatorios/anual/export")
                        .param("ano", String.valueOf(ANO)).param("formato", "PDF"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")));

        mvc.perform(get("/api/relatorios/estoque/export").param("formato", "CSV"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void apiRest_listagensPaginadasRespondem() throws Exception {
        mvc.perform(get("/api/movimentacoes").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/api/saidas").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/api/materiais").param("size", "5"))
                .andExpect(status().isOk());
    }

    // =====================================================================
    // 4. Estáticos com cache configurado
    // =====================================================================

    @Test
    void estaticos_servidosComCacheControlDeLongaDuracao() throws Exception {
        mvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }
}
