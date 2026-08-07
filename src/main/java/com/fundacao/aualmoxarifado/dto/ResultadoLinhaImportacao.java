package com.fundacao.aualmoxarifado.dto;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado do processamento de UMA linha da planilha.
 *
 * O importador nunca aborta o lote inteiro por causa de uma linha ruim: cada
 * linha vira um destes objetos e o usuário recebe um relatório completo do que
 * entrou, do que foi atualizado e do que falhou (com o número da linha para
 * ele corrigir na planilha original).
 */
@Getter
public class ResultadoLinhaImportacao {

    public enum Situacao {
        CRIADO("Criado", "success"),
        ATUALIZADO("Atualizado", "info"),
        IGNORADO("Ignorado", "secondary"),
        ERRO("Erro", "danger");

        private final String rotulo;
        private final String cor;

        Situacao(String rotulo, String cor) {
            this.rotulo = rotulo;
            this.cor = cor;
        }

        public String getRotulo() { return rotulo; }
        public String getCor() { return cor; }
    }

    /** Número da linha na planilha original (1 = cabeçalho), para o usuário localizar o erro. */
    private final int numeroLinha;
    private final String nome;
    private Situacao situacao;
    private String sku;
    private String mensagem;

    /** Observações não-fatais: "área criada automaticamente", "SKU já existia", etc. */
    private final List<String> avisos = new ArrayList<>();

    public ResultadoLinhaImportacao(int numeroLinha, String nome) {
        this.numeroLinha = numeroLinha;
        this.nome = (nome == null || nome.isBlank()) ? "(sem nome)" : nome;
    }

    public ResultadoLinhaImportacao criado(String sku, String mensagem) {
        this.situacao = Situacao.CRIADO;
        this.sku = sku;
        this.mensagem = mensagem;
        return this;
    }

    public ResultadoLinhaImportacao atualizado(String sku, String mensagem) {
        this.situacao = Situacao.ATUALIZADO;
        this.sku = sku;
        this.mensagem = mensagem;
        return this;
    }

    public ResultadoLinhaImportacao ignorado(String mensagem) {
        this.situacao = Situacao.IGNORADO;
        this.mensagem = mensagem;
        return this;
    }

    public ResultadoLinhaImportacao erro(String mensagem) {
        this.situacao = Situacao.ERRO;
        this.mensagem = mensagem;
        return this;
    }

    public ResultadoLinhaImportacao aviso(String aviso) {
        this.avisos.add(aviso);
        return this;
    }

    /** Usado no template para pintar a linha de vermelho. */
    public boolean isFalhou() {
        return situacao == Situacao.ERRO;
    }
}
