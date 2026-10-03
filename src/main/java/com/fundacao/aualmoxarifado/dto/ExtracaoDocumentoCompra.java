package com.fundacao.aualmoxarifado.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da leitura automática do anexo de uma compra (Parte de setor ou Ofício
 * de unidade da PMTO/CBMTO). Todos os campos são opcionais: o que não for localizado
 * fica nulo e gera um aviso para o colaborador conferir no formulário.
 */
@Getter @Setter
public class ExtracaoDocumentoCompra {

    public enum TipoDocumento { PARTE, OFICIO, OUTRO }

    /** Natureza do pedido lida do documento. */
    public enum Natureza {
        /** Parte interna de setor da Fundação pedindo aquisição (normalmente reposição de estoque). */
        SOLICITACAO_SETOR,
        /** Ofício de unidade (BPM/CIPM/Coordenação) pedindo autorização de compra, geralmente com orçamentos. */
        COMPRA_DIRETA,
        /** Ofício encaminhando nota fiscal já emitida em nome da FPTO para pagamento. */
        PAGAMENTO_NOTA_FISCAL
    }

    @Getter @Setter
    public static class Orcamento {
        private String empresa;
        private BigDecimal valor;
        private String telefone;
    }

    @Getter @Setter
    public static class Item {
        private String descricao;
        private BigDecimal quantidade;
        private String unidade;
        private BigDecimal valorUnitario;
    }

    private TipoDocumento tipoDocumento;
    private String numeroDocumento;
    private String numeroSgd;
    private LocalDate dataDocumento;
    private String localDocumento;
    private String origemDocumento;
    private String remetente;
    private String destinatario;
    private String assunto;
    private String descricao;
    private Natureza natureza;
    private String unidadeSolicitante;
    private String fornecedor;
    private String cnpjFornecedor;
    private String numeroNotaFiscal;
    private BigDecimal valorEstimado;
    private String dadosBancarios;
    private List<Orcamento> orcamentos = new ArrayList<>();
    private List<Item> itens = new ArrayList<>();

    private boolean lidoPorIa;
    private String textoExtraido;
    private int paginas;
    private List<String> avisos = new ArrayList<>();

    public void aviso(String msg) {
        if (msg != null && !avisos.contains(msg)) avisos.add(msg);
    }

    /** Mescla outra extração sobre esta: valores não vazios da outra prevalecem (usado pela leitura por IA). */
    public void mesclar(ExtracaoDocumentoCompra outra) {
        if (outra == null) return;
        if (outra.tipoDocumento != null) tipoDocumento = outra.tipoDocumento;
        if (naoVazio(outra.numeroDocumento)) numeroDocumento = outra.numeroDocumento;
        if (naoVazio(outra.numeroSgd)) numeroSgd = outra.numeroSgd;
        if (outra.dataDocumento != null) dataDocumento = outra.dataDocumento;
        if (naoVazio(outra.localDocumento)) localDocumento = outra.localDocumento;
        if (naoVazio(outra.origemDocumento)) origemDocumento = outra.origemDocumento;
        if (naoVazio(outra.remetente)) remetente = outra.remetente;
        if (naoVazio(outra.destinatario)) destinatario = outra.destinatario;
        if (naoVazio(outra.assunto)) assunto = outra.assunto;
        if (naoVazio(outra.descricao)) descricao = outra.descricao;
        if (outra.natureza != null) natureza = outra.natureza;
        if (naoVazio(outra.unidadeSolicitante)) unidadeSolicitante = outra.unidadeSolicitante;
        if (naoVazio(outra.fornecedor)) fornecedor = outra.fornecedor;
        if (naoVazio(outra.cnpjFornecedor)) cnpjFornecedor = outra.cnpjFornecedor;
        if (naoVazio(outra.numeroNotaFiscal)) numeroNotaFiscal = outra.numeroNotaFiscal;
        if (outra.valorEstimado != null) valorEstimado = outra.valorEstimado;
        if (naoVazio(outra.dadosBancarios)) dadosBancarios = outra.dadosBancarios;
        if (!outra.orcamentos.isEmpty()) orcamentos = new ArrayList<>(outra.orcamentos);
        if (!outra.itens.isEmpty()) itens = new ArrayList<>(outra.itens);
        outra.avisos.forEach(this::aviso);
        lidoPorIa = lidoPorIa || outra.lidoPorIa;
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }
}
