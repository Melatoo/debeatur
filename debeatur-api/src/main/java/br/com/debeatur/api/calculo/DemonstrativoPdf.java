package br.com.debeatur.api.calculo;

import br.com.debeatur.api.calculo.DemonstrativoResponse.Linha;
import br.com.debeatur.api.calculo.DemonstrativoResponse.MesPensao;
import br.com.debeatur.api.calculo.RegimeDto.RegraDto;
import br.com.debeatur.api.calculo.RegimeDto.TipoJuros;
import br.com.debeatur.core.alimentos.InicioJuros;
import br.com.debeatur.core.alimentos.Rito;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.indices.SgsClient;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Demonstrativo em PDF para juntar aos autos: identificação do processo, critérios do
 * cálculo, memória linha a linha, totais com o art. 523, §1º, e as fontes dos índices.
 * <p>
 * Só apresenta o que já está em {@link DemonstrativoResponse}: nenhum valor é recalculado
 * aqui, e os totais são os somados pelo motor.
 */
final class DemonstrativoPdf {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Color TEXTO = new Color(0x1F, 0x29, 0x33);
    private static final Color SUAVE = new Color(0x5B, 0x67, 0x73);
    private static final Color FUNDO_CABECALHO = new Color(0xE9, 0xEC, 0xEF);
    private static final Color FUNDO_TOTAL = new Color(0xF4, 0xF5, 0xF7);
    private static final Color LINHA = new Color(0xC4, 0xCA, 0xD0);

    private static final Font TITULO = new Font(Font.HELVETICA, 13, Font.BOLD, TEXTO);
    private static final Font SECAO = new Font(Font.HELVETICA, 10, Font.BOLD, TEXTO);
    private static final Font NORMAL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXTO);
    private static final Font NEGRITO = new Font(Font.HELVETICA, 8.5f, Font.BOLD, TEXTO);
    private static final Font ROTULO = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, SUAVE);
    private static final Font TABELA = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, TEXTO);
    private static final Font TABELA_NEGRITO = new Font(Font.HELVETICA, 7.5f, Font.BOLD, TEXTO);
    private static final Font NOTA = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, SUAVE);

    private static final Map<Rito, String[]> RITOS = Map.of(
            Rito.PRISAO, new String[]{"Rito da prisão",
                    "Art. 528, §3º, do CPC: as três parcelas vencidas até o ajuizamento e as que vencem depois dele."},
            Rito.PENHORA, new String[]{"Rito da penhora",
                    "Art. 528, §8º, do CPC: as parcelas mais antigas, com multa e honorários do art. 523, §1º."});

    /** Colunas com total no rodapé, na ordem em que aparecem. */
    private enum Total { CORRIGIDO, JUROS, ATUALIZADO }

    private record Coluna(String titulo, float largura, boolean numero, Function<Linha, String> valor, Total total) {
    }

    /** Totais de um grupo de linhas, sempre os somados pelo motor. */
    private record Totais(BigDecimal corrigido, BigDecimal juros, BigDecimal atualizado) {

        BigDecimal de(Total total) {
            return switch (total) {
                case CORRIGIDO -> corrigido;
                case JUROS -> juros;
                case ATUALIZADO -> atualizado;
            };
        }
    }

    private final CalculoRequest pedido;
    private final DemonstrativoResponse resultado;
    private final LocalDateTime emissao;
    private final List<Coluna> colunas;
    private final boolean comAcrescimos;
    private PdfWriter writer;

    private DemonstrativoPdf(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao) {
        this.pedido = pedido;
        this.resultado = resultado;
        this.emissao = emissao;
        this.colunas = colunas(resultado.linhas());
        this.comAcrescimos = resultado.multaArt523().signum() > 0 || resultado.honorariosArt523().signum() > 0;
    }

    /** @param emissao data e hora impressas no cabeçalho e no rodapé */
    static byte[] gerar(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao) {
        return new DemonstrativoPdf(pedido, resultado, emissao).gerar();
    }

    /** Ex.: {@code demonstrativo-5001234-56.2024.8.13.0024-2026-10-31.pdf}. */
    static String nomeDoArquivo(CalculoRequest pedido) {
        String numero = pedido.processo() == null ? null : texto(pedido.processo().numero());
        String identificacao = numero == null ? "" : numero.replaceAll("[^0-9A-Za-z.-]+", "_") + "-";
        return "demonstrativo-" + identificacao + pedido.dataBase() + ".pdf";
    }

    private byte[] gerar() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        // paisagem: a memória chega a onze colunas, e retrato as espremeria
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 46);
        writer = PdfWriter.getInstance(documento, saida);
        // 1.7, e não 2.0: validadores de peticionamento eletrônico ainda recusam PDF 2.0
        writer.setPdfVersion(PdfWriter.VERSION_1_7);
        writer.setPageEvent(new Rodape());
        documento.addTitle("Demonstrativo do débito atualizado até " + data(resultado.dataBase()));
        documento.addCreator("Debeatur");
        documento.open();

        cabecalho(documento);
        criterios(documento);
        memoria(documento);
        totais(documento);
        fontes(documento);

        documento.close();
        return saida.toByteArray();
    }

    // ---- cabeçalho -----------------------------------------------------------------------

    private void cabecalho(Document documento) {
        Paragraph titulo = new Paragraph("Demonstrativo discriminado e atualizado do débito", TITULO);
        documento.add(titulo);
        Paragraph subtitulo = new Paragraph(pedido.pensao() == null
                ? "Cumprimento de sentença (art. 524 do CPC)"
                : "Execução de alimentos (arts. 524 e 528 do CPC)", ROTULO);
        subtitulo.setSpacingAfter(10);
        documento.add(subtitulo);

        Map<String, String> campos = new LinkedHashMap<>();
        ProcessoRequest processo = pedido.processo();
        if (processo != null) {
            campos.put("Processo", texto(processo.numero()));
            campos.put("Juízo", texto(processo.juizo()));
            campos.put("Exequente", texto(processo.exequente()));
            campos.put("Executado", texto(processo.executado()));
        }
        campos.put("Data-base do cálculo", data(resultado.dataBase()));
        campos.put("Emitido em", emissao.format(DATA) + " às " + emissao.format(HORA));
        campos.values().removeIf(Objects::isNull);
        documento.add(pares(campos));
    }

    // ---- critérios -----------------------------------------------------------------------

    private void criterios(Document documento) {
        documento.add(secao("Critérios do cálculo"));
        Map<String, String> campos = new LinkedHashMap<>();
        PensaoRequest pensao = pedido.pensao();
        int avulsas = pedido.parcelas() == null ? 0 : pedido.parcelas().size();
        if (pensao != null) {
            campos.put("Pensão", numeroLivre(pensao.percentual()) + "% do salário mínimo vigente em cada mês, com vencimento no dia "
                    + pensao.diaVencimento());
            campos.put("Parcelas", pensao.primeiroMes().format(MES) + " a " + pensao.ultimoMes().format(MES)
                    + (avulsas > 0 ? ", mais " + contar(avulsas, "parcela avulsa", "parcelas avulsas") : ""));
            campos.put("Juros", pensao.inicioJuros() == InicioJuros.CITACAO
                    ? "Desde a citação (" + data(pensao.citacao()) + ") para as parcelas vencidas antes dela; as seguintes, desde o vencimento"
                    : "Desde o vencimento de cada parcela");
            campos.put("Pagamentos parciais", pagamentos());
            campos.put("Ajuizamento", resultado.ritos() == null
                    ? "Ritos não separados" : data(resultado.ritos().ajuizamento()));
        } else {
            campos.put("Parcelas", contar(avulsas, "parcela", "parcelas"));
        }
        campos.put("Art. 523, §1º", art523());
        campos.put("Correção e juros", resultado.regime().nome());
        documento.add(pares(campos));

        List<String> regras = regrasDoPeriodo().stream()
                .map(r -> periodo(r) + ": " + nomeIndice(r.correcao()) + " + " + r.descricaoJuros())
                .toList();
        PdfPTable tabela = semBordas(2, new float[]{1.6f, 8.4f});
        for (String regra : regras) {
            tabela.addCell(celulaLivre("", ROTULO));
            tabela.addCell(celulaLivre("• " + regra, NORMAL));
        }
        documento.add(tabela);
    }

    private String pagamentos() {
        List<MesPensao> pagos = resultado.linhas().stream()
                .map(Linha::pensao)
                .filter(p -> p != null && p.valorPago().signum() > 0)
                .sorted(Comparator.comparing(MesPensao::mes))
                .toList();
        if (pagos.isEmpty()) {
            return "Nenhum";
        }
        return String.join("; ", pagos.stream().map(p -> p.mes().format(MES) + ": " + moeda(p.valorPago())).toList());
    }

    private String art523() {
        boolean ritos = resultado.ritos() != null;
        if (comAcrescimos) {
            return "Multa de 10% e honorários de 10% sobre " + (ritos ? "o débito da penhora" : "o débito atualizado");
        }
        return pedido.aplicarArt523()
                ? "Multa e honorários de 10%, sem débito " + (ritos ? "na penhora" : "a acrescer")
                : "Não aplicados";
    }

    /**
     * Regras que alcançam o período do cálculo: do primeiro termo inicial ao mês anterior à
     * data-base, o último que o motor corrige e sobre o qual conta juros. Se tudo vence no
     * mês da data-base, fica a regra vigente nesse mês.
     */
    private List<RegraDto> regrasDoPeriodo() {
        List<RegraDto> regras = resultado.regime().regras();
        YearMonth primeiro = resultado.linhas().stream()
                .flatMap(l -> java.util.stream.Stream.of(l.inicioCorrecao(),
                        l.inicioJuros() == null ? l.inicioCorrecao() : l.inicioJuros()))
                .map(YearMonth::from)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (primeiro == null) {
            return regras;
        }
        YearMonth anteriorABase = YearMonth.from(resultado.dataBase()).minusMonths(1);
        YearMonth ultimo = primeiro.isAfter(anteriorABase) ? primeiro : anteriorABase;
        return regras.stream()
                .filter(r -> !r.inicio().isAfter(ultimo) && (r.fim() == null || !r.fim().isBefore(primeiro)))
                .toList();
    }

    // ---- memória de cálculo --------------------------------------------------------------

    private static List<Coluna> colunas(List<Linha> linhas) {
        boolean comSalarioMinimo = linhas.stream().anyMatch(l -> l.pensao() != null);
        boolean comPagamento = linhas.stream().anyMatch(l -> l.pensao() != null && l.pensao().valorPago().signum() > 0);
        List<Coluna> colunas = new ArrayList<>();
        colunas.add(new Coluna("Parcela", 1.6f, false, DemonstrativoPdf::nomeDaParcela, null));
        colunas.add(new Coluna("Correção desde", 1.0f, false, l -> data(l.inicioCorrecao()), null));
        colunas.add(new Coluna("Juros desde", 1.0f, false,
                l -> l.inicioJuros() == null ? "sem juros" : data(l.inicioJuros()), null));
        if (comSalarioMinimo) {
            colunas.add(new Coluna("Salário mínimo (R$)", 1.1f, true,
                    l -> l.pensao() == null ? "—" : decimal(l.pensao().salarioMinimo()), null));
        }
        colunas.add(new Coluna("Devido (R$)", 1.1f, true,
                l -> decimal(l.pensao() == null ? l.valorOriginal() : l.pensao().valorDevido()), null));
        if (comPagamento) {
            colunas.add(new Coluna("Pago (R$)", 1.0f, true,
                    l -> l.pensao() == null || l.pensao().valorPago().signum() == 0 ? "—" : decimal(l.pensao().valorPago()),
                    null));
        }
        colunas.add(new Coluna("Fator de correção", 1.1f, true, l -> fator(l.fatorCorrecao()), null));
        colunas.add(new Coluna("Corrigido (R$)", 1.2f, true, l -> decimal(l.valorCorrigido()), Total.CORRIGIDO));
        colunas.add(new Coluna("Juros (%)", 0.9f, true, l -> percentual(l.percentualJuros()), null));
        colunas.add(new Coluna("Juros (R$)", 1.1f, true, l -> decimal(l.valorJuros()), Total.JUROS));
        colunas.add(new Coluna("Atualizado (R$)", 1.2f, true, l -> decimal(l.subtotal()), Total.ATUALIZADO));
        return List.copyOf(colunas);
    }

    private static String nomeDaParcela(Linha l) {
        if (l.pensao() == null) {
            return l.descricao();
        }
        boolean quitada = l.pensao().valorPago().signum() > 0 && l.valorOriginal().signum() == 0;
        return l.pensao().mes().format(MES) + (quitada ? " (quitada)" : "");
    }

    private void memoria(Document documento) {
        documento.add(secao("Memória de cálculo"));
        Paragraph explicacao = new Paragraph(explicacaoDasColunas(), NOTA);
        explicacao.setSpacingAfter(6);
        documento.add(explicacao);

        DemonstrativoResponse.Ritos ritos = resultado.ritos();
        if (ritos == null) {
            Totais totais = new Totais(resultado.totalCorrigido(), resultado.totalJuros(), resultado.debitoAtualizado());
            tabela(documento, resultado.linhas(), totais, true);
            return;
        }
        for (Rito rito : Rito.values()) {
            DemonstrativoResponse.Subtotal subtotal = rito == Rito.PRISAO ? ritos.prisao() : ritos.penhora();
            // o título do rito não fica no pé da página, longe da tabela
            reservar(documento, 90);
            Paragraph titulo = new Paragraph(RITOS.get(rito)[0], NEGRITO);
            titulo.setSpacingBefore(6);
            documento.add(titulo);
            Paragraph regra = new Paragraph(RITOS.get(rito)[1], NOTA);
            regra.setSpacingAfter(4);
            documento.add(regra);
            List<Linha> doRito = resultado.linhas().stream().filter(l -> l.rito() == rito).toList();
            Totais totais = new Totais(subtotal.totalCorrigido(), subtotal.totalJuros(), subtotal.debitoAtualizado());
            tabela(documento, doRito, totais, rito == Rito.PENHORA);
        }
    }

    private String explicacaoDasColunas() {
        String pensao = resultado.linhas().stream().anyMatch(l -> l.pensao() != null)
                ? "O devido é o percentual do salário mínimo vigente no mês, e o pago é abatido antes da correção. "
                : "";
        return pensao + "O fator de correção é o produto de (1 + variação) do índice em cada mês, do mês do termo inicial "
                + "da correção até o mês anterior ao da data-base, sem arredondamento. Os juros de mora são simples: "
                + "o percentual soma as taxas dos meses do termo inicial dos juros até o mês anterior ao da data-base "
                + "e incide sobre o valor corrigido. Valores monetários arredondados a duas casas (meio para cima).";
    }

    /**
     * A memória de um grupo de linhas: as parcelas numa tabela que se divide entre páginas, com
     * o cabeçalho repetido, e o fechamento (subtotal e art. 523) noutra, que não se divide. Se
     * o fechamento não cabe no resto da página, vai para a seguinte com o cabeçalho das colunas,
     * para que os totais não fiquem sem rótulo.
     *
     * @param comArt523 se a multa e os honorários do art. 523 entram no fechamento desta tabela
     */
    private void tabela(Document documento, List<Linha> linhas, Totais totais, boolean comArt523) {
        PdfPTable parcelas = novaTabela();
        parcelas.setHeaderRows(1);
        cabecalhoDasColunas(parcelas);
        List<Linha> ordenadas = linhas.stream().sorted(Comparator.comparing(Linha::inicioCorrecao)).toList();
        for (Linha linha : ordenadas) {
            for (Coluna coluna : colunas) {
                parcelas.addCell(celula(coluna.valor().apply(linha), TABELA, coluna.numero()));
            }
        }
        if (ordenadas.isEmpty()) {
            PdfPCell vazia = celula("Nenhuma parcela neste rito.", NOTA, false);
            vazia.setColspan(colunas.size());
            parcelas.addCell(vazia);
        }
        documento.add(parcelas);

        PdfPTable fechamento = fechamento(totais, ordenadas.size(), comArt523, false);
        fechamento.setTotalWidth(documento.right() - documento.left());
        fechamento.setLockedWidth(true);
        if (fechamento.getTotalHeight() > writer.getVerticalPosition(true) - documento.bottom()) {
            fechamento = fechamento(totais, ordenadas.size(), comArt523, true);
        }
        documento.add(fechamento);
    }

    private PdfPTable novaTabela() {
        float[] larguras = new float[colunas.size()];
        for (int i = 0; i < larguras.length; i++) {
            larguras[i] = colunas.get(i).largura();
        }
        PdfPTable tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100);
        return tabela;
    }

    private void cabecalhoDasColunas(PdfPTable tabela) {
        for (Coluna coluna : colunas) {
            PdfPCell celula = celula(coluna.titulo(), TABELA_NEGRITO, coluna.numero());
            celula.setBackgroundColor(FUNDO_CABECALHO);
            tabela.addCell(celula);
        }
    }

    /** @param comCabecalho se repete o cabeçalho das colunas, para o fechamento que abre uma página */
    private PdfPTable fechamento(Totais totais, int parcelas, boolean comArt523, boolean comCabecalho) {
        PdfPTable tabela = novaTabela();
        tabela.setKeepTogether(true);
        tabela.setSpacingAfter(8);
        if (comCabecalho) {
            cabecalhoDasColunas(tabela);
        }
        int primeiraComTotal = 0;
        while (colunas.get(primeiraComTotal).total() == null) {
            primeiraComTotal++;
        }
        PdfPCell rotulo = celula("Subtotal, " + contar(parcelas, "parcela", "parcelas"), TABELA_NEGRITO, false);
        rotulo.setColspan(primeiraComTotal);
        rotulo.setBackgroundColor(FUNDO_TOTAL);
        tabela.addCell(rotulo);
        for (Coluna coluna : colunas.subList(primeiraComTotal, colunas.size())) {
            PdfPCell celula = celula(coluna.total() == null ? "" : decimal(totais.de(coluna.total())), TABELA_NEGRITO, true);
            celula.setBackgroundColor(FUNDO_TOTAL);
            tabela.addCell(celula);
        }

        if (comArt523 && comAcrescimos) {
            linhaDeTotal(tabela, "Multa de 10% (art. 523, §1º, do CPC)", resultado.multaArt523(), false);
            linhaDeTotal(tabela, "Honorários de 10% (art. 523, §1º, do CPC)", resultado.honorariosArt523(), false);
            if (resultado.ritos() != null) {
                linhaDeTotal(tabela, "Total da penhora", resultado.ritos().totalPenhora(), true);
            }
        }
        return tabela;
    }

    /** Começa outra página se sobrar menos que {@code altura} na atual. */
    private void reservar(Document documento, float altura) {
        if (writer.getVerticalPosition(true) - documento.bottom() < altura) {
            documento.newPage();
        }
    }

    private void linhaDeTotal(PdfPTable tabela, String rotulo, BigDecimal valor, boolean destaque) {
        Font fonte = destaque ? TABELA_NEGRITO : TABELA;
        PdfPCell celula = celula(rotulo, fonte, true);
        celula.setColspan(colunas.size() - 1);
        tabela.addCell(celula);
        tabela.addCell(celula(decimal(valor), fonte, true));
    }

    // ---- totais --------------------------------------------------------------------------

    private void totais(Document documento) {
        documento.add(secao("Resumo"));
        Map<String, BigDecimal> linhas = new LinkedHashMap<>();
        DemonstrativoResponse.Ritos ritos = resultado.ritos();
        if (ritos != null) {
            linhas.put("Rito da prisão: débito atualizado (" + contar(ritos.prisao().parcelas(), "parcela", "parcelas") + ")",
                    ritos.prisao().debitoAtualizado());
            linhas.put("Rito da penhora: débito atualizado (" + contar(ritos.penhora().parcelas(), "parcela", "parcelas") + ")",
                    ritos.penhora().debitoAtualizado());
        } else {
            linhas.put("Valor corrigido", resultado.totalCorrigido());
            linhas.put("Juros de mora", resultado.totalJuros());
            linhas.put("Débito atualizado", resultado.debitoAtualizado());
        }
        if (comAcrescimos) {
            linhas.put("Multa de 10% (art. 523, §1º, do CPC)", resultado.multaArt523());
            linhas.put("Honorários de 10% (art. 523, §1º, do CPC)", resultado.honorariosArt523());
            if (ritos != null) {
                linhas.put("Rito da penhora: total com os acréscimos", ritos.totalPenhora());
            }
        }

        PdfPTable tabela = new PdfPTable(new float[]{4, 1.4f});
        tabela.setWidthPercentage(55);
        tabela.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabela.setKeepTogether(true);
        linhas.forEach((rotulo, valor) -> {
            tabela.addCell(celula(rotulo, NORMAL, false));
            tabela.addCell(celula(moeda(valor), NORMAL, true));
        });
        PdfPCell rotulo = celula("Total da execução", NEGRITO, false);
        rotulo.setBackgroundColor(FUNDO_CABECALHO);
        tabela.addCell(rotulo);
        PdfPCell total = celula(moeda(resultado.total()), NEGRITO, true);
        total.setBackgroundColor(FUNDO_CABECALHO);
        tabela.addCell(total);
        tabela.setSpacingAfter(4);
        documento.add(tabela);
    }

    // ---- fontes --------------------------------------------------------------------------

    private void fontes(Document documento) {
        documento.add(secao("Fontes dos índices"));
        List<RegraDto> regras = regrasDoPeriodo();
        Set<Indice> indices = EnumSet.noneOf(Indice.class);
        for (RegraDto regra : regras) {
            indices.add(regra.correcao());
            if (regra.juros() == TipoJuros.TAXA_LEGAL || regra.juros() == TipoJuros.TRANSICAO_TAXA_LEGAL) {
                indices.add(Indice.TAXA_LEGAL);
            }
        }
        List<String> itens = new ArrayList<>();
        for (Indice indice : indices) {
            String valores = indice.corrigeMoeda() ? "variações mensais" : "taxas mensais";
            itens.add(indice.descricao() + ": " + valores + " da série " + SgsClient.codigo(indice) + " do SGS/BCB.");
        }
        if (indices.contains(Indice.INPC) && regimePadrao()) {
            itens.add("Até 07/2024, o regime padrão segue a tabela de fatores de atualização monetária da "
                    + "Corregedoria-Geral de Justiça do TJMG, de base INPC; o fator é composto das variações mensais "
                    + "do INPC.");
        }
        if (resultado.linhas().stream().anyMatch(l -> l.pensao() != null)) {
            itens.add("Salário mínimo nacional: valor vigente em cada mês, da série " + SgsClient.CODIGO_SALARIO_MINIMO
                    + " do SGS/BCB.");
        }
        for (String item : itens) {
            documento.add(new Paragraph("• " + item, NORMAL));
        }
        Paragraph consulta = new Paragraph("Séries consultadas no Sistema Gerenciador de Séries Temporais do Banco "
                + "Central do Brasil (api.bcb.gov.br) em " + emissao.format(DATA) + ".", NOTA);
        consulta.setSpacingBefore(4);
        documento.add(consulta);
    }

    /** Se o cálculo usou as regras do regime cível padrão do TJMG, com ou sem o nome dele. */
    private boolean regimePadrao() {
        return resultado.regime().regras().equals(RegimeDto.de(RegimeAtualizacao.civelPadrao()).regras());
    }

    // ---- rodapé --------------------------------------------------------------------------

    /** Identificação e "Página X de Y" em todas as páginas; o total só se sabe ao fechar. */
    private final class Rodape extends PdfPageEventHelper {

        private PdfTemplate totalDePaginas;
        private BaseFont fonte;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalDePaginas = writer.getDirectContent().createTemplate(40, 12);
            fonte = NOTA.getCalculatedBaseFont(false);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 22;
            String processo = pedido.processo() == null ? null : texto(pedido.processo().numero());
            String identificacao = (processo == null ? "" : "Processo " + processo + " · ")
                    + "Demonstrativo atualizado até " + data(resultado.dataBase())
                    + " · emitido em " + emissao.format(DATA) + " às " + emissao.format(HORA);
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(identificacao, NOTA),
                    document.left(), y, 0);

            String pagina = "Página " + writer.getPageNumber() + " de ";
            float tamanho = NOTA.getSize();
            float largura = fonte.getWidthPoint(pagina, tamanho);
            float x = document.right() - largura - 16;
            canvas.beginText();
            canvas.setFontAndSize(fonte, tamanho);
            canvas.setColorFill(SUAVE);
            canvas.setTextMatrix(x, y);
            canvas.showText(pagina);
            canvas.endText();
            canvas.addTemplate(totalDePaginas, x + largura, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalDePaginas.beginText();
            totalDePaginas.setFontAndSize(fonte, NOTA.getSize());
            totalDePaginas.setColorFill(SUAVE);
            totalDePaginas.setTextMatrix(0, 0);
            totalDePaginas.showText(String.valueOf(writer.getPageNumber() - 1));
            totalDePaginas.endText();
        }
    }

    // ---- blocos --------------------------------------------------------------------------

    private static Paragraph secao(String titulo) {
        Paragraph paragrafo = new Paragraph(titulo, SECAO);
        paragrafo.setSpacingBefore(12);
        paragrafo.setSpacingAfter(4);
        return paragrafo;
    }

    private static PdfPTable pares(Map<String, String> campos) {
        PdfPTable tabela = semBordas(2, new float[]{1.6f, 8.4f});
        campos.forEach((rotulo, valor) -> {
            tabela.addCell(celulaLivre(rotulo, ROTULO));
            tabela.addCell(celulaLivre(valor, NORMAL));
        });
        return tabela;
    }

    private static PdfPTable semBordas(int colunas, float[] larguras) {
        PdfPTable tabela = new PdfPTable(colunas);
        tabela.setWidths(larguras);
        tabela.setWidthPercentage(100);
        tabela.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        return tabela;
    }

    private static PdfPCell celulaLivre(String texto, Font fonte) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, fonte));
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPaddingTop(1.5f);
        celula.setPaddingBottom(2.5f);
        celula.setPaddingLeft(0);
        return celula;
    }

    private static PdfPCell celula(String texto, Font fonte, boolean direita) {
        PdfPCell celula = new PdfPCell(new Phrase(new Chunk(texto, fonte)));
        celula.setHorizontalAlignment(direita ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        celula.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celula.setBorderColor(LINHA);
        celula.setBorderWidth(0.5f);
        celula.setPadding(3);
        celula.setPaddingBottom(4);
        return celula;
    }

    // ---- formatos ------------------------------------------------------------------------

    private static DecimalFormat formato(String padrao) {
        DecimalFormat formato = new DecimalFormat(padrao, DecimalFormatSymbols.getInstance(PT_BR));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    }

    private static String decimal(BigDecimal valor) {
        return formato("#,##0.00").format(valor);
    }

    private static String moeda(BigDecimal valor) {
        return "R$ " + decimal(valor);
    }

    /** Sete casas, como na tela; o fator completo, sem arredondar, é o que o motor aplica. */
    private static String fator(BigDecimal valor) {
        return formato("0.0000000").format(valor);
    }

    /**
     * Seis casas, a precisão em que o BCB publica a taxa legal: com menos, quem confere os
     * juros a partir do percentual impresso pode errar o centavo.
     */
    private static String percentual(BigDecimal valor) {
        return formato("0.000000").format(valor) + "%";
    }

    private static String numeroLivre(BigDecimal valor) {
        return formato("#,##0.##########").format(valor);
    }

    private static String data(LocalDate data) {
        return data == null ? "—" : data.format(DATA);
    }

    private static String periodo(RegraDto regra) {
        if (regra.fim() == null) {
            return "desde " + regra.inicio().format(MES);
        }
        return regra.inicio().equals(regra.fim())
                ? regra.inicio().format(MES)
                : regra.inicio().format(MES) + " a " + regra.fim().format(MES);
    }

    private static String nomeIndice(Indice indice) {
        return switch (indice) {
            case IPCA_E -> "IPCA-E";
            default -> indice.name();
        };
    }

    private static String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    /** O texto sem espaços nas pontas, ou nulo se vazio. */
    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
