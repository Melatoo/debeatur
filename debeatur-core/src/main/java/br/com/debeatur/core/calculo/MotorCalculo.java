package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.SerieIndices;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Atualiza as parcelas de uma condenação e monta o demonstrativo.
 * <p>
 * Convenções provisórias (a validar contra cálculos homologados):
 * <ul>
 *   <li>Granularidade mensal: entram os meses do termo inicial (inclusive) até o mês da
 *       data-base (exclusive), sem pro rata die.</li>
 *   <li>Correção: produto de (1 + variação) dos meses, sem arredondar o fator.</li>
 *   <li>Juros: simples, somando os percentuais mensais, aplicados sobre o valor corrigido.</li>
 *   <li>Arredondamento: HALF_UP em duas casas a cada valor monetário.</li>
 * </ul>
 */
public final class MotorCalculo {

    private static final MathContext PRECISAO = MathContext.DECIMAL128;
    private static final BigDecimal DEZ_PORCENTO = BigDecimal.TEN;

    private final SerieIndices serie;

    public MotorCalculo(SerieIndices serie) {
        this.serie = serie;
    }

    public Demonstrativo calcular(ParametrosCalculo parametros) {
        RegimeAtualizacao regime = parametros.regime();
        YearMonth mesBase = YearMonth.from(parametros.dataBase());

        List<LinhaDemonstrativo> linhas = new ArrayList<>();
        Dinheiro totalCorrigido = Dinheiro.ZERO;
        Dinheiro totalJuros = Dinheiro.ZERO;

        for (Parcela parcela : parametros.parcelas()) {
            BigDecimal fator = fatorCorrecao(regime, parcela.inicioCorrecao(), mesBase);
            Dinheiro corrigido = parcela.valor().vezes(fator);
            BigDecimal percentualJuros = percentualJuros(regime, parcela.inicioJuros(), mesBase);
            Dinheiro juros = corrigido.percentual(percentualJuros);

            linhas.add(new LinhaDemonstrativo(parcela, fator, corrigido, percentualJuros, juros, corrigido.mais(juros)));
            totalCorrigido = totalCorrigido.mais(corrigido);
            totalJuros = totalJuros.mais(juros);
        }

        Dinheiro debito = totalCorrigido.mais(totalJuros);
        Dinheiro multa = parametros.aplicarArt523() ? debito.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;
        Dinheiro honorarios = parametros.aplicarArt523() ? debito.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;

        return new Demonstrativo(parametros.dataBase(), regime.nome(), linhas,
                totalCorrigido, totalJuros, debito, multa, honorarios, debito.mais(multa).mais(honorarios));
    }

    private BigDecimal fatorCorrecao(RegimeAtualizacao regime, LocalDate inicio, YearMonth mesBase) {
        BigDecimal fator = BigDecimal.ONE;
        for (YearMonth mes = YearMonth.from(inicio); mes.isBefore(mesBase); mes = mes.plusMonths(1)) {
            RegraPeriodo regra = regime.regraPara(mes);
            BigDecimal variacao = serie.variacaoMensal(regra.correcao(), mes);
            fator = fator.multiply(BigDecimal.ONE.add(variacao.movePointLeft(2)), PRECISAO);
        }
        return fator;
    }

    private BigDecimal percentualJuros(RegimeAtualizacao regime, LocalDate inicio, YearMonth mesBase) {
        if (inicio == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal percentual = BigDecimal.ZERO;
        for (YearMonth mes = YearMonth.from(inicio); mes.isBefore(mesBase); mes = mes.plusMonths(1)) {
            percentual = percentual.add(regime.regraPara(mes).juros().percentualNoMes(mes, serie));
        }
        return percentual;
    }
}
