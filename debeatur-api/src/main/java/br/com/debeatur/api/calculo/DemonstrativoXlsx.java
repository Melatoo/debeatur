package br.com.debeatur.api.calculo;

import br.com.debeatur.api.calculo.ConteudoDemonstrativo.Coluna;
import br.com.debeatur.api.calculo.ConteudoDemonstrativo.Grupo;
import br.com.debeatur.api.calculo.ConteudoDemonstrativo.LinhaDeTotal;
import br.com.debeatur.api.calculo.DemonstrativoResponse.Linha;
import org.dhatim.fastexcel.PaperSize;
import org.dhatim.fastexcel.Position;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * O demonstrativo em planilha, com os mesmos dados do PDF: uma aba com o cabeçalho, o resumo,
 * os critérios e as fontes dos índices, e outra com a memória de cálculo.
 * <p>
 * Os valores entram como números e datas, para quem quiser conferir ou reaproveitar as contas,
 * mas os totais são os do motor, gravados como valor, e não fórmulas: a planilha não recalcula
 * nada, e uma soma do Excel em ponto flutuante não pode divergir do demonstrativo.
 */
final class DemonstrativoXlsx {

    static final String TIPO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String CINZA_CABECALHO = "E9ECEF";
    private static final String CINZA_TOTAL = "F4F5F7";
    private static final String CINZA_TEXTO = "5B6773";

    private static final String FORMATO_DATA = "dd/mm/yyyy";
    private static final String FORMATO_REAIS = "#,##0.00";
    private static final String FORMATO_MOEDA = "\"R$ \"#,##0.00";
    /**
     * Sete casas como no PDF. A célula guarda o fator com a precisão do Excel (cerca de 15
     * dígitos), mais do que aparece, mas menos que os 34 do motor: os valores vêm do motor.
     */
    private static final String FORMATO_FATOR = "0.0000000";
    private static final String FORMATO_PERCENTUAL = "0.0000##";

    /** Largura da coluna de valores da aba do demonstrativo, em caracteres. */
    private static final int LARGURA_VALOR = 100;
    private static final double ALTURA_DA_LINHA = 15;
    private static final double ALTURA_MAXIMA = 409;

    private final ConteudoDemonstrativo conteudo;
    private final List<Coluna> colunas;

    private DemonstrativoXlsx(ConteudoDemonstrativo conteudo) {
        this.conteudo = conteudo;
        this.colunas = conteudo.colunas();
    }

    /** Planilha de um cálculo não salvo, com as séries em uso na emissão. */
    static byte[] gerar(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao) {
        return new DemonstrativoXlsx(new ConteudoDemonstrativo(pedido, resultado, emissao, null)).gerar();
    }

    /** Planilha de um cálculo salvo, refeito com o snapshot das séries. */
    static byte[] gerarSalvo(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao,
                             LocalDateTime salvoEm) {
        return new DemonstrativoXlsx(new ConteudoDemonstrativo(pedido, resultado, emissao, Objects.requireNonNull(salvoEm)))
                .gerar();
    }

    /** Ex.: {@code demonstrativo-5001234-56.2024.8.13.0024-2026-10-31.xlsx}. */
    static String nomeDoArquivo(CalculoRequest pedido) {
        return ConteudoDemonstrativo.nomeDoArquivo(pedido, "xlsx");
    }

    private byte[] gerar() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (Workbook planilha = new Workbook(saida, "Debeatur", null)) {
            planilha.properties().setTitle("Demonstrativo do débito atualizado até "
                    + ConteudoDemonstrativo.data(conteudo.resultado().dataBase()));
            demonstrativo(planilha.newWorksheet("Demonstrativo"));
            memoria(planilha.newWorksheet("Memória de cálculo"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }

    // ---- aba do demonstrativo ------------------------------------------------------------

    private void demonstrativo(Worksheet aba) {
        impressao(aba);
        aba.width(0, 44);
        aba.width(1, LARGURA_VALOR);

        int r = 0;
        aba.value(r, 0, ConteudoDemonstrativo.TITULO);
        aba.style(r, 0).bold().fontSize(13).set();
        r++;
        aba.value(r, 0, conteudo.subtitulo());
        aba.style(r, 0).fontColor(CINZA_TEXTO).set();
        r += 2;
        r = pares(aba, r, conteudo.cabecalho());

        r = secao(aba, r + 1, "Resumo");
        for (Map.Entry<String, BigDecimal> linha : conteudo.resumo().entrySet()) {
            aba.value(r, 0, linha.getKey());
            moeda(aba, r, 1, linha.getValue());
            r++;
        }
        r = totalDaExecucao(aba, r, 0, 1);

        r = secao(aba, r + 1, "Critérios do cálculo");
        r = pares(aba, r, conteudo.criterios());
        for (String regra : conteudo.regras()) {
            r = texto(aba, r, 1, "• " + regra, LARGURA_VALOR);
        }

        r = secao(aba, r + 1, "Fontes dos índices");
        for (String fonte : conteudo.fontes()) {
            r = texto(aba, r, 1, "• " + fonte, LARGURA_VALOR);
        }
        texto(aba, r, 1, conteudo.consultaDasSeries(), LARGURA_VALOR);
    }

    private int pares(Worksheet aba, int r, Map<String, String> campos) {
        for (Map.Entry<String, String> campo : campos.entrySet()) {
            aba.value(r, 0, campo.getKey());
            aba.style(r, 0).fontColor(CINZA_TEXTO).verticalAlignment("top").set();
            r = texto(aba, r, 1, campo.getValue(), LARGURA_VALOR);
        }
        return r;
    }

    // ---- aba da memória ------------------------------------------------------------------

    private void memoria(Worksheet aba) {
        impressao(aba);
        for (int c = 0; c < colunas.size(); c++) {
            aba.width(c, largura(c));
        }
        int ultima = colunas.size() - 1;

        int r = secao(aba, 0, "Memória de cálculo");
        aba.value(r, 0, conteudo.explicacaoDasColunas());
        aba.range(r, 0, r, ultima).merge();
        aba.style(r, 0).wrapText(true).verticalAlignment("top").fontColor(CINZA_TEXTO).set();
        aba.rowHeight(r, altura(linhas(conteudo.explicacaoDasColunas(), larguraTotal())));
        r += 2;

        for (Grupo grupo : conteudo.grupos()) {
            if (grupo.titulo() != null) {
                aba.value(r, 0, grupo.titulo());
                aba.style(r, 0).bold().set();
                r++;
                aba.value(r, 0, grupo.regra());
                aba.style(r, 0).fontColor(CINZA_TEXTO).set();
                r++;
            }
            r = tabela(aba, r, grupo) + 1;
        }
        totalDaExecucao(aba, r, 0, ultima);
    }

    /** As parcelas do grupo, o subtotal e o fechamento; devolve a linha seguinte à última. */
    private int tabela(Worksheet aba, int r, Grupo grupo) {
        for (int c = 0; c < colunas.size(); c++) {
            Coluna coluna = colunas.get(c);
            aba.value(r, c, coluna.titulo());
            aba.style(r, c).bold().fillColor(CINZA_CABECALHO).wrapText(true)
                    .horizontalAlignment(coluna.numero() ? "right" : "left").set();
        }
        r++;
        for (Linha linha : grupo.linhas()) {
            for (int c = 0; c < colunas.size(); c++) {
                celula(aba, r, c, colunas.get(c), linha);
            }
            r++;
        }
        if (grupo.linhas().isEmpty()) {
            aba.value(r, 0, "Nenhuma parcela neste rito.");
            aba.style(r, 0).fontColor(CINZA_TEXTO).set();
            r++;
        }

        aba.value(r, 0, grupo.rotuloDoSubtotal());
        for (int c = 0; c < colunas.size(); c++) {
            Coluna coluna = colunas.get(c);
            if (coluna.total() != null) {
                aba.value(r, c, grupo.totais().de(coluna.total()));
            }
            aba.style(r, c).bold().fillColor(CINZA_TOTAL).format(FORMATO_REAIS).set();
        }
        r++;
        int ultima = colunas.size() - 1;
        for (LinhaDeTotal linha : grupo.fechamento()) {
            aba.value(r, 0, linha.rotulo());
            aba.value(r, ultima, linha.valor());
            if (linha.destaque()) {
                aba.style(r, 0).bold().set();
                aba.style(r, ultima).bold().format(FORMATO_REAIS).set();
            } else {
                aba.style(r, ultima).format(FORMATO_REAIS).set();
            }
            r++;
        }
        return r;
    }

    private static void celula(Worksheet aba, int r, int c, Coluna coluna, Linha linha) {
        Object dado = coluna.valor().apply(linha);
        switch (dado) {
            case null -> aba.value(r, c, "—");
            case LocalDate data -> aba.value(r, c, data);
            case BigDecimal numero -> aba.value(r, c, numero);
            default -> aba.value(r, c, dado.toString());
        }
        String formato = switch (coluna.formato()) {
            case DATA -> FORMATO_DATA;
            case REAIS -> FORMATO_REAIS;
            case FATOR -> FORMATO_FATOR;
            case PERCENTUAL -> FORMATO_PERCENTUAL;
            case TEXTO -> null;
        };
        // o traço e o "sem juros" ficam do lado dos números da coluna
        String alinhamento = coluna.numero() ? "right" : "left";
        if (formato == null) {
            aba.style(r, c).horizontalAlignment(alinhamento).set();
        } else {
            aba.style(r, c).format(formato).horizontalAlignment(alinhamento).set();
        }
    }

    /** Largura da coluna da memória, em caracteres: a da parcela é a mais larga. */
    private int largura(int c) {
        return c == 0 ? 22 : colunas.get(c).numero() ? 17 : 14;
    }

    private int larguraTotal() {
        int total = 0;
        for (int c = 0; c < colunas.size(); c++) {
            total += largura(c);
        }
        return total;
    }

    // ---- blocos --------------------------------------------------------------------------

    /**
     * Paisagem em A4, uma página de largura, e em todas as páginas a marca de segredo de justiça
     * no alto e a identificação e a página no pé, como no PDF.
     */
    private void impressao(Worksheet aba) {
        aba.pageOrientation("landscape");
        aba.paperSize(PaperSize.A4_PAPER);
        aba.fitToWidth((short) 1);
        aba.fitToHeight((short) 0);
        aba.header(ConteudoDemonstrativo.SEGREDO_DE_JUSTICA, Position.RIGHT, "Arial", 8);
        // "&" é código no cabeçalho do Excel; "&&" é o "&" literal
        aba.footer(conteudo.identificacao().replace("&", "&&"), Position.LEFT, "Arial", 8);
        aba.footer("Página &P de &N", Position.RIGHT, "Arial", 8);
    }

    private static int secao(Worksheet aba, int r, String titulo) {
        aba.value(r, 0, titulo);
        aba.style(r, 0).bold().fontSize(11).set();
        return r + 1;
    }

    /** Texto que quebra na largura da coluna, com a altura da linha para caber. */
    private static int texto(Worksheet aba, int r, int c, String texto, int largura) {
        aba.value(r, c, texto);
        aba.style(r, c).wrapText(true).verticalAlignment("top").set();
        int linhas = linhas(texto, largura);
        if (linhas > 1) {
            // o Excel não ajusta a altura ao abrir o arquivo; sem isso, só a primeira linha aparece
            aba.rowHeight(r, altura(linhas));
        }
        return r + 1;
    }

    /**
     * A altura para tantas linhas de texto, até 409 pontos, o máximo de uma linha no Excel: uma
     * lista longa de pagamentos fica com a célula inteira, só cortada na tela.
     */
    private static double altura(int linhas) {
        return Math.min(ALTURA_MAXIMA, ALTURA_DA_LINHA * linhas);
    }

    /** Quantas linhas o texto ocupa numa coluna dessa largura, por estimativa. */
    private static int linhas(String texto, int largura) {
        // uma folga de 10%: a largura da coluna é contada em dígitos, e o texto tem letras mais largas
        int porLinha = Math.max(1, largura * 9 / 10);
        return Math.max(1, (texto.length() + porLinha - 1) / porLinha);
    }

    private static void moeda(Worksheet aba, int r, int c, BigDecimal valor) {
        aba.value(r, c, valor);
        aba.style(r, c).format(FORMATO_MOEDA).horizontalAlignment("left").set();
    }

    private int totalDaExecucao(Worksheet aba, int r, int rotulo, int valor) {
        aba.value(r, rotulo, "Total da execução");
        aba.style(r, rotulo).bold().fillColor(CINZA_CABECALHO).set();
        aba.value(r, valor, conteudo.resultado().total());
        aba.style(r, valor).bold().fillColor(CINZA_CABECALHO).format(FORMATO_MOEDA)
                .horizontalAlignment(valor == 1 ? "left" : "right").set();
        return r + 1;
    }
}
