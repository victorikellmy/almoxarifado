package com.fundacao.aualmoxarifado.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Relatório consolidado de uma importação de materiais.
 *
 * Serve tanto para a SIMULAÇÃO (quando {@link #simulacao} é true, nada foi
 * gravado — é só uma prévia) quanto para a importação definitiva.
 */
@Getter
@Setter
public class ResultadoImportacao {

    private String nomeArquivo;
    private boolean simulacao;
    private ModoEstoqueImportacao modoEstoque;

    /** Colunas do arquivo que o sistema não reconheceu — apenas informativo. */
    private final List<String> colunasIgnoradas = new ArrayList<>();

    private final List<ResultadoLinhaImportacao> linhas = new ArrayList<>();

    public void adicionar(ResultadoLinhaImportacao linha) {
        linhas.add(linha);
    }

    public long getTotalLinhas() {
        return linhas.size();
    }

    public long getTotalCriados() {
        return contar(ResultadoLinhaImportacao.Situacao.CRIADO);
    }

    public long getTotalAtualizados() {
        return contar(ResultadoLinhaImportacao.Situacao.ATUALIZADO);
    }

    public long getTotalIgnorados() {
        return contar(ResultadoLinhaImportacao.Situacao.IGNORADO);
    }

    public long getTotalErros() {
        return contar(ResultadoLinhaImportacao.Situacao.ERRO);
    }

    public boolean isTemErros() {
        return getTotalErros() > 0;
    }

    /** Só as linhas com erro — o template mostra essa lista primeiro. */
    public List<ResultadoLinhaImportacao> getLinhasComErro() {
        return linhas.stream().filter(ResultadoLinhaImportacao::isFalhou).toList();
    }

    private long contar(ResultadoLinhaImportacao.Situacao situacao) {
        return linhas.stream().filter(l -> l.getSituacao() == situacao).count();
    }
}
