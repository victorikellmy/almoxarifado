package com.fundacao.aualmoxarifado.dto.integracao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato enviado ao sistema de Patrimônio (Gerenciador Patrimonial) em
 * {@code POST {patrimonio.url}/api/integracao/almoxarifado/recebimentos}
 * quando uma compra é recebida com "será patrimoniado".
 *
 * <p>O Patrimônio guarda isto como uma <b>pendência de patrimoniamento</b>, mostra a
 * notificação ao usuário e pré-preenche o cadastro do bem (descrição, data e valor
 * de compra, nota fiscal, observação com setor/retirado por). {@code compraId} é a
 * chave de idempotência: reenvios do mesmo recebimento não duplicam pendências.</p>
 *
 * Datas em ISO-8601, valores como número, enums pelo nome.
 */
public record RecebimentoPatrimonioPayload(
        String origem,                    // sempre "ALMOXARIFADO"
        Long compraId,                    // id da compra no almoxarifado (idempotência)
        String numeroDocumento,           // nº da Parte/Ofício que originou a compra
        String numeroSgd,
        String assunto,
        String solicitanteDocumento,      // quem assinou a Parte/Ofício
        String fornecedor,
        String numeroNotaFiscal,
        LocalDateTime dataRecebimento,
        LocalDate dataCompra,             // = data do recebimento (sugestão para "data de aquisição")
        BigDecimal valorTotal,            // valor real final da NF
        String retiradoPor,               // quem retirou o bem no almoxarifado
        String setorDestino,              // setor/unidade que recebeu o bem (nome)
        String setorDestinoCentroCusto,
        String registradoPor,             // login de quem deu a baixa no almoxarifado
        String observacao,
        List<Item> itens,
        List<Anexo> anexos
) {
    public record Item(
            String descricao,
            String codigoSku,             // nulo para itens patrimoniais sem material do catálogo
            Integer quantidade,
            BigDecimal valorUnitario
    ) {}

    /** Nota fiscal em base64 para o Patrimônio guardar como anexo NOTA_FISCAL do bem. */
    public record Anexo(
            String tipo,                  // SOLICITACAO | NOTA_FISCAL
            String nomeOriginal,
            String contentType,
            Long tamanhoBytes,
            String conteudoBase64
    ) {}
}
