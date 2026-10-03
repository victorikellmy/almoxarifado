package com.fundacao.aualmoxarifado;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.LeituraParteCompraService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo "Nova pré-compra" com leitura automática da Parte: upload do PDF em
 * /compras/ler-anexo devolve o formulário preenchido (tipo, setor, documento,
 * itens casados com o catálogo); o submit em /compras salva a compra com o PDF
 * anexado como SOLICITACAO sem precisar reenviá-lo.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
class LeituraParteCompraFluxoTest {

    @Autowired WebApplicationContext contexto;
    @Autowired AreaRepository areaRepository;
    @Autowired SubcategoriaRepository subcategoriaRepository;
    @Autowired MaterialRepository materialRepository;
    @Autowired SetorRepository setorRepository;
    @Autowired CompraRepository compraRepository;
    @Autowired MovimentacaoRepository movimentacaoRepository;

    private MockMvc mvc;
    private Material papel;
    private Setor bpm5;

    @BeforeEach
    void cenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // O contexto (e o H2) é compartilhado entre classes de teste: limpa na ordem das FKs
        // (movimentações e compras referenciam material e setor).
        movimentacaoRepository.deleteAll();
        compraRepository.deleteAll();
        materialRepository.deleteAll();
        subcategoriaRepository.deleteAll();
        areaRepository.deleteAll();
        setorRepository.deleteAll();

        Area area = areaRepository.save(Area.builder().nome("Escritório LeituraTest").sigla("ESCLT").build());
        Subcategoria sub = subcategoriaRepository.save(Subcategoria.builder()
                .nome("Papelaria LeituraTest").sigla("PAPLT").area(area).proximoSequencial(1).build());
        papel = materialRepository.save(Material.builder()
                .nome("Papel A4 75g").unidadeMedida("cx").estoqueAtual(0).estoqueMinimo(5).subcategoria(sub).build());
        materialRepository.save(Material.builder()
                .nome("Grampeador de mesa").unidadeMedida("un").estoqueAtual(0).estoqueMinimo(1).subcategoria(sub).build());

        setorRepository.save(Setor.builder().nome("Diretoria").responsavel("X").build());
        bpm5 = setorRepository.save(Setor.builder().nome("5º BPM - Núcleo de Saúde").responsavel("Maj Emerson").build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void oficioDeUnidade_preencheCompraDiretaComSetorEItens_eAnexaPdfAoSalvar() throws Exception {
        byte[] pdf = gerarPdf(
                "OFICIO n. 012/2026 - NUCLEO DE SAUDE",
                "SGD: 2026/09039/000999",
                "Porto Nacional/TO, 05 de janeiro de 2026.",
                "Ao Senhor Diretor Presidente da Fundacao Pro Tocantins",
                "Assunto: Solicitacao de material",
                "Senhor Presidente,",
                "Solicito autorizacao para compra dos itens abaixo para o Nucleo de Saude do 5o BPM.",
                "10 cx Papel A4 75g 25,90",
                "2 un Grampeador 32,00",
                "Respeitosamente,",
                "Emerson Rodrigues Moura - MAJ QOPM",
                "Resp. pelo Comando do 5o BPM");

        MockHttpSession sessao = new MockHttpSession();

        // Passo 1: leitura do anexo devolve o formulário preenchido
        MvcResult leitura = mvc.perform(multipart("/compras/ler-anexo")
                        .file(new MockMultipartFile("pdfParte", "Oficio 012-2026.pdf", "application/pdf", pdf))
                        .session(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("compras/form"))
                .andExpect(model().attributeExists("leituraId", "itensSugeridos"))
                .andExpect(content().string(containsString("012/2026")))
                .andExpect(content().string(containsString("2026/09039/000999")))
                .andExpect(content().string(containsString("Anexo lido")))
                .andReturn();

        Compra preenchida = (Compra) leitura.getModelAndView().getModel().get("compra");
        assertThat(preenchida.getTipo()).isEqualTo(TipoCompra.DIRETA);
        assertThat(preenchida.getNumeroDocumento()).isEqualTo("012/2026");
        assertThat(preenchida.getAssunto()).isEqualTo("Solicitacao de material");
        assertThat(preenchida.getSolicitanteDocumento()).contains("Emerson Rodrigues Moura");

        Long setorSugerido = (Long) leitura.getModelAndView().getModel().get("setorSugeridoId");
        assertThat(setorSugerido).isEqualTo(bpm5.getId());

        @SuppressWarnings("unchecked")
        List<LeituraParteCompraService.ItemSugerido> itens =
                (List<LeituraParteCompraService.ItemSugerido>) leitura.getModelAndView().getModel().get("itensSugeridos");
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).materialId()).isEqualTo(papel.getId());
        assertThat(itens.get(0).quantidade()).isEqualTo(10);
        assertThat(itens.get(0).valorUnitario()).isEqualByComparingTo("25.90");
        assertThat(itens.get(1).materialNome()).isEqualTo("Grampeador de mesa");

        String leituraId = (String) leitura.getModelAndView().getModel().get("leituraId");

        // Passo 2: colaborador revisa e salva — o PDF lido vira o anexo da compra
        mvc.perform(post("/compras").session(sessao).with(csrf())
                        .param("tipo", "DIRETA")
                        .param("setorSolicitanteId", bpm5.getId().toString())
                        .param("fornecedor", "Papelaria Central")
                        .param("valorEstimado", "0")
                        .param("numeroDocumento", preenchida.getNumeroDocumento())
                        .param("numeroSgd", preenchida.getNumeroSgd())
                        .param("dataDocumento", "2026-01-05")
                        .param("assunto", preenchida.getAssunto())
                        .param("solicitanteDocumento", preenchida.getSolicitanteDocumento())
                        .param("itemMaterialId", papel.getId().toString())
                        .param("itemQuantidade", "10")
                        .param("itemValorUnitario", "25.90")
                        .param("leituraId", leituraId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/compras/aguardando"));

        List<Compra> compras = compraRepository.findAll();
        assertThat(compras).hasSize(1);
        Compra salva = compraRepository.findByIdComItens(compras.get(0).getId()).orElseThrow();
        assertThat(salva.getStatus()).isEqualTo(StatusCompra.AGUARDANDO_COMPRA);
        assertThat(salva.getNumeroDocumento()).isEqualTo("012/2026");
        assertThat(salva.getNumeroSgd()).isEqualTo("2026/09039/000999");
        assertThat(salva.getValorEstimado()).isEqualByComparingTo(new BigDecimal("259.00"));
        assertThat(salva.getItens()).hasSize(1);

        Compra comAnexos = compraRepository.findByIdComAnexos(salva.getId()).orElseThrow();
        assertThat(comAnexos.getAnexos()).hasSize(1);
        assertThat(comAnexos.getAnexos().get(0).getTipo()).isEqualTo(TipoAnexoCompra.SOLICITACAO);
        assertThat(comAnexos.getAnexos().get(0).getNomeOriginal()).isEqualTo("Oficio 012-2026.pdf");

        // sessão limpa após salvar
        assertThat(sessao.getAttribute("compras.leitura." + leituraId)).isNull();

        // detalhes mostram o documento de origem
        mvc.perform(get("/compras/" + salva.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("012/2026")))
                .andExpect(content().string(containsString("Oficio 012-2026.pdf")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void parteDoAlmoxarifado_viraCompraDeEstoque() throws Exception {
        byte[] pdf = gerarPdf(
                "PARTE n. 003/2026 - Compras/Almoxarifado",
                "Palmas - TO, 20 de fevereiro de 2026.",
                "Da MAJ QOAPM RR Delva Maria - Chefe do Compras/Almox.",
                "Ao Sr. Cel QOPM R/R Diretor Presidente da Fundacao Pro Tocantins.",
                "Assunto: Solicitacao de material de escritorio",
                "Senhor Diretor Presidente,",
                "Solicito a aquisicao dos materiais de escritorio conforme orcamentos anexos.",
                "Respeitosamente,");

        MvcResult r = mvc.perform(multipart("/compras/ler-anexo")
                        .file(new MockMultipartFile("pdfParte", "parte.pdf", "application/pdf", pdf))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        Compra c = (Compra) r.getModelAndView().getModel().get("compra");
        assertThat(c.getTipo()).isEqualTo(TipoCompra.ESTOQUE);
        assertThat(c.getNumeroDocumento()).isEqualTo("003/2026");
        @SuppressWarnings("unchecked")
        List<String> avisos = (List<String>) r.getModelAndView().getModel().get("avisos");
        assertThat(avisos).anyMatch(a -> a.contains("anexo separado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void arquivoQueNaoEhPdf_voltaAoFormularioComErro() throws Exception {
        mvc.perform(multipart("/compras/ler-anexo")
                        .file(new MockMultipartFile("pdfParte", "parte.txt", "text/plain", "ola".getBytes()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("compras/form"))
                .andExpect(model().attributeExists("erro"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void formularioManualContinuaFuncionando() throws Exception {
        mvc.perform(get("/compras/nova"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ler anexo da Parte")));
    }

    /** Gera um PDF simples com uma linha de texto por item (fonte padrão, sem acentos). */
    private static byte[] gerarPdf(String... linhas) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                float y = 760;
                for (String l : linhas) {
                    cs.beginText();
                    cs.newLineAtOffset(60, y);
                    cs.showText(l);
                    cs.endText();
                    y -= 18;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
