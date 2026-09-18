package com.fundacao.aualmoxarifado.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que os dois advices compartilham o mesmo mapeamento exceção→status
 * ({@link TipoErro}) e que detalhes internos do banco não vazam ao cliente.
 */
class ExceptionHandlersTest {

    private static final String DETALHE_DO_BANCO =
            "Unique index or primary key violation: PUBLIC.CONSTRAINT_INDEX_4 ON PUBLIC.MATERIAL(CODIGO_SKU)";

    private final HttpServletRequest request = new MockHttpServletRequest("GET", "/materiais");

    private DataIntegrityViolationException violacaoDeIntegridade() {
        return new DataIntegrityViolationException("wrap",
                new RuntimeException(DETALHE_DO_BANCO));
    }

    @Nested
    class Web {

        private final WebExceptionHandler handler = new WebExceptionHandler();

        @Test
        void notFound_rendezizaViewErroCom404() {
            Model model = new ConcurrentModel();
            String view = handler.handleNotFound(
                    new RecursoNaoEncontradoException("Material", 9L), model, request);

            assertThat(view).isEqualTo("erro");
            assertThat(model.getAttribute("status")).isEqualTo(404);
            assertThat(model.getAttribute("titulo")).isEqualTo(TipoErro.NAO_ENCONTRADO.titulo());
        }

        @Test
        void regraDeNegocio_rendezizaViewErroCom422() {
            Model model = new ConcurrentModel();
            String view = handler.handleBusiness(
                    new RegraDeNegocioException("Estoque insuficiente"), model, request);

            assertThat(view).isEqualTo("erro");
            assertThat(model.getAttribute("status")).isEqualTo(422);
            assertThat(model.getAttribute("mensagem")).isEqualTo("Estoque insuficiente");
        }

        @Test
        void violacaoDeIntegridade_naoVazaDetalheDoBancoParaOUsuario() {
            Model model = new ConcurrentModel();
            handler.handleDataIntegrity(violacaoDeIntegridade(), model, request);

            String mensagem = String.valueOf(model.getAttribute("mensagem"));
            assertThat(mensagem).doesNotContain("CONSTRAINT_INDEX_4")
                                .doesNotContain("PUBLIC.MATERIAL");
            assertThat(mensagem).isEqualTo(TipoErro.mensagemConflitoDados());
        }

        @Test
        void fallbackGenerico_naoCaiNaWhitelabel_eGeraTracking() {
            Model model = new ConcurrentModel();
            String view = handler.handleGeneric(new IllegalStateException("boom"), model, request);

            assertThat(view).isEqualTo("erro");
            assertThat(model.getAttribute("status")).isEqualTo(500);
            assertThat(String.valueOf(model.getAttribute("mensagem")))
                    .contains("rastreamento")
                    .doesNotContain("boom"); // detalhe interno só no log
        }
    }

    @Nested
    class Api {

        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        @Test
        void statusHttp_seguemOMapaUnicoDeErros() {
            assertThat(handler.handleNotFound(
                    new RecursoNaoEncontradoException("Setor", 1L), request)
                    .getStatusCode().value()).isEqualTo(TipoErro.NAO_ENCONTRADO.status());

            assertThat(handler.handleBusiness(
                    new RegraDeNegocioException("x"), request)
                    .getStatusCode().value()).isEqualTo(TipoErro.REGRA_NEGOCIO.status());

            assertThat(handler.handleDataIntegrity(violacaoDeIntegridade(), request)
                    .getStatusCode().value()).isEqualTo(TipoErro.CONFLITO_DADOS.status());
        }

        @Test
        void violacaoDeIntegridade_naoVazaDetalheDoBancoNoJson() {
            ResponseEntity<ErroResponse> resp =
                    handler.handleDataIntegrity(violacaoDeIntegridade(), request);

            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().mensagem())
                    .doesNotContain("CONSTRAINT_INDEX_4")
                    .isEqualTo(TipoErro.mensagemConflitoDados());
        }

        @Test
        void rotaInexistente_eMetodoErrado_naoViram500ComStackTrace() throws Exception {
            ResponseEntity<ErroResponse> semRota = handler.handleNoResource(
                    new NoResourceFoundException(HttpMethod.GET, "/api/nao-existe", null), request);
            assertThat(semRota.getStatusCode().value()).isEqualTo(404);

            ResponseEntity<ErroResponse> metodoErrado = handler.handleMethodNotSupported(
                    new HttpRequestMethodNotSupportedException("PATCH"), request);
            assertThat(metodoErrado.getStatusCode().value()).isEqualTo(405);
        }

        @Test
        void fallbackGenerico_devolveTrackingSemDetalheInterno() {
            ResponseEntity<ErroResponse> resp =
                    handler.handleGeneric(new RuntimeException("segredo interno"), request);

            assertThat(resp.getStatusCode().value()).isEqualTo(500);
            assertThat(resp.getBody()).isNotNull();
            assertThat(resp.getBody().mensagem())
                    .contains("rastreamento")
                    .doesNotContain("segredo interno");
        }
    }
}
