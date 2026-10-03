package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.CompraService;
import com.fundacao.aualmoxarifado.service.integracao.PatrimonioIntegracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo do setor de Compras (perfil COMPRAS): pré-compra → decisão da Diretoria →
 * recebimento com NF, quem retirou e setor → envio ao Patrimônio (outbox).
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class FluxoAutorizacaoPatrimonioTest {

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
    @Autowired UsuarioRepository usuarioRepository;

    private MockMvc mvc;
    private Setor nucleoSaude;
    private Material luva;

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

        Area area = areaRepository.save(Area.builder().nome("Saúde FluxoTest").sigla("SAUFT").build());
        Subcategoria sub = subcategoriaRepository.save(Subcategoria.builder()
                .nome("Descartáveis FluxoTest").sigla("DESFT").area(area).proximoSequencial(1).build());
        luva = materialRepository.save(Material.builder().nome("Luva M").codigoSku("SAU-DES-00001")
                .unidadeMedida("CX").estoqueAtual(10).estoqueMinimo(1).subcategoria(sub).build());
        nucleoSaude = setorRepository.save(Setor.builder().nome("5º BPM - Núcleo de Saúde").responsavel("Maj").build());
    }

    @Test
    void seederCriouUsuariosDoSetorDeComprasComPerfilCompras() {
        for (String login : List.of("delva.maria", "sarah.luz", "daisy.dias")) {
            Usuario u = usuarioRepository.findByUsernameIgnoreCase(login).orElseThrow();
            assertThat(u.getPerfil()).isEqualTo(PerfilUsuario.COMPRAS);
            assertThat(u.getAtivo()).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "delva.maria", roles = "COMPRAS")
    void compraPatrimonial_precisaDaDiretoria_eVaiParaFilaDoPatrimonioNaBaixa() throws Exception {
        // 1) Pré-compra PATRIMONIAL com item descrito livremente (sem material do catálogo)
        mvc.perform(post("/compras").with(csrf())
                        .param("tipo", "PATRIMONIAL")
                        .param("setorSolicitanteId", nucleoSaude.getId().toString())
                        .param("fornecedor", "Planeta Cartuchos")
                        .param("valorEstimado", "0")
                        .param("numeroDocumento", "001/2026")
                        .param("itemMaterialId", "")
                        .param("itemDescricao", "Impressora multifuncional Samsung M2070")
                        .param("itemQuantidade", "1")
                        .param("itemValorUnitario", "1200.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compras/aguardando"));

        Compra compra = compraRepository.findAll().get(0);
        assertThat(compra.getTipo()).isEqualTo(TipoCompra.PATRIMONIAL);
        assertThat(compra.getAutorizacaoDiretoria()).isEqualTo(AutorizacaoDiretoria.PENDENTE);
        assertThat(compra.getValorEstimado()).isEqualByComparingTo("1200.00");
        Compra comItens = compraRepository.findByIdComItens(compra.getId()).orElseThrow();
        assertThat(comItens.getItens()).hasSize(1);
        assertThat(comItens.getItens().get(0).getMaterial()).isNull();
        assertThat(comItens.getItens().get(0).getNomeItem()).isEqualTo("Impressora multifuncional Samsung M2070");

        // 2) Sem a Diretoria, a baixa é barrada
        mvc.perform(multipart("/compras/" + compra.getId() + "/baixa").with(csrf())
                        .param("numeroNF", "123").param("valorRealFinal", "1200.00")
                        .param("retiradoPor", "Sgt. João").param("setorEntregaId", nucleoSaude.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erro", containsString("autorizada pela Diretoria")));
        assertThat(compraRepository.findById(compra.getId()).orElseThrow().getStatus()).isEqualTo(StatusCompra.AGUARDANDO_COMPRA);

        // 3) Delva registra que a Diretoria autorizou
        mvc.perform(post("/compras/" + compra.getId() + "/autorizacao").with(csrf())
                        .param("decisao", "AUTORIZADA").param("parecer", "Ok, Diretoria autorizou em reunião."))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("sucesso"));
        Compra autorizada = compraRepository.findById(compra.getId()).orElseThrow();
        assertThat(autorizada.getAutorizacaoDiretoria()).isEqualTo(AutorizacaoDiretoria.AUTORIZADA);
        assertThat(autorizada.getAutorizadoPor()).isEqualTo("delva.maria");
        assertThat(autorizada.getAutorizadoEm()).isNotNull();

        // 4) Recebimento com NF, quem retirou e setor → patrimônio
        MockMultipartFile nf = new MockMultipartFile("pdfNotaFiscal", "NF-123.pdf", "application/pdf", "%PDF-1.4 teste".getBytes());
        mvc.perform(multipart("/compras/" + compra.getId() + "/baixa").file(nf).with(csrf())
                        .param("numeroNF", "123").param("valorRealFinal", "1180.00")
                        .param("retiradoPor", "Sgt. João Silva")
                        .param("setorEntregaId", nucleoSaude.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compras/" + compra.getId()));

        Compra recebida = compraRepository.findById(compra.getId()).orElseThrow();
        assertThat(recebida.getStatus()).isEqualTo(StatusCompra.COMPRA_REALIZADA);
        assertThat(recebida.getEnviarPatrimonio()).isTrue();
        assertThat(recebida.getRetiradoPor()).isEqualTo("Sgt. João Silva");
        assertThat(recebida.getRecebidoPor()).isEqualTo("delva.maria");

        // Patrimonial não mexe em estoque nem gera movimentação
        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(10);
        assertThat(movimentacaoRepository.count()).isZero();

        // Outbox criado; sem PATRIMONIO_URL fica PENDENTE com o motivo
        EnvioPatrimonio envio = envioRepository.findByCompraId(compra.getId()).orElseThrow();
        assertThat(envio.getStatus()).isEqualTo(StatusEnvioPatrimonio.PENDENTE);
        assertThat(envio.getUltimoErro()).contains("PATRIMONIO_URL");

        // Payload que será enviado ao Patrimônio
        var payload = integracao.montarPayloadDaCompra(compra.getId());
        assertThat(payload.origem()).isEqualTo("ALMOXARIFADO");
        assertThat(payload.compraId()).isEqualTo(compra.getId());
        assertThat(payload.numeroNotaFiscal()).isEqualTo("123");
        assertThat(payload.valorTotal()).isEqualByComparingTo("1180.00");
        assertThat(payload.retiradoPor()).isEqualTo("Sgt. João Silva");
        assertThat(payload.setorDestino()).isEqualTo("5º BPM - Núcleo de Saúde");
        assertThat(payload.registradoPor()).isEqualTo("delva.maria");
        assertThat(payload.itens()).hasSize(1);
        assertThat(payload.itens().get(0).descricao()).isEqualTo("Impressora multifuncional Samsung M2070");
        assertThat(payload.anexos()).anySatisfy(a -> {
            assertThat(a.tipo()).isEqualTo("NOTA_FISCAL");
            assertThat(a.conteudoBase64()).isNotBlank();
        });

        // Detalhes mostram a decisão e o status do envio
        mvc.perform(get("/compras/" + compra.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AUTORIZADA")))
                .andExpect(content().string(containsString("Patrimônio")))
                .andExpect(content().string(containsString("Reenviar agora")));
    }

    @Test
    @WithMockUser(username = "sarah.luz", roles = "COMPRAS")
    void naoAutorizada_cancelaPreCompra_eExigeParecer() throws Exception {
        Compra c = preCompraEstoque();

        mvc.perform(post("/compras/" + c.getId() + "/autorizacao").with(csrf())
                        .param("decisao", "NAO_AUTORIZADA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", containsString("parecer")));
        assertThat(compraRepository.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(StatusCompra.AGUARDANDO_COMPRA);

        mvc.perform(post("/compras/" + c.getId() + "/autorizacao").with(csrf())
                        .param("decisao", "NAO_AUTORIZADA").param("parecer", "Sem orçamento este mês"))
                .andExpect(status().is3xxRedirection());
        Compra atual = compraRepository.findById(c.getId()).orElseThrow();
        assertThat(atual.getStatus()).isEqualTo(StatusCompra.CANCELADA);
        assertThat(atual.getAutorizacaoDiretoria()).isEqualTo(AutorizacaoDiretoria.NAO_AUTORIZADA);
        assertThat(atual.getParecerDiretoria()).isEqualTo("Sem orçamento este mês");
        assertThat(atual.getAutorizadoPor()).isEqualTo("sarah.luz");
    }

    @Test
    @WithMockUser(username = "maria.padrao", roles = "PADRAO")
    void usuarioPadrao_naoRegistraDecisaoNemCadastraPreCompra() throws Exception {
        Compra c = preCompraEstoque();
        mvc.perform(post("/compras/" + c.getId() + "/autorizacao").with(csrf())
                        .param("decisao", "AUTORIZADA"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/compras/nova")).andExpect(status().isForbidden());
        // consulta continua liberada
        mvc.perform(get("/compras")).andExpect(status().isOk());
        mvc.perform(get("/compras/" + c.getId())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "daisy.dias", roles = "COMPRAS")
    void compraDeEstoqueAutorizada_baixaNormal_semPatrimonio() throws Exception {
        Compra c = preCompraEstoque();
        compraService.registrarAutorizacao(c.getId(), AutorizacaoDiretoria.AUTORIZADA, null, "daisy.dias");

        mvc.perform(multipart("/compras/" + c.getId() + "/baixa").with(csrf())
                        .param("numeroNF", "77").param("valorRealFinal", "50.00"))
                .andExpect(status().is3xxRedirection());

        assertThat(compraRepository.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(StatusCompra.COMPRA_REALIZADA);
        assertThat(materialRepository.findById(luva.getId()).orElseThrow().getEstoqueAtual()).isEqualTo(15);
        assertThat(envioRepository.findByCompraId(c.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "delva.maria", roles = "COMPRAS")
    void api_registraDecisaoDaDiretoria_eDevolveNoDetalhe() throws Exception {
        Compra c = preCompraEstoque();
        mvc.perform(post("/api/compras/" + c.getId() + "/autorizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"AUTORIZADA\",\"parecer\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizacaoDiretoria").value("AUTORIZADA"))
                .andExpect(jsonPath("$.autorizadoPor").value("delva.maria"));

        mvc.perform(get("/api/compras").param("status", "AGUARDANDO_COMPRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].autorizacaoDiretoria").value("AUTORIZADA"));
    }

    private Compra preCompraEstoque() {
        Compra compra = Compra.builder()
                .dataSolicitacao(LocalDateTime.now())
                .tipo(TipoCompra.ESTOQUE)
                .valorEstimado(BigDecimal.ZERO)
                .build();
        ItemCompra item = ItemCompra.builder().material(luva).quantidade(5).valorUnitario(BigDecimal.TEN).build();
        return compraService.criarPreCompra(compra, List.of(item), null);
    }
}
