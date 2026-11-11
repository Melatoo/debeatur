package br.com.debeatur.api.calculo;

import br.com.debeatur.api.serie.FonteSeries;
import br.com.debeatur.api.serie.SnapshotSeries;
import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.alimentos.Rito;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.indices.SgsClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

/**
 * @param ritos  nulo se o cálculo não separa os ritos; se separa, multa, honorários e total
 *               são os do {@link DemonstrativoPorRito}, com o art. 523 só sobre a penhora
 * @param series os índices que o cálculo leu, mês a mês, para conferir o fator e os juros de
 *               cada linha; o salário mínimo já vem nas linhas da pensão
 */
public record DemonstrativoResponse(
        LocalDate dataBase,
        RegimeDto regime,
        List<Linha> linhas,
        BigDecimal totalCorrigido,
        BigDecimal totalJuros,
        BigDecimal debitoAtualizado,
        BigDecimal multaArt523,
        BigDecimal honorariosArt523,
        BigDecimal total,
        Ritos ritos,
        List<SerieUsada> series) {

    /** O mesmo demonstrativo, com as séries descritas de outra forma (de onde e de quando vieram). */
    DemonstrativoResponse comSeries(List<SerieUsada> outras) {
        return new DemonstrativoResponse(dataBase, regime, linhas, totalCorrigido, totalJuros, debitoAtualizado,
                multaArt523, honorariosArt523, total, ritos, outras);
    }

    /**
     * @param rito   nulo se o cálculo não separa os ritos
     * @param pensao de onde veio a parcela da pensão; nulo nas parcelas avulsas
     */
    public record Linha(
            String descricao,
            BigDecimal valorOriginal,
            LocalDate inicioCorrecao,
            LocalDate inicioJuros,
            BigDecimal fatorCorrecao,
            BigDecimal valorCorrigido,
            BigDecimal percentualJuros,
            BigDecimal valorJuros,
            BigDecimal subtotal,
            Rito rito,
            MesPensao pensao) {

        static Linha de(LinhaDemonstrativo l, Rito rito, MesPensao pensao) {
            return new Linha(l.parcela().descricao(), l.parcela().valor().valor(),
                    l.parcela().inicioCorrecao(), l.parcela().inicioJuros(),
                    l.fatorCorrecao(), l.valorCorrigido().valor(),
                    l.percentualJuros(), l.valorJuros().valor(), l.subtotal().valor(), rito, pensao);
        }
    }

    /**
     * Memória de uma parcela da pensão: o devido é o percentual do salário mínimo vigente no
     * mês, e o valor original da linha é o devido menos o pago.
     */
    public record MesPensao(YearMonth mes, BigDecimal salarioMinimo, BigDecimal valorDevido, BigDecimal valorPago) {
    }

    /** @param totalPenhora débito da penhora mais multa e honorários do art. 523 */
    public record Ritos(LocalDate ajuizamento, Subtotal prisao, Subtotal penhora, BigDecimal totalPenhora) {

        static Ritos de(DemonstrativoPorRito porRito) {
            return new Ritos(porRito.ajuizamento(), Subtotal.de(porRito.prisao()), Subtotal.de(porRito.penhora()),
                    porRito.totalPenhora().valor());
        }
    }

    /**
     * Um índice que o cálculo leu, com os valores que entregou ao motor.
     *
     * @param serie      o nome do {@link Indice}
     * @param fonte      de onde vêm os valores: "SGS/BCB, série 188"
     * @param capturadaEm de quando são os valores (a última consulta ao SGS ou o salvamento do
     *                   cálculo, conforme {@code snapshot}); nulo se a fonte não sabe
     * @param snapshot   se os valores são os guardados com um cálculo salvo
     * @param valores    variação de cada mês, em pontos percentuais, em ordem; só os meses que
     *                   saíram, entre o primeiro termo inicial e o mês anterior à data-base
     */
    public record SerieUsada(String serie, String descricao, String fonte, Instant capturadaEm, boolean snapshot,
                             List<ValorMensal> valores) {

        /** Os índices anotados pela gravação, na ordem do {@link Indice}. */
        static List<SerieUsada> de(SnapshotSeries usadas, FonteSeries fonte) {
            List<SerieUsada> series = new ArrayList<>();
            for (Indice indice : Indice.values()) {
                SortedMap<YearMonth, BigDecimal> meses = usadas.valores().get(indice.name());
                if (meses == null) {
                    continue;
                }
                Optional<FonteSeries.Captura> captura = fonte.captura(indice.name());
                series.add(new SerieUsada(indice.name(), indice.descricao(), "SGS/BCB, série " + SgsClient.codigo(indice),
                        captura.map(FonteSeries.Captura::em).orElse(null),
                        captura.map(FonteSeries.Captura::snapshot).orElse(false),
                        meses.entrySet().stream().map(e -> new ValorMensal(e.getKey(), e.getValue())).toList()));
            }
            return series;
        }
    }

    public record ValorMensal(YearMonth mes, BigDecimal valor) {
    }

    public record Subtotal(int parcelas, BigDecimal totalCorrigido, BigDecimal totalJuros, BigDecimal debitoAtualizado) {

        static Subtotal de(DemonstrativoPorRito.Subtotal s) {
            return new Subtotal(s.linhas().size(), s.totalCorrigido().valor(), s.totalJuros().valor(),
                    s.debitoAtualizado().valor());
        }
    }

    /**
     * @param memoria memória de cada parcela da pensão, indexada pela parcela que o motor
     *                repassa à linha; as parcelas avulsas não estão nela
     */
    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime, Map<Parcela, MesPensao> memoria,
                                    List<SerieUsada> series) {
        List<Linha> linhas = new ArrayList<>();
        for (LinhaDemonstrativo linha : d.linhas()) {
            linhas.add(Linha.de(linha, null, memoria.get(linha.parcela())));
        }
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime), linhas,
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                d.multaArt523().valor(), d.honorariosArt523().valor(), d.total().valor(), null, series);
    }

    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime, Map<Parcela, MesPensao> memoria,
                                    DemonstrativoPorRito porRito, List<SerieUsada> series) {
        List<Linha> linhas = new ArrayList<>();
        for (int i = 0; i < d.linhas().size(); i++) {
            LinhaDemonstrativo linha = d.linhas().get(i);
            linhas.add(Linha.de(linha, porRito.ritoDasLinhas().get(i), memoria.get(linha.parcela())));
        }
        Ritos ritos = Ritos.de(porRito);
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime), linhas,
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                porRito.multaArt523().valor(), porRito.honorariosArt523().valor(), porRito.total().valor(), ritos, series);
    }
}
