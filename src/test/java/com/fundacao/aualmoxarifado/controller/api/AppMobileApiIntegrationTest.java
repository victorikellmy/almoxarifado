package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.CompraService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Endpoints adicionados para o app Android (contrato em docs do app):
 * {@code GET /api/me}, {@code GET /api/movimentacoes/{id}},
 * {@code POST /api/movimentacoes/{id}/status}, {@code GET /api/compras[/{id}]}
 * e a regressão do {@code POST /api/saidas} (baixa imediata).
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class AppMobileApiIntegrationTest {

    @Autowired WebApplicationContext contexto;
    @Autowired MovimentacaoService movimentacaoService;
    @Autowired CompraService compraService;
    @Autowired MovimentacaoRepository movimentacaoRepository;
    @Autowired CompraRepository compraRepository;
    @Autowired MaterialRepository materialRepository;
    @Autowired SubcategoriaRepository subcategoriaRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired SetorRepository setorRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private MockMvc mvc;
    private Setor setor;
    private Material luva;

    @BeforeEach
    void cenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        movimentacaoRepository.deleteAll();
        compraRepository.deleteAll();
        materialRepository.deleteAll();
        subcategoriaRepository.deleteAll();
        areaRepository.deleteAll();
        setorRepository.deleteAll();

        if (usuarioRepository.findByUsernameIgnoreCase("joao.silva").isEmpty()) {
            usuarioRepository.save(Usuario.builder().username("joao.silva").nome("João Silva")
                    .senhaHash("$2a$10$hashqualquer").perfil(PerfilUsuario.ADMIN).build());
        }
        if (usuarioRepository.findByUsernameIgnoreCase("maria.padrao").isEmpty()) {
            usuarioRepository.save(Usuario.builder().username("maria.padrao").nome("Maria Padrão")
                    .senhaHash("$2a$10$hashqualquer").perfil(PerfilUsuario.PADRAO).build());
        }

        Area area = areaRepository.save(Area.builder().nome("Odonto ApiTest").sigla("ODOAT").build());
        Subcategoria sub = subcategoriaRepository.save(Subcategoria.builder()
                .nome("Descartáveis ApiTest").sigla("DESAT").area(area).proximoSequencial(1).build());
        luva = materialRepository.save(Material.builder()
                .nome("Luva de procedimento M").codigoSku("ODO-DES-00003").unidadeMedida("CX")
                .estoqueAtual(50).estoqueMinimo(5).valorUnitario(new BigDecimal("32.90"))
                .subcategoria(sub).build());
        setor = setorRepository.save(Setor.builder().nome("Odontologia").responsavel("Dra. Ana").build());
    }

    // ------------------------------------------------------------------ /api/me

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void me_admin_devolvePerfil() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("joao.silva"))
                .andExpect(jsonPath("$.nome").value("João Silva"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "maria.padrao", roles = "PADRAO")
    void me_padrao_devolvePerfilPadrao() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("PADRAO"));
    }

    @Test
    void me_semCredencial_401Json() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ------------------------------------------------------------------ detalhe da movimentação

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void detalheMovimentacao_trazItens_e404QuandoNaoExiste() throws Exception {
        Movimentacao saida = saidaPendente(2);

        mvc.perform(get("/api/movimentacoes/" + saida.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saida.getId()))
                .andExpect(jsonPath("$.status").value("PENDENTE_APROVACAO"))
                .andExpect(jsonPath("$.setorNome").value("Odontologia"))
                .andExpect(jsonPath("$.criadoEm").isString())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].materialId").value(luva.getId()))
                .andExpect(jsonPath("$.itens[0].materialNome").value("Luva de procedimento M"))
                .andExpect(jsonPath("$.itens[0].codigoSku").value("ODO-DES-00003"))
                .andExpect(jsonPath("$.itens[0].unidadeMedida").value("CX"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2));

        mvc.perform(get("/api/movimentacoes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("999999")));
    }

    // ------------------------------------------------------------------ status

    @Test
    @WithMockUser(username = "maria.padrao", roles = "PADRAO")
    void status_usuarioPadrao_403Json() throws Exception {
        Movimentacao saida = saidaPendente(1);
        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APROVADO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        assertThat(movimentacaoRepository.findById(saida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusMovimentacao.PENDENTE_APROVACAO);
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void status_pendenteParaAprovado_debitaEstoque() throws Exception {
        Movimentacao saida = saidaPendente(5);

        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APROVADO\",\"motivo\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.itens", hasSize(1)));

        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(45);
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void status_entregueParaPendente_422ComMensagem() throws Exception {
        Movimentacao saida = saidaPendente(1);
        movimentacaoService.alterarStatus(saida.getId(), StatusMovimentacao.ENTREGUE, null);

        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDENTE_APROVACAO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("já foi entregue")));

        assertThat(movimentacaoRepository.findById(saida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusMovimentacao.ENTREGUE);
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void status_rejeitarSemMotivo_422() throws Exception {
        Movimentacao saida = saidaPendente(1);

        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJEITADO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", containsString("motivo")));

        assertThat(movimentacaoRepository.findById(saida.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusMovimentacao.PENDENTE_APROVACAO);
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void status_aprovadoParaRejeitado_devolveEstoqueEGravaMotivo() throws Exception {
        Movimentacao saida = saidaPendente(5);
        movimentacaoService.alterarStatus(saida.getId(), StatusMovimentacao.APROVADO, null);
        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(45);

        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJEITADO\",\"motivo\":\"material vencido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADO"))
                .andExpect(jsonPath("$.observacao", startsWith("Rejeitada: material vencido")));

        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(50);
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void status_statusAusente_400() throws Exception {
        Movimentacao saida = saidaPendente(1);
        mvc.perform(post("/api/movimentacoes/" + saida.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "joao.silva", roles = "ADMIN")
    void telaWeb_detalheDaSaida_mostraRejeitarComMotivo_eExibeRegraViolada() throws Exception {
        Movimentacao saida = saidaPendente(1);

        mvc.perform(get("/movimentacoes/" + saida.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Motivo da rejeição")));

        // Tela web: rejeitar sem motivo não estoura erro, volta com a mensagem da regra.
        mvc.perform(post("/movimentacoes/" + saida.getId() + "/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("status", "REJEITADO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", containsString("motivo")));
    }

    // ------------------------------------------------------------------ compras

    @Test
    @WithMockUser(username = "maria.padrao", roles = "PADRAO")
    void compras_listagemFiltradaEDetalhe_semLazyException() throws Exception {
        Compra compra = Compra.builder()
                .dataSolicitacao(LocalDateTime.now())
                .tipo(TipoCompra.DIRETA)
                .fornecedor("Dental Cremer")
                .observacao("Urgente")
                .valorEstimado(BigDecimal.ZERO)
                .build();
        compra.setSetorSolicitante(setor);
        ItemCompra item = ItemCompra.builder().material(luva).quantidade(10)
                .valorUnitario(new BigDecimal("32.90")).build();
        Compra salva = compraService.criarPreCompra(compra, List.of(item), null);

        mvc.perform(get("/api/compras").param("status", "AGUARDANDO_COMPRA").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(salva.getId()))
                .andExpect(jsonPath("$.content[0].tipo").value("DIRETA"))
                .andExpect(jsonPath("$.content[0].status").value("AGUARDANDO_COMPRA"))
                .andExpect(jsonPath("$.content[0].setorSolicitante").value("Odontologia"))
                .andExpect(jsonPath("$.content[0].fornecedor").value("Dental Cremer"))
                .andExpect(jsonPath("$.content[0].valorEstimado").value(329.00))
                .andExpect(jsonPath("$.content[0].valorRealFinal").value(nullValue()))
                .andExpect(jsonPath("$.content[0].totalItens").value(1));

        mvc.perform(get("/api/compras").param("status", "COMPRA_REALIZADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/compras/" + salva.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(1)))
                .andExpect(jsonPath("$.itens[0].materialId").value(luva.getId()))
                .andExpect(jsonPath("$.itens[0].codigoSku").value("ODO-DES-00003"))
                .andExpect(jsonPath("$.itens[0].quantidade").value(10))
                .andExpect(jsonPath("$.itens[0].valorUnitario").value(32.90))
                .andExpect(jsonPath("$.itens[0].subtotal").value(329.00))
                .andExpect(jsonPath("$.anexos", hasSize(0)));

        mvc.perform(get("/api/compras/999999"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ regressão: POST /api/saidas

    @Test
    @WithMockUser(username = "maria.padrao", roles = "PADRAO")
    void saidaPeloApp_continuaNascendoEntregueComBaixaImediata() throws Exception {
        mvc.perform(post("/api/saidas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setorDestinoId\":" + setor.getId()
                                + ",\"retiradoPor\":\"Ana\",\"itens\":[{\"codigoSku\":\"ODO-DES-00003\",\"quantidade\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimentacaoIds", hasSize(1)));

        Movimentacao mov = movimentacaoRepository.findAll().get(0);
        assertThat(mov.getStatus()).isEqualTo(StatusMovimentacao.ENTREGUE);
        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(47);
    }

    // ------------------------------------------------------------------ helpers

    /** Saída pelo fluxo web: nasce PENDENTE_APROVACAO sem debitar estoque (RN04). */
    private Movimentacao saidaPendente(int quantidade) {
        return movimentacaoService.registrarSaida(setor, "Ana", null,
                List.of(new LinhaItem(luva.getId(), quantidade)));
    }
}
