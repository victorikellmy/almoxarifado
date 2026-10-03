package com.fundacao.aualmoxarifado.service.extracao;

import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.Natureza;
import com.fundacao.aualmoxarifado.dto.ExtracaoDocumentoCompra.TipoDocumento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do leitor heurístico com textos no formato dos documentos reais da FPTO
 * (Parte de setor, Ofício de compra direta com orçamentos e Ofício com NFS-e anexa).
 */
class ParteCompraParserTest {

    private final ParteCompraParser parser = new ParteCompraParser();

    private static final String PARTE_ALMOX = """
            E-mail: fundacao@fundacaoprotocantins.org
            Site: www.fundacaoprotocantins.org
            ______________________________________________________
            PARTE nº 001/2026 - Compras/Almoxarifado
                                       Palmas - TO, 15 de Janeiro de 2026.
            Da MAJ QOAPM RR Delva Maria A. Rodrigues - Chefe do Compras/Almox.
            Ao Sr. Cel QOPM R/R Diretor Presidente da Fundação Pró Tocantins.
            Assunto: Solicitação

                     Senhor Diretor Presidente,
                 Para o atendimento da demanda do Almoxarifado da Fundação Pró-Tocantins,
            solicito à Vossa Senhoria a compra de materiais odontológicos para suprir a necessidade
            dos gabinetes e repor o estoque do setor de Almoxarifado. Segue relação anexa:

            Respeitosamente,
            ______________________________________________________
            """;

    private static final String OFICIO_GAS = """
            OFÍCIO nº 136/2025 - FPTO Araguaína - Coord. Região Norte.
            SGD Nº 2025/09039/114662
                                     Araguaína - TO, 23 de dezembro de 2025
            DA: CEL PM RR Coordenadora da Fundação Pró-Tocantins da Região Norte.
            AO: Senhor CEL PM RR Diretor Presidente da Fundação Pró-Tocantins.

            Assunto: Gás de cozinha (solicita).

                     Nesta oportunidade, reporto-me a Vossa Senhoria, no intuito de solicitar
            autorização para compra de Gás de Cozinha para atender aos beneficiários do
            Serviço de Saúde do 2º BPM e da Fundação Araguaína. Friso que a última troca fora
            realizada em junho/2025.

            Abaixo orçamento:

            Empresa                   Valor   Telefone
            Tupy Gás                  120,00  0800 646 1818
            SIM Gás                   130,00  (63) 99221-4579
            Toc Gás                   135,00  (63) 99934-7679

            Dados Bancários:
            Banco do Brasil
            Ag 0638-6
            C/C 103698-X
            Carvalho Comércio de gás Ltda

                      Respeitosamente,
                               Lyvya Gomes do Prado - CEL PM RR
                               Coordenadora da FPTO Região Norte
            ASSINADO POR LOGIN E SENHA POR: LYVYA GOMES DO PRADO EM 23/12/2025 11:01:16
            """;

    private static final String OFICIO_NF = """
            POLÍCIA MILITAR DO ESTADO DO TOCANTINS
            Ofício nº 001/2026 - NÚCLEO DE SAÚDE
            SGD: 2026/09039/000362
                                   Porto Nacional/TO, 05 de janeiro de 2026.
            Ao Senhor
            Marciano Montelo Maranhão Monteiro - CEL QOPM
            Diretor de Saúde
            Palmas - TO.

            Assunto: Solicitação para pagamento
            Anexo: Nota fiscal de produtos/serviços

                 Senhor Presidente,
                 Ao cumprimentar cordialmente Vossa Senhoria, encaminho a nota fiscal-e
            de nº 00000002, referente ao reparo da impressora da marca Samsung e
            patrimônio de nº 001.605, proveniente do Núcleo de Saúde do 5º BPM,
            confeccionada em nome da Fundação Pró Tocantins para fins de pagamento,
            conforme nota fiscal em anexo. Informo que o serviço foi autorizado
            verbalmente pela Major Delva, da Fundação Pró Tocantins.
                 Os dados bancários para pagamento constam na nota fiscal.
                De qualquer forma, este Comando permanece à disposição para novas
            demandas apresentadas por Vossa Excelência.

                 Respeitosamente,
                                   Assinatura digital
                       Emerson Rodrigues Moura - MAJ QOPM
                             Resp. pelo Comando do 5º BPM
            Av. Nações Unidas S/Nº, Qd. 19 Lt. 18, Setor São Vicente, CEP: 77500-000 - Porto Nacional - TO
            ASSINADO POR LOGIN E SENHA POR: Emerson Rodrigues Moura EM 05/01/2026 11:08:51
            \f
            NOTA FISCAL DE SERVIÇOS ELETRÔNICA - NFS-e
            PRESTADOR DE SERVIÇOS
            Razão Social
            DAVID WELLYNGTON VAZ-ME
            Nome Fantasia                                   Email
            PLANETA CARTUCHOS & CARIMBOS                    dominiocontabilidade2010@gmail.com
            CPF/CNPJ                 Inscrição Municipal
            17.380.000/0001-67       3880714
            TOMADOR DE SERVIÇOS
            Nome/Razão Social
            FUNDACAO PRO - TOCANTINS
            CPF/CNPJ
            17.670.141/0001-14
            DESCRIÇÃO DOS SERVIÇOS
            REPARO DE PLACA FONTE ..................R$320,00
            BANCO DO BRASIL
            AG.: 1117-7
            C/C:35593-3
            DAVID WELLYNGTON VAZ - ME
            PIX:
            17.380.000/0001-67
            SICOOB
            RETENÇÕES FEDERAIS
            Valor Líquido (R$)            Valor Total da Nota (R$)
            320,00                             320,00
            """;

    @Test
    void parteDeSetorComRelacaoAnexa() {
        ExtracaoDocumentoCompra r = parser.parse(PARTE_ALMOX);

        assertEquals(TipoDocumento.PARTE, r.getTipoDocumento());
        assertEquals("001/2026", r.getNumeroDocumento());
        assertEquals("Compras/Almoxarifado", r.getOrigemDocumento());
        assertEquals(LocalDate.of(2026, 1, 15), r.getDataDocumento());
        assertEquals("Palmas - TO", r.getLocalDocumento());
        assertEquals("MAJ QOAPM RR Delva Maria A. Rodrigues - Chefe do Compras/Almox", r.getRemetente());
        assertEquals("Sr. Cel QOPM R/R Diretor Presidente da Fundação Pró Tocantins", r.getDestinatario());
        assertEquals("Solicitação", r.getAssunto());
        assertTrue(r.getDescricao().contains("materiais odontológicos"));
        assertEquals(Natureza.SOLICITACAO_SETOR, r.getNatureza());
        assertEquals("Almoxarifado", r.getUnidadeSolicitante());
        assertTrue(r.getItens().isEmpty());
        assertTrue(r.getAvisos().stream().anyMatch(a -> a.contains("anexo separado")), r.getAvisos().toString());
    }

    @Test
    void oficioCompraDiretaComOrcamentos() {
        ExtracaoDocumentoCompra r = parser.parse(OFICIO_GAS);

        assertEquals(TipoDocumento.OFICIO, r.getTipoDocumento());
        assertEquals("136/2025", r.getNumeroDocumento());
        assertEquals("2025/09039/114662", r.getNumeroSgd());
        assertEquals(LocalDate.of(2025, 12, 23), r.getDataDocumento());
        assertEquals("Araguaína - TO", r.getLocalDocumento());
        assertEquals("FPTO Araguaína - Coord. Região Norte", r.getOrigemDocumento());
        assertEquals("CEL PM RR Coordenadora da Fundação Pró-Tocantins da Região Norte", r.getRemetente());
        assertEquals("Gás de cozinha (solicita)", r.getAssunto());
        assertEquals("2º BPM", r.getUnidadeSolicitante());
        assertEquals(Natureza.COMPRA_DIRETA, r.getNatureza());

        assertEquals(3, r.getOrcamentos().size());
        assertEquals("Tupy Gás", r.getOrcamentos().get(0).getEmpresa());
        assertEquals(new BigDecimal("120.00"), r.getOrcamentos().get(0).getValor());
        assertEquals("0800 646 1818", r.getOrcamentos().get(0).getTelefone());
        assertEquals("Toc Gás", r.getOrcamentos().get(2).getEmpresa());

        assertEquals(new BigDecimal("120.00"), r.getValorEstimado());
        assertEquals("Carvalho Comércio de gás Ltda", r.getFornecedor());
        assertTrue(r.getDadosBancarios().contains("Ag 0638-6"));
        assertTrue(r.getDadosBancarios().contains("C/C 103698-X"));

        assertEquals(1, r.getItens().size());
        assertEquals("Gás de cozinha", r.getItens().get(0).getDescricao());
        assertEquals(new BigDecimal("120.00"), r.getItens().get(0).getValorUnitario());
    }

    @Test
    void oficioPagamentoNotaFiscal() {
        ExtracaoDocumentoCompra r = parser.parse(OFICIO_NF);

        assertEquals(TipoDocumento.OFICIO, r.getTipoDocumento());
        assertEquals("001/2026", r.getNumeroDocumento());
        assertEquals("NÚCLEO DE SAÚDE", r.getOrigemDocumento());
        assertEquals("2026/09039/000362", r.getNumeroSgd());
        assertEquals(LocalDate.of(2026, 1, 5), r.getDataDocumento());
        assertEquals("Porto Nacional - TO", r.getLocalDocumento());
        assertEquals("Marciano Montelo Maranhão Monteiro - CEL QOPM, Diretor de Saúde", r.getDestinatario());
        assertEquals("Emerson Rodrigues Moura - MAJ QOPM - Resp. pelo Comando do 5º BPM", r.getRemetente());
        assertEquals("Solicitação para pagamento", r.getAssunto());
        assertEquals("5º BPM", r.getUnidadeSolicitante());
        assertEquals(Natureza.PAGAMENTO_NOTA_FISCAL, r.getNatureza());

        assertEquals("00000002", r.getNumeroNotaFiscal());
        assertEquals("17.380.000/0001-67", r.getCnpjFornecedor());
        assertEquals("DAVID WELLYNGTON VAZ-ME", r.getFornecedor());
        assertEquals(new BigDecimal("320.00"), r.getValorEstimado());
        assertTrue(r.getDadosBancarios().startsWith("BANCO DO BRASIL"));
        assertTrue(r.getDadosBancarios().contains("AG.: 1117-7"));

        assertEquals(1, r.getItens().size());
        assertEquals("REPARO DE PLACA FONTE", r.getItens().get(0).getDescricao());
        assertEquals(new BigDecimal("320.00"), r.getItens().get(0).getValorUnitario());
        assertTrue(r.getDescricao().contains("reparo da impressora"));
        assertFalse(r.getDescricao().contains("Respeitosamente"));
    }

    @Test
    void textoVazioGeraAvisoDeEscaneado() {
        ExtracaoDocumentoCompra r = parser.parse("   ");
        assertFalse(r.getAvisos().isEmpty());
        assertNull(r.getNumeroDocumento());
    }

    @Test
    void conversaoDeValoresBrasileiros() {
        assertEquals(new BigDecimal("8367.20"), ParteCompraParser.valor("8.367,20"));
        assertEquals(new BigDecimal("320.00"), ParteCompraParser.valor("320,00"));
        assertNull(ParteCompraParser.valor("abc"));
    }
}
