package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.dto.ColunaMaterial;
import com.fundacao.aualmoxarifado.dto.ContextoImportacao;
import com.fundacao.aualmoxarifado.dto.ModoEstoqueImportacao;
import com.fundacao.aualmoxarifado.dto.ResultadoImportacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * Orquestra a importação em massa de materiais a partir de planilha
 * (RF12 - carga e manutenção do catálogo).
 *
 * Responsabilidades desta classe: ler o arquivo, conferir se o cabeçalho faz
 * sentido e percorrer as linhas montando o relatório. A regra de negócio de
 * cada linha fica no {@link ImportacaoMaterialLinhaService} — que roda em
 * transação separada justamente para que este laço não seja transacional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoMaterialService {

    private final LeitorPlanilhaService leitor;
    private final ImportacaoMaterialLinhaService linhaService;

    /**
     * @param arquivo       .xlsx, .xls ou .csv enviado pelo usuário
     * @param modoEstoque   como tratar a coluna "estoque_atual"
     * @param criarAusentes cria Área/Subcategoria que ainda não existirem
     * @param simular       true = apenas prévia, nada é gravado
     */
    public ResultadoImportacao importar(MultipartFile arquivo,
                                        ModoEstoqueImportacao modoEstoque,
                                        boolean criarAusentes,
                                        boolean simular) {

        LeitorPlanilhaService.PlanilhaLida planilha = leitor.ler(arquivo);

        ResultadoImportacao resultado = new ResultadoImportacao();
        resultado.setNomeArquivo(arquivo.getOriginalFilename());
        resultado.setSimulacao(simular);
        resultado.setModoEstoque(modoEstoque);

        validarCabecalho(planilha, resultado);

        if (planilha.linhas().isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo tem cabeçalho mas nenhuma linha de dados.");
        }

        ContextoImportacao ctx = new ContextoImportacao(simular, criarAusentes, modoEstoque);
        for (LeitorPlanilhaService.LinhaPlanilha linha : planilha.linhas()) {
            resultado.adicionar(linhaService.processar(linha, ctx));
        }

        log.info("Importação de materiais [{}]: {} linha(s), {} criado(s), {} atualizado(s), {} erro(s){}",
                resultado.getNomeArquivo(), resultado.getTotalLinhas(), resultado.getTotalCriados(),
                resultado.getTotalAtualizados(), resultado.getTotalErros(),
                simular ? " (SIMULAÇÃO — nada gravado)" : "");

        return resultado;
    }

    /**
     * A coluna "nome" é o mínimo indispensável. Sem ela o arquivo quase
     * certamente é outra planilha qualquer — melhor avisar antes de processar
     * 500 linhas e devolver 500 erros idênticos.
     */
    private void validarCabecalho(LeitorPlanilhaService.PlanilhaLida planilha,
                                  ResultadoImportacao resultado) {

        boolean temNome = planilha.cabecalhos().stream().anyMatch(ColunaMaterial.NOME::reconhece);
        boolean temSku  = planilha.cabecalhos().stream().anyMatch(ColunaMaterial.SKU::reconhece);

        if (!temNome && !temSku) {
            throw new IllegalArgumentException(
                    "Não encontrei a coluna \"nome\" no arquivo. Confira se a primeira linha "
                  + "da planilha contém os títulos das colunas — baixe o modelo para conferir "
                  + "o formato esperado.");
        }

        planilha.cabecalhos().stream()
                .filter(c -> !c.isBlank())
                .filter(c -> Arrays.stream(ColunaMaterial.values()).noneMatch(col -> col.reconhece(c)))
                .distinct()
                .forEach(resultado.getColunasIgnoradas()::add);
    }
}
