package com.fundacao.aualmoxarifado.service.report;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.MovimentacaoItem;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.ConsumoMaterialDTO;
import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.dto.EstoqueLinhaDTO;
import com.fundacao.aualmoxarifado.dto.GastoSetorDTO;
import com.fundacao.aualmoxarifado.dto.LinhaMesTipoDTO;
import com.fundacao.aualmoxarifado.dto.ResumoMesDTO;
import com.fundacao.aualmoxarifado.dto.ResumoTipoDTO;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.service.report.exporter.CsvExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.Exporter;
import com.fundacao.aualmoxarifado.service.report.exporter.PdfExporter;
import com.fundacao.aualmoxarifado.service.report.exporter.XlsxExporter;
import com.fundacao.aualmoxarifado.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Geração de relatórios em CSV, XLSX ou PDF.
 *
 * <p>Cobre estoque atual, alertas, consumo por setor (RF10) e os relatórios
 * gerenciais/contábeis de movimentações: mensal, trimestral e anual.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelatorioService {

    private final MaterialRepository materialRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final CsvExporter  csvExporter;
    private final XlsxExporter xlsxExporter;
    private final PdfExporter  pdfExporter;

    /**
     * Auto-referência via proxy: chamadas internas (this.dadosRelatorio*) não
     * passam pelo interceptor de cache; os exports usam {@code self} para que
     * página e exportações compartilhem a mesma entrada cacheada.
     */
    @Autowired @Lazy
    private RelatorioService self;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] NOMES_MES = {
            "Janeiro","Fevereiro","Março","Abril","Maio","Junho",
            "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"
    };

    public enum Formato { CSV, XLSX, PDF }

    /**
     * Relatório pronto para download — <b>sem materializar o arquivo inteiro em
     * memória</b>. O {@link #writer()} é executado só na hora de escrever na
     * resposta HTTP (via {@code StreamingResponseBody}), com o exporter gravando
     * direto no {@code OutputStream} do cliente. As linhas já são Strings prontas
     * (construídas dentro da transação), então o streaming não toca em entidade
     * lazy nem depende de sessão Hibernate aberta.
     */
    public record Arquivo(String nome, String contentType, ConteudoWriter writer) {
        @FunctionalInterface
        public interface ConteudoWriter {
            void writeTo(java.io.OutputStream out) throws Exception;
        }
    }

    /** Bundle de dados do relatório mensal — consumido pelo template. */
    public record DadosMensal(
            int ano, int mes, String nomeMes,
            List<ResumoTipoDTO> resumoPorTipo,
            ResumoTipoDTO totalGeral,
            List<ConsumoMaterialDTO> topMateriais,
            List<GastoSetorDTO> gastoPorSetor
    ) {}

    /**
     * Totais das linhas mensais, calculados uma única vez ao montar o bundle —
     * evita que o template re-itere {@code meses} com projeções SpEL no rodapé.
     */
    public record TotaisMeses(
            long qtdEntradas, long qtdSaidas, long qtdComprasDiretas,
            long qtdTotalMovimentacoes, long totalItens,
            BigDecimal valorEntradas, BigDecimal valorSaidas,
            BigDecimal valorComprasDiretas, BigDecimal valorTotal
    ) {}

    /** Bundle do relatório trimestral. */
    public record DadosTrimestral(
            int ano, int trimestre,
            List<ResumoMesDTO> meses,                 // 3 linhas
            List<ResumoTipoDTO> resumoPorTipo,
            ResumoTipoDTO totalGeral,
            TotaisMeses totaisMeses,
            List<GastoSetorDTO> gastoPorSetor
    ) {
        public String labelTrimestre() {
            return trimestre + "º trimestre / " + ano;
        }
    }

    /** Bundle do relatório anual (apresentação contábil). */
    public record DadosAnual(
            int ano,
            List<ResumoMesDTO> meses,                 // 12 linhas (mês 1..12, sempre completas)
            List<ResumoTipoDTO> resumoPorTipo,
            ResumoTipoDTO totalGeral,
            TotaisMeses totaisMeses,
            List<GastoSetorDTO> gastoPorSetor
    ) {}

    // =====================================================================
    // RELATÓRIOS EXISTENTES
    // =====================================================================

    public Arquivo estoqueAtual(Formato formato) {
        List<String> cabecalhos = List.of("SKU", "Material", "Área", "Subcategoria",
                "Unidade", "Estoque atual", "Estoque mínimo", "Valor unitário");
        List<List<String>> linhas = new ArrayList<>();
        // Projeção plana: 1 query, sem entidades gerenciadas nem lazy loading.
        for (EstoqueLinhaDTO m : materialRepository.linhasRelatorioEstoque()) {
            linhas.add(List.of(
                    nv(m.codigoSku()),
                    nv(m.nome()),
                    nv(m.nomeArea()),
                    nv(m.nomeSubcategoria()),
                    nv(m.unidadeMedida()),
                    String.valueOf(m.estoqueAtual()),
                    String.valueOf(m.estoqueMinimo()),
                    fmtMoeda(m.valorUnitario())
            ));
        }
        return gerar("Estoque atual", cabecalhos, linhas, formato);
    }

    public Arquivo alertasEstoque(Formato formato) {
        List<String> cabecalhos = List.of("SKU", "Material", "Área", "Subcategoria",
                "Estoque atual", "Estoque mínimo", "Faltam (mín - atual)");
        List<List<String>> linhas = new ArrayList<>();
        for (Material m : materialRepository.findEmAlertaDeEstoque()) {
            int falta = Math.max(0, m.getEstoqueMinimo() - m.getEstoqueAtual());
            linhas.add(List.of(
                    nv(m.getCodigoSku()),
                    nv(m.getNome()),
                    nomeArea(m),
                    nomeSubcategoria(m),
                    String.valueOf(m.getEstoqueAtual()),
                    String.valueOf(m.getEstoqueMinimo()),
                    String.valueOf(falta)
            ));
        }
        return gerar("Alertas de estoque", cabecalhos, linhas, formato);
    }

    public Arquivo consumoPorSetor(LocalDateTime inicio, LocalDateTime fim, Formato formato) {
        List<String> cabecalhos = List.of("Setor", "Centro de custo", "Total consumido");
        List<List<String>> linhas = new ArrayList<>();
        for (ConsumoSetorDTO c : movimentacaoRepository.consumoPorSetor(inicio, fim)) {
            linhas.add(List.of(
                    nv(c.nomeSetor()),
                    nv(c.codigoCentroCusto()),
                    Objects.toString(c.totalConsumido(), "0")
            ));
        }
        return gerar("Consumo por setor", cabecalhos, linhas, formato);
    }

    // =====================================================================
    // RELATÓRIO MENSAL
    // =====================================================================

    @Cacheable(cacheNames = CacheConfig.CACHE_REL_MENSAL, key = "#ano + '-' + #mes")
    public DadosMensal dadosRelatorioMensal(int ano, int mes) {
        YearMonth ym = YearMonth.of(ano, mes);
        LocalDateTime ini = ym.atDay(1).atStartOfDay();
        LocalDateTime fim = ym.atEndOfMonth().atTime(LocalTime.MAX);

        List<ResumoTipoDTO> resumo = movimentacaoRepository.resumoPorTipo(ini, fim);
        // LIMIT 10 no banco — antes a query trazia todos os materiais do período
        // e o subList ainda retinha a lista completa referenciada no record.
        List<ConsumoMaterialDTO> top =
                movimentacaoRepository.topMateriais(ini, fim, PageRequest.of(0, 10));
        List<GastoSetorDTO> setores = movimentacaoRepository.gastoPorSetor(ini, fim);

        return new DadosMensal(ano, mes, NOMES_MES[mes - 1],
                resumo, somarTipos(resumo), top, setores);
    }

    public Arquivo relatorioMensal(int ano, int mes, Formato formato) {
        DadosMensal d = self.dadosRelatorioMensal(ano, mes);
        List<String> cabecalhos = List.of("Seção", "Detalhe", "Qtd movs", "Itens", "Valor");
        List<List<String>> linhas = new ArrayList<>();

        // Resumo por tipo
        for (ResumoTipoDTO r : d.resumoPorTipo()) {
            linhas.add(List.of("Resumo por tipo", labelTipo(r.tipo()),
                    Objects.toString(r.qtdMovimentacoes(), "0"),
                    Objects.toString(r.totalItens(), "0"),
                    fmtMoeda(r.valorTotal())));
        }
        linhas.add(List.of("Resumo por tipo", "TOTAL",
                Objects.toString(d.totalGeral().qtdMovimentacoes(), "0"),
                Objects.toString(d.totalGeral().totalItens(), "0"),
                fmtMoeda(d.totalGeral().valorTotal())));

        // Top materiais
        int i = 1;
        for (ConsumoMaterialDTO c : d.topMateriais()) {
            linhas.add(List.of("Top materiais",
                    i++ + ". " + nv(c.sku()) + " — " + nv(c.nome()),
                    "",
                    Objects.toString(c.totalQuantidade(), "0"),
                    fmtMoeda(c.valorTotal())));
        }

        // Gasto por setor
        for (GastoSetorDTO g : d.gastoPorSetor()) {
            linhas.add(List.of("Gasto por setor", labelSetor(g),
                    "",
                    Objects.toString(g.totalQuantidade(), "0"),
                    fmtMoeda(g.valorTotal())));
        }

        String titulo = "Relatorio mensal " + ano + "-" + String.format("%02d", mes);
        return gerar(titulo, cabecalhos, linhas, formato);
    }

    // =====================================================================
    // RELATÓRIO TRIMESTRAL
    // =====================================================================

    @Cacheable(cacheNames = CacheConfig.CACHE_REL_TRIMESTRAL, key = "#ano + '-' + #trimestre")
    public DadosTrimestral dadosRelatorioTrimestral(int ano, int trimestre) {
        if (trimestre < 1 || trimestre > 4) {
            throw new RegraDeNegocioException("Trimestre deve estar entre 1 e 4");
        }
        int mesInicio = (trimestre - 1) * 3 + 1;
        DadosPeriodo p = carregarPeriodo(YearMonth.of(ano, mesInicio),
                                         YearMonth.of(ano, mesInicio + 2));
        return new DadosTrimestral(ano, trimestre, p.meses(), p.resumo(),
                somarTipos(p.resumo()), totaisDe(p.meses()), p.setores());
    }

    public Arquivo relatorioTrimestral(int ano, int trimestre, Formato formato) {
        DadosTrimestral d = self.dadosRelatorioTrimestral(ano, trimestre);
        List<String> cabecalhos = List.of("Mês", "Entradas", "Saídas", "Compras diretas",
                "Total movs", "Total itens",
                "Valor entradas", "Valor saídas", "Valor compras", "Valor total");
        List<List<String>> linhas = new ArrayList<>();
        for (ResumoMesDTO m : d.meses()) {
            linhas.add(linhaResumoMes(m));
        }
        // Linha de totais do trimestre
        linhas.add(linhaTotais(d.meses(), d.labelTrimestre()));

        String titulo = "Relatorio trimestral " + ano + "-T" + trimestre;
        return gerar(titulo, cabecalhos, linhas, formato);
    }

    // =====================================================================
    // RELATÓRIO ANUAL
    // =====================================================================

    @Cacheable(cacheNames = CacheConfig.CACHE_REL_ANUAL, key = "#ano")
    public DadosAnual dadosRelatorioAnual(int ano) {
        DadosPeriodo p = carregarPeriodo(YearMonth.of(ano, 1), YearMonth.of(ano, 12));
        return new DadosAnual(ano, p.meses(), p.resumo(), somarTipos(p.resumo()),
                totaisDe(p.meses()), p.setores());
    }

    /** Resumo consolidado anual — uma aba/linha por mês + totais por tipo + gasto por setor. */
    public Arquivo relatorioAnual(int ano, Formato formato) {
        DadosAnual d = self.dadosRelatorioAnual(ano);

        List<String> cabecalhos = List.of("Seção", "Detalhe",
                "Entradas", "Saídas", "Compras diretas",
                "Total movs", "Total itens",
                "Valor entradas", "Valor saídas", "Valor compras", "Valor total");
        List<List<String>> linhas = new ArrayList<>();

        for (ResumoMesDTO m : d.meses()) {
            List<String> base = linhaResumoMes(m);
            List<String> linha = new ArrayList<>(11);
            linha.add("Consolidado mensal");
            linha.addAll(base);
            linhas.add(linha);
        }
        // Total anual
        List<String> totais = linhaTotais(d.meses(), "TOTAL " + ano);
        List<String> linhaTotal = new ArrayList<>(11);
        linhaTotal.add("Consolidado mensal");
        linhaTotal.addAll(totais);
        linhas.add(linhaTotal);

        // Gasto por setor (colunas extras vazias para alinhar)
        for (GastoSetorDTO g : d.gastoPorSetor()) {
            linhas.add(List.of("Gasto por setor", labelSetor(g), "", "", "", "",
                    Objects.toString(g.totalQuantidade(), "0"),
                    "", "", "", fmtMoeda(g.valorTotal())));
        }

        return gerar("Relatorio anual " + ano, cabecalhos, linhas, formato);
    }

    /**
     * Versão DETALHADA do anual: uma linha por item de cada movimentação do ano.
     * Útil para a contabilidade conferir lançamentos.
     */
    public Arquivo relatorioAnualDetalhado(int ano, Formato formato) {
        YearMonth ymIni = YearMonth.of(ano, 1);
        YearMonth ymFim = YearMonth.of(ano, 12);
        LocalDateTime ini = ymIni.atDay(1).atStartOfDay();
        LocalDateTime fim = ymFim.atEndOfMonth().atTime(LocalTime.MAX);

        List<String> cabecalhos = List.of(
                "Mov #", "Data", "Tipo", "Status",
                "Setor destino", "Centro de custo", "Retirado por",
                "Fornecedor", "Nota fiscal",
                "SKU", "Material", "Unidade",
                "Quantidade", "Valor unitário", "Subtotal");
        List<List<String>> linhas = new ArrayList<>();

        for (Movimentacao m : movimentacaoRepository.findEfetivasNoIntervalo(ini, fim)) {
            String setorNome = m.getSetorDestino() != null ? nv(m.getSetorDestino().getNome()) : "";
            String cc = m.getSetorDestino() != null ? nv(m.getSetorDestino().getCodigoCentroCusto()) : "";

            for (MovimentacaoItem item : m.getItens()) {
                Material mat = item.getMaterial();
                BigDecimal subtotal = item.getValorUnitario() != null
                        ? item.getValorUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))
                        : null;
                linhas.add(List.of(
                        String.valueOf(m.getId()),
                        m.getData() != null ? m.getData().format(DTF) : "",
                        labelTipo(m.getTipo()),
                        m.getStatus() != null ? m.getStatus().name() : "",
                        setorNome, cc, nv(m.getRetiradoPor()),
                        nv(m.getFornecedor()), nv(m.getNotaFiscal()),
                        nv(mat.getCodigoSku()), nv(mat.getNome()), nv(mat.getUnidadeMedida()),
                        String.valueOf(item.getQuantidade()),
                        fmtMoeda(item.getValorUnitario()),
                        fmtMoeda(subtotal)
                ));
            }
        }
        return gerar("Relatorio anual detalhado " + ano, cabecalhos, linhas, formato);
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    /** Dados comuns aos relatórios de período (trimestral e anual). */
    private record DadosPeriodo(List<ResumoMesDTO> meses,
                                List<ResumoTipoDTO> resumo,
                                List<GastoSetorDTO> setores) {}

    private DadosPeriodo carregarPeriodo(YearMonth ymIni, YearMonth ymFim) {
        LocalDateTime ini = ymIni.atDay(1).atStartOfDay();
        LocalDateTime fim = ymFim.atEndOfMonth().atTime(LocalTime.MAX);

        List<LinhaMesTipoDTO> bruto = movimentacaoRepository.agregadoPorMesTipo(ini, fim);
        return new DadosPeriodo(
                pivotarParaMeses(bruto, ymIni, ymFim),
                movimentacaoRepository.resumoPorTipo(ini, fim),
                movimentacaoRepository.gastoPorSetor(ini, fim));
    }

    /** Label "Setor [CC 123]" usado nas seções de gasto por setor. */
    private static String labelSetor(GastoSetorDTO g) {
        return nv(g.nomeSetor())
                + (g.codigoCentroCusto() != null ? " [CC " + g.codigoCentroCusto() + "]" : "");
    }

    /**
     * Pivota o agregado por (ano, mês, tipo) numa lista de ResumoMesDTO,
     * preenchendo meses sem dados com zeros para o intervalo inteiro.
     */
    private static List<ResumoMesDTO> pivotarParaMeses(List<LinhaMesTipoDTO> bruto,
                                                       YearMonth ymIni, YearMonth ymFim) {
        Map<YearMonth, Map<TipoMovimentacao, LinhaMesTipoDTO>> mapa = new HashMap<>();
        for (LinhaMesTipoDTO l : bruto) {
            YearMonth ym = YearMonth.of(l.ano(), l.mes());
            mapa.computeIfAbsent(ym, k -> new EnumMap<>(TipoMovimentacao.class)).put(l.tipo(), l);
        }
        List<ResumoMesDTO> out = new ArrayList<>();
        YearMonth cursor = ymIni;
        while (!cursor.isAfter(ymFim)) {
            Map<TipoMovimentacao, LinhaMesTipoDTO> porTipo = mapa.getOrDefault(cursor, Map.of());
            LinhaMesTipoDTO e = porTipo.get(TipoMovimentacao.ENTRADA);
            LinhaMesTipoDTO s = porTipo.get(TipoMovimentacao.SAIDA);
            LinhaMesTipoDTO c = porTipo.get(TipoMovimentacao.COMPRA_DIRETA);
            long totalItens = soma(e == null ? 0L : nz(e.totalItens()),
                                   s == null ? 0L : nz(s.totalItens()),
                                   c == null ? 0L : nz(c.totalItens()));
            out.add(new ResumoMesDTO(
                    cursor.getYear(),
                    cursor.getMonthValue(),
                    e == null ? 0L : nz(e.qtdMovimentacoes()),
                    s == null ? 0L : nz(s.qtdMovimentacoes()),
                    c == null ? 0L : nz(c.qtdMovimentacoes()),
                    totalItens,
                    e == null ? BigDecimal.ZERO : nz(e.valorTotal()),
                    s == null ? BigDecimal.ZERO : nz(s.valorTotal()),
                    c == null ? BigDecimal.ZERO : nz(c.valorTotal())
            ));
            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    private static ResumoTipoDTO somarTipos(List<ResumoTipoDTO> lista) {
        long qtd = 0, itens = 0;
        BigDecimal valor = BigDecimal.ZERO;
        for (ResumoTipoDTO r : lista) {
            qtd += nz(r.qtdMovimentacoes());
            itens += nz(r.totalItens());
            valor = valor.add(nz(r.valorTotal()));
        }
        return new ResumoTipoDTO(null, qtd, itens, valor);
    }

    private static List<String> linhaResumoMes(ResumoMesDTO m) {
        return List.of(
                m.mes() + "/" + m.ano() + " (" + NOMES_MES[m.mes() - 1] + ")",
                Objects.toString(m.qtdEntradas(), "0"),
                Objects.toString(m.qtdSaidas(), "0"),
                Objects.toString(m.qtdComprasDiretas(), "0"),
                Objects.toString(m.qtdTotalMovimentacoes(), "0"),
                Objects.toString(m.totalItens(), "0"),
                fmtMoeda(m.valorEntradas()),
                fmtMoeda(m.valorSaidas()),
                fmtMoeda(m.valorComprasDiretas()),
                fmtMoeda(m.valorTotal())
        );
    }

    /** Soma as linhas mensais numa única passada. */
    private static TotaisMeses totaisDe(List<ResumoMesDTO> meses) {
        long qe = 0, qs = 0, qc = 0, qi = 0;
        BigDecimal ve = BigDecimal.ZERO, vs = BigDecimal.ZERO, vc = BigDecimal.ZERO;
        for (ResumoMesDTO m : meses) {
            qe += nz(m.qtdEntradas());
            qs += nz(m.qtdSaidas());
            qc += nz(m.qtdComprasDiretas());
            qi += nz(m.totalItens());
            ve = ve.add(nz(m.valorEntradas()));
            vs = vs.add(nz(m.valorSaidas()));
            vc = vc.add(nz(m.valorComprasDiretas()));
        }
        return new TotaisMeses(qe, qs, qc, qe + qs + qc, qi,
                ve, vs, vc, ve.add(vs).add(vc));
    }

    private static List<String> linhaTotais(List<ResumoMesDTO> meses, String label) {
        TotaisMeses t = totaisDe(meses);
        return List.of(label,
                String.valueOf(t.qtdEntradas()), String.valueOf(t.qtdSaidas()),
                String.valueOf(t.qtdComprasDiretas()),
                String.valueOf(t.qtdTotalMovimentacoes()), String.valueOf(t.totalItens()),
                fmtMoeda(t.valorEntradas()), fmtMoeda(t.valorSaidas()),
                fmtMoeda(t.valorComprasDiretas()), fmtMoeda(t.valorTotal()));
    }

    private static long soma(long... vs) {
        long s = 0;
        for (long v : vs) s += v;
        return s;
    }
    private static long nz(Long v) { return v == null ? 0L : v; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Arquivo gerar(String titulo, List<String> cabecalhos,
                          List<List<String>> linhas, Formato formato) {
        Exporter exp = switch (formato) {
            case CSV  -> csvExporter;
            case XLSX -> xlsxExporter;
            case PDF  -> pdfExporter;
        };
        String nome = titulo.toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replace('/', '-') + "." + exp.fileExtension();
        // Escrita adiada: o exporter grava direto no stream da resposta,
        // sem passar por um byte[] intermediário do arquivo inteiro.
        return new Arquivo(nome, exp.contentType(),
                out -> exp.exportar(titulo, cabecalhos, linhas, out));
    }

    private static String labelTipo(TipoMovimentacao t) {
        if (t == null) return "";
        return switch (t) {
            case ENTRADA -> "Entrada";
            case SAIDA -> "Saída";
            case COMPRA_DIRETA -> "Compra direta";
        };
    }

    private static String nomeSubcategoria(Material m) {
        return m.getSubcategoria() != null ? nv(m.getSubcategoria().getNome()) : "";
    }

    private static String nomeArea(Material m) {
        return (m.getSubcategoria() != null && m.getSubcategoria().getArea() != null)
                ? nv(m.getSubcategoria().getArea().getNome())
                : "";
    }

    private static String nv(String s) { return s == null ? "" : s; }

    private static String fmtMoeda(BigDecimal v) {
        if (v == null) return "";
        return "R$ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
