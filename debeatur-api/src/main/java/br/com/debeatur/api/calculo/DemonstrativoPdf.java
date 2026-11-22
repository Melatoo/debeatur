package br.com.debeatur.api.calculo;

import br.com.debeatur.api.calculo.ConteudoDemonstrativo.Coluna;
import br.com.debeatur.api.calculo.ConteudoDemonstrativo.Grupo;
import br.com.debeatur.api.calculo.ConteudoDemonstrativo.LinhaDeTotal;
import br.com.debeatur.api.calculo.DemonstrativoResponse.Linha;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static br.com.debeatur.api.calculo.ConteudoDemonstrativo.data;
import static br.com.debeatur.api.calculo.ConteudoDemonstrativo.decimal;
import static br.com.debeatur.api.calculo.ConteudoDemonstrativo.moeda;

/**
 * Demonstrativo em PDF para juntar aos autos: identificação do processo, critérios do
 * cálculo, memória linha a linha, totais com o art. 523, §1º, e as fontes dos índices.
 * <p>
 * O conteúdo sai de {@link ConteudoDemonstrativo}, o mesmo da planilha; aqui fica só a forma.
 */
final class DemonstrativoPdf {

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
    /** Entrelinha das notas em lista: a padrão do OpenPDF, 1,5 vez o corpo, abre espaço demais. */
    private static final float ENTRELINHA_NOTA = 9.5f;
    private static final Font MARCA =new Font(Font.HELVETICA, 8.5f, Font.BOLD, TEXTO);

    private final ConteudoDemonstrativo conteudo;
    private final DemonstrativoResponse resultado;
    private final List<Coluna> colunas;
    private PdfWriter writer;

    private DemonstrativoPdf(ConteudoDemonstrativo conteudo) {
        this.conteudo = conteudo;
        this.resultado = conteudo.resultado();
        this.colunas = conteudo.colunas();
    }

    /**
     * Demonstrativo de um cálculo não salvo, com as séries em uso na emissão.
     *
     * @param emissao data e hora impressas no cabeçalho e no rodapé
     */
    static byte[] gerar(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao) {
        return new DemonstrativoPdf(new ConteudoDemonstrativo(pedido, resultado, emissao, null)).gerar();
    }

    /**
     * Demonstrativo de um cálculo salvo, refeito com o snapshot das séries.
     *
     * @param emissao data e hora impressas no cabeçalho e no rodapé
     * @param salvoEm quando o cálculo foi salvo, que é a data dos valores do snapshot
     */
    static byte[] gerarSalvo(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao,
                             LocalDateTime salvoEm) {
        return new DemonstrativoPdf(new ConteudoDemonstrativo(pedido, resultado, emissao, Objects.requireNonNull(salvoEm)))
                .gerar();
    }

    /** Ex.: {@code demonstrativo-5001234-56.2024.8.13.0024-2026-10-31.pdf}. */
    static String nomeDoArquivo(CalculoRequest pedido) {
        return ConteudoDemonstrativo.nomeDoArquivo(pedido, "pdf");
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
        // o total na primeira página: quem abre o PDF nos autos acha o valor sem passar pela memória
        resumo(documento);
        criterios(documento);
        memoria(documento);
        encerramento(documento);

        documento.close();
        return saida.toByteArray();
    }

    // ---- cabeçalho -----------------------------------------------------------------------

    private void cabecalho(Document documento) {
        documento.add(new Paragraph(ConteudoDemonstrativo.TITULO, TITULO));
        Paragraph subtitulo = new Paragraph(conteudo.subtitulo(), ROTULO);
        subtitulo.setSpacingAfter(10);
        documento.add(subtitulo);
        documento.add(pares(conteudo.cabecalho()));
    }

    // ---- critérios -----------------------------------------------------------------------

    private void criterios(Document documento) {
        documento.add(secao("Critérios do cálculo"));
        documento.add(pares(conteudo.criterios()));
        PdfPTable tabela = semBordas(2, new float[]{1.6f, 8.4f});
        for (String regra : conteudo.regras()) {
            tabela.addCell(celulaLivre("", ROTULO));
            tabela.addCell(celulaLivre("• " + regra, NORMAL));
        }
        documento.add(tabela);
    }

    // ---- memória de cálculo --------------------------------------------------------------

    private void memoria(Document documento) {
        documento.add(secao("Memória de cálculo"));
        Paragraph explicacao = new Paragraph(conteudo.explicacaoDasColunas(), NOTA);
        explicacao.setSpacingAfter(6);
        documento.add(explicacao);

        for (Grupo grupo : conteudo.grupos()) {
            if (grupo.titulo() != null) {
                // o título do rito não fica no pé da página, longe da tabela
                reservar(documento, 90);
                Paragraph titulo = new Paragraph(grupo.titulo(), NEGRITO);
                titulo.setSpacingBefore(6);
                documento.add(titulo);
                Paragraph regra = new Paragraph(grupo.regra(), NOTA);
                regra.setSpacingAfter(4);
                documento.add(regra);
            }
            tabela(documento, grupo);
        }
    }

    /**
     * A memória de um grupo de linhas: as parcelas e o fechamento (subtotal e art. 523) numa só
     * tabela, que se divide entre páginas com o cabeçalho das colunas repetido. Assim, um total
     * que caia no alto da página seguinte nunca fica sem rótulo.
     * <p>
     * O fechamento não vai numa tabela à parte, que não se dividisse: medir se ela cabe no resto
     * da página não bate com a regra do OpenPDF, e ela chegava a abrir a página seguinte sem o
     * cabeçalho.
     */
    private void tabela(Document documento, Grupo grupo) {
        PdfPTable parcelas = novaTabela();
        parcelas.setHeaderRows(1);
        parcelas.setSpacingAfter(8);
        cabecalhoDasColunas(parcelas);
        for (Linha linha : grupo.linhas()) {
            for (Coluna coluna : colunas) {
                parcelas.addCell(celula(coluna.texto(linha), TABELA, coluna.numero()));
            }
        }
        if (grupo.linhas().isEmpty()) {
            PdfPCell vazia = celula("Nenhuma parcela neste rito.", NOTA, false);
            vazia.setColspan(colunas.size());
            parcelas.addCell(vazia);
        }
        fechamento(parcelas, grupo);
        documento.add(parcelas);
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

    /** Acrescenta à tabela o subtotal e o fechamento do grupo. */
    private void fechamento(PdfPTable tabela, Grupo grupo) {
        int primeiraComTotal = conteudo.primeiraColunaComTotal();
        PdfPCell rotulo = celula(grupo.rotuloDoSubtotal(), TABELA_NEGRITO, false);
        rotulo.setColspan(primeiraComTotal);
        rotulo.setBackgroundColor(FUNDO_TOTAL);
        tabela.addCell(rotulo);
        for (Coluna coluna : colunas.subList(primeiraComTotal, colunas.size())) {
            PdfPCell celula = celula(coluna.total() == null ? "" : decimal(grupo.totais().de(coluna.total())),
                    TABELA_NEGRITO, true);
            celula.setBackgroundColor(FUNDO_TOTAL);
            tabela.addCell(celula);
        }
        for (LinhaDeTotal linha : grupo.fechamento()) {
            linhaDeTotal(tabela, linha);
        }
    }

    /** Começa outra página se sobrar menos que {@code altura} na atual. */
    private void reservar(Document documento, float altura) {
        if (writer.getVerticalPosition(true) - documento.bottom() < altura) {
            documento.newPage();
        }
    }

    private void linhaDeTotal(PdfPTable tabela, LinhaDeTotal linha) {
        Font fonte = linha.destaque() ? TABELA_NEGRITO : TABELA;
        PdfPCell celula = celula(linha.rotulo(), fonte, true);
        celula.setColspan(colunas.size() - 1);
        tabela.addCell(celula);
        tabela.addCell(celula(decimal(linha.valor()), fonte, true));
    }

    // ---- totais --------------------------------------------------------------------------

    /** O quadro-resumo da primeira página: o débito de cada rito, os acréscimos e o total. */
    private void resumo(Document documento) {
        documento.add(secao("Resumo"));
        PdfPTable tabela = quadroDeTotais();
        conteudo.resumo().forEach((rotulo, valor) -> {
            tabela.addCell(celula(rotulo, NORMAL, false));
            tabela.addCell(celula(moeda(valor), NORMAL, true));
        });
        linhaDoTotalDaExecucao(tabela);
        tabela.setSpacingAfter(4);
        documento.add(tabela);
    }

    /**
     * O total da execução, que fecha a memória de onde ele sai (a composição está no resumo), e as
     * fontes dos índices, num bloco que não se divide: se não couber no fim da memória, a página
     * seguinte leva os dois, e nunca só a procedência das séries.
     */
    private void encerramento(Document documento) {
        PdfPTable total = quadroDeTotais();
        linhaDoTotalDaExecucao(total);
        PdfPCell celula = new PdfPCell();
        celula.setBorder(Rectangle.NO_BORDER);
        celula.setPadding(0);
        celula.addElement(total);
        fontes().forEach(celula::addElement);
        PdfPTable bloco = new PdfPTable(1);
        bloco.setWidthPercentage(100);
        bloco.setKeepTogether(true);
        // sem espaço antes: a tabela da memória já deixa o dela
        bloco.addCell(celula);
        documento.add(bloco);
    }

    private static PdfPTable quadroDeTotais() {
        PdfPTable tabela = new PdfPTable(new float[]{4, 1.4f});
        tabela.setWidthPercentage(55);
        tabela.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabela.setKeepTogether(true);
        return tabela;
    }

    private void linhaDoTotalDaExecucao(PdfPTable tabela) {
        PdfPCell rotulo = celula("Total da execução", NEGRITO, false);
        rotulo.setBackgroundColor(FUNDO_CABECALHO);
        tabela.addCell(rotulo);
        PdfPCell total = celula(moeda(resultado.total()), NEGRITO, true);
        total.setBackgroundColor(FUNDO_CABECALHO);
        tabela.addCell(total);
    }

    // ---- fontes --------------------------------------------------------------------------

    /** A procedência das séries: o título, um item por série e de quando são os valores. */
    private List<Element> fontes() {
        List<Element> elementos = new ArrayList<>();
        Paragraph titulo = secao("Fontes dos índices");
        // logo abaixo do total, que já separa a procedência da memória
        titulo.setSpacingBefore(6);
        elementos.add(titulo);
        // em corpo de nota: a procedência é consulta de quem confere, e cabe no fim da memória
        // em vez de abrir uma página só para ela
        for (String item : conteudo.fontes()) {
            elementos.add(new Paragraph(ENTRELINHA_NOTA, "• " + item, NOTA));
        }
        Paragraph consulta = new Paragraph(ENTRELINHA_NOTA, conteudo.consultaDasSeries(), NOTA);
        consulta.setSpacingBefore(4);
        elementos.add(consulta);
        return elementos;
    }

    // ---- rodapé --------------------------------------------------------------------------

    /**
     * Em todas as páginas: a marca de segredo de justiça no alto, e a identificação e "Página X
     * de Y" no pé; o total de páginas só se sabe ao fechar.
     */
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
            // ações de alimentos correm em segredo de justiça (art. 189, II, do CPC): uma folha
            // solta do demonstrativo também o diz
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                    new Phrase(ConteudoDemonstrativo.SEGREDO_DE_JUSTICA, MARCA), document.right(), document.top() + 14, 0);

            float y = document.bottom() - 22;
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(conteudo.identificacao(), NOTA),
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
        // respiro curto: com 3 e 4, as fontes do caso de 18 meses com ritos abriam uma página sozinhas
        celula.setPadding(2);
        celula.setPaddingBottom(3);
        return celula;
    }
}
