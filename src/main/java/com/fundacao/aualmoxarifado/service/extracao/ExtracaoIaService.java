package com.fundacao.aualmoxarifado.service.extracao;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Item;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Natureza;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Orcamento;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.TipoDocumento;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Leitura assistida por IA (Claude) do anexo de compra.
 *
 * Complementa o {@link ParteCompraParser}: lê o PDF inteiro (inclusive páginas escaneadas,
 * como notas fiscais digitalizadas) e devolve os campos em JSON. Desabilitada por padrão;
 * ative com {@code compras.ia.habilitada=true} e a variável de ambiente {@code ANTHROPIC_API_KEY}.
 */
@Service
@Slf4j
public class ExtracaoIaService {

    private static final String SYSTEM_PROMPT = """
            Você é um assistente do setor de Compras/Almoxarifado da Fundação Pró-Tocantins (FPTO),
            entidade de apoio à Polícia Militar (PMTO) e ao Corpo de Bombeiros (CBMTO) do Tocantins.
            Você recebe o PDF de uma "Parte" (pedido interno de setor) ou "Ofício" (pedido de unidade
            como BPM/CIPM/Coordenação regional, às vezes com nota fiscal anexa) e deve extrair os dados
            para pré-preencher uma Solicitação de Compra.

            Responda SOMENTE com um objeto JSON válido, sem comentários nem texto fora do JSON, com estas chaves
            (use null quando a informação não existir no documento):
            {
              "tipoDocumento": "PARTE" | "OFICIO" | "OUTRO",
              "numeroDocumento": "001/2026",
              "numeroSgd": "2026/09039/000362",
              "dataDocumento": "2026-01-15",
              "localDocumento": "Palmas - TO",
              "origemDocumento": "setor/unidade que assina (ex.: Compras/Almoxarifado, Núcleo de Saúde do 5º BPM)",
              "remetente": "nome e posto/função de quem assina",
              "destinatario": "a quem é dirigido",
              "assunto": "texto do campo Assunto",
              "descricao": "resumo fiel do pedido/justificativa, em até 600 caracteres",
              "natureza": "SOLICITACAO_SETOR" | "COMPRA_DIRETA" | "PAGAMENTO_NOTA_FISCAL",
              "unidadeSolicitante": "5º BPM" | "2ª CIPM" | "Almoxarifado" | ...,
              "fornecedor": "razão social ou nome fantasia do fornecedor/prestador escolhido",
              "cnpjFornecedor": "00.000.000/0000-00",
              "numeroNotaFiscal": "número da NF/NFS-e, se houver",
              "valorEstimado": 320.00,
              "dadosBancarios": "banco, agência, conta, titular, PIX (texto corrido)",
              "orcamentos": [ { "empresa": "...", "valor": 120.00, "telefone": "..." } ],
              "itens": [ { "descricao": "...", "quantidade": 1, "unidade": "un", "valorUnitario": 320.00 } ],
              "avisos": [ "observações relevantes para quem vai revisar (ex.: relação de itens em anexo separado)" ]
            }

            Regras:
            - natureza: PAGAMENTO_NOTA_FISCAL quando o documento encaminha nota fiscal já emitida para pagamento;
              COMPRA_DIRETA quando uma unidade pede autorização de compra (normalmente com orçamentos);
              SOLICITACAO_SETOR para Partes internas de setor da Fundação.
            - Valores numéricos em formato JSON (ponto decimal), nunca em texto.
            - Quando houver vários orçamentos, o fornecedor sugerido é o de menor valor, salvo indicação contrária no documento.
            - Não invente dados. Se a relação de materiais estiver "em anexo" e não constar no PDF, deixe "itens" vazio e avise.
            """;

    private final boolean habilitada;
    private final String modelo;
    // Jackson 2 (trazido pelo SDK da Anthropic); independente do Jackson 3 usado pelo Spring Boot 4.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile AnthropicClient client;

    public ExtracaoIaService(@Value("${compras.ia.habilitada:false}") boolean habilitada,
                             @Value("${compras.ia.modelo:claude-opus-5-5}") String modelo) {
        this.habilitada = habilitada;
        this.modelo = modelo;
    }

    public boolean isHabilitada() {
        return habilitada;
    }

    /** Devolve a extração feita pela IA, ou vazio se desabilitada/indisponível (nunca lança). */
    public Optional<ExtracaoDocumentoCompra> extrair(byte[] pdf, String nomeArquivo) {
        if (!habilitada) return Optional.empty();
        try {
            String b64 = Base64.getEncoder().encodeToString(pdf);
            DocumentBlockParam doc = DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(b64).build())
                    .title(nomeArquivo == null ? "documento.pdf" : nomeArquivo)
                    .build();

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(16000L)
                    .system(SYSTEM_PROMPT)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofDocument(doc),
                            ContentBlockParam.ofText(TextBlockParam.builder()
                                    .text("Extraia os dados deste documento de compra e responda apenas com o JSON.")
                                    .build())))
                    .build();

            Message resposta = cliente().messages().create(params);

            if (resposta.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.warn("[Compras/IA] Leitura recusada para o arquivo {}", nomeArquivo);
                return Optional.empty();
            }

            StringBuilder sb = new StringBuilder();
            resposta.content().stream()
                    .flatMap(b -> b.text().stream())
                    .forEach(t -> sb.append(t.text()));

            return Optional.of(converter(extrairJson(sb.toString())));
        } catch (Exception e) {
            log.warn("[Compras/IA] Falha na leitura do arquivo {}: {}", nomeArquivo, e.toString());
            return Optional.empty();
        }
    }

    private AnthropicClient cliente() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) client = AnthropicOkHttpClient.fromEnv();
                c = client;
            }
        }
        return c;
    }

    /** Remove cercas ```json ... ``` e qualquer texto antes/depois do objeto. */
    static String extrairJson(String s) {
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b < a) throw new IllegalStateException("Resposta da IA sem JSON");
        return s.substring(a, b + 1);
    }

    ExtracaoDocumentoCompra converter(String json) throws Exception {
        JsonNode n = objectMapper.readTree(json);
        ExtracaoDocumentoCompra r = new ExtracaoDocumentoCompra();
        r.setLidoPorIa(true);

        r.setTipoDocumento(enumOuNulo(TipoDocumento.class, texto(n, "tipoDocumento")));
        r.setNumeroDocumento(texto(n, "numeroDocumento"));
        r.setNumeroSgd(texto(n, "numeroSgd"));
        r.setDataDocumento(data(texto(n, "dataDocumento")));
        r.setLocalDocumento(texto(n, "localDocumento"));
        r.setOrigemDocumento(texto(n, "origemDocumento"));
        r.setRemetente(texto(n, "remetente"));
        r.setDestinatario(texto(n, "destinatario"));
        r.setAssunto(texto(n, "assunto"));
        r.setDescricao(texto(n, "descricao"));
        r.setNatureza(enumOuNulo(Natureza.class, texto(n, "natureza")));
        r.setUnidadeSolicitante(texto(n, "unidadeSolicitante"));
        r.setFornecedor(texto(n, "fornecedor"));
        r.setCnpjFornecedor(texto(n, "cnpjFornecedor"));
        r.setNumeroNotaFiscal(texto(n, "numeroNotaFiscal"));
        r.setValorEstimado(decimal(n.get("valorEstimado")));
        r.setDadosBancarios(texto(n, "dadosBancarios"));

        JsonNode orc = n.get("orcamentos");
        if (orc != null && orc.isArray()) {
            for (JsonNode o : orc) {
                Orcamento x = new Orcamento();
                x.setEmpresa(texto(o, "empresa"));
                x.setValor(decimal(o.get("valor")));
                x.setTelefone(texto(o, "telefone"));
                r.getOrcamentos().add(x);
            }
        }
        JsonNode itens = n.get("itens");
        if (itens != null && itens.isArray()) {
            for (JsonNode i : itens) {
                Item x = new Item();
                x.setDescricao(texto(i, "descricao"));
                x.setQuantidade(decimal(i.get("quantidade")));
                x.setUnidade(texto(i, "unidade"));
                x.setValorUnitario(decimal(i.get("valorUnitario")));
                r.getItens().add(x);
            }
        }
        JsonNode avisos = n.get("avisos");
        if (avisos != null && avisos.isArray()) {
            for (JsonNode a : avisos) if (a.isTextual()) r.aviso("IA: " + a.asText());
        }
        return r;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        if (v == null || v.isNull()) return null;
        String s = v.isTextual() ? v.asText() : v.toString();
        return s.isBlank() ? null : s.trim();
    }

    private static BigDecimal decimal(JsonNode v) {
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return v.decimalValue();
        String s = v.asText().trim();
        if (s.isEmpty()) return null;
        if (s.matches(".*,\\d{1,2}$")) s = s.replace(".", "").replace(',', '.');
        s = s.replaceAll("[^\\d\\.\\-]", "");
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate data(String s) {
        if (s == null) return null;
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private static <E extends Enum<E>> E enumOuNulo(Class<E> tipo, String valor) {
        if (valor == null) return null;
        try { return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }
}
