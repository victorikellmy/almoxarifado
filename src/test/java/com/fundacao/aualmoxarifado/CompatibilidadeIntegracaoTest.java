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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
    @Autowired com.fundacao.aualmoxarifado.repository.SetorRepository setorRepository;

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
        // Exports agora usam StreamingResponseBody (memória O(1)) → resposta
        // assíncrona: é preciso fazer o asyncDispatch para materializar o result.
        exportAsync("/api/relatorios/anual/export", "CSV", "text/csv");
        exportAsync("/api/relatorios/anual/export", "XLSX", "spreadsheetml");
        exportAsync("/api/relatorios/anual/export", "PDF", "application/pdf");
        exportAsync("/api/relatorios/estoque/export", "CSV", "text/csv");
    }

    /** Dispara o export, faz o asyncDispatch e valida status 200 + content-type. */
    private void exportAsync(String url, String formato, String contentTypeEsperado) throws Exception {
        var mvcResult = mvc.perform(get(url)
                        .param("ano", String.valueOf(ANO)).param("formato", formato))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(contentTypeEsperado)));
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
    // 3b. Contrato da API mobile — espelha os critérios de aceite do app
    // =====================================================================

    @Test
    void apiSemCredencial_recebe401ComCorpoJson_semRedirect() throws Exception {
        mvc.perform(get("/api/setores"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/setores"));
    }

    @Test
    @WithMockUser
    void apiSetores_autenticado_devolveArrayComIdENome() throws Exception {
        mvc.perform(get("/api/setores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].nome").isNotEmpty());
    }

    @Test
    @WithMockUser
    void apiSaidas_baixaEstoqueNaHora_eReplayComMesmaChaveNaoBaixaDeNovo() throws Exception {
        var material = materialRepository.findAll().stream()
                .filter(m -> m.getEstoqueAtual() >= 2)
                .findFirst().orElseThrow();
        var setor = setorRepository.findAll().get(0);
        int estoqueAntes = material.getEstoqueAtual();

        String body = """
                {"setorDestinoId": %d, "retiradoPor": "teste-integracao",
                 "itens": [{"codigoSku": "%s", "quantidade": 1}]}
                """.formatted(setor.getId(), material.getCodigoSku());
        String chave = "it-idem-" + System.nanoTime();

        mvc.perform(post("/api/saidas")
                        .header("Idempotency-Key", chave)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setorDestinoId").value(setor.getId()))
                .andExpect(jsonPath("$.nomeSetor").value(setor.getNome()))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.movimentacaoIds").isArray())
                .andExpect(jsonPath("$.dataRegistro").isNotEmpty());

        assertThat(materialRepository.findById(material.getId()).orElseThrow().getEstoqueAtual())
                .as("POST /api/saidas debita o estoque imediatamente")
                .isEqualTo(estoqueAntes - 1);

        // Replay com a mesma Idempotency-Key: mesma resposta, sem novo débito.
        mvc.perform(post("/api/saidas")
                        .header("Idempotency-Key", chave)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        assertThat(materialRepository.findById(material.getId()).orElseThrow().getEstoqueAtual())
                .as("reenvio com a mesma chave não pode debitar de novo")
                .isEqualTo(estoqueAntes - 1);
    }

    @Test
    @WithMockUser
    void apiSaidas_estoqueInsuficiente_devolve422ComMensagemLegivel() throws Exception {
        var material = materialRepository.findAll().get(0);
        var setor = setorRepository.findAll().get(0);

        String body = """
                {"setorDestinoId": %d, "retiradoPor": null,
                 "itens": [{"codigoSku": "%s", "quantidade": %d}]}
                """.formatted(setor.getId(), material.getCodigoSku(),
                              material.getEstoqueAtual() + 1_000);

        mvc.perform(post("/api/saidas")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.mensagem", containsString("Estoque insuficiente")));
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    void apiAuditoria_operador_recebe403() throws Exception {
        // Trilha de auditoria é admin-only também na API (paridade com a web).
        mvc.perform(get("/api/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void apiAuditoria_admin_recebe200() throws Exception {
        mvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    void webContinuaComFormLogin_redirecionandoNaoAutenticadoPara302() throws Exception {
        // A cadeia web (fora de /api) segue com redirect para o formulário —
        // prova de que o entry point JSON ficou restrito ao /api/**.
        mvc.perform(get("/materiais"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        mvc.perform(get("/login")).andExpect(status().isOk());
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
