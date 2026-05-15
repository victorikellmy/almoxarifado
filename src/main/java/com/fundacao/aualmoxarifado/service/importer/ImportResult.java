package com.fundacao.aualmoxarifado.service.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da importação em lote.
 */
public class ImportResult {

    private int totalLinhas;
    private int importados;
    private int atualizados;
    private int ignorados;
    private final List<String> avisos = new ArrayList<>();
    private final List<String> erros = new ArrayList<>();

    public void contarImportado()  { importados++;  }
    public void contarAtualizado() { atualizados++; }
    public void contarIgnorado()   { ignorados++;   }
    public void contarLinha()      { totalLinhas++; }

    public void aviso(String msg) { avisos.add(msg); }
    public void erro(String msg)  { erros.add(msg);  }

    public int getTotalLinhas() { return totalLinhas; }
    public int getImportados()  { return importados; }
    public int getAtualizados() { return atualizados; }
    public int getIgnorados()   { return ignorados; }
    public List<String> getAvisos() { return avisos; }
    public List<String> getErros()  { return erros; }
}
