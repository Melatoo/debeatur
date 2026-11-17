package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link MotorCalculo} conferidas em casos sorteados.
 * <p>
 * Não substituem o gabarito: nenhum valor esperado aqui é um valor de cálculo. São invariantes
 * que valem para qualquer entrada (os totais fecham com as linhas, cada linha só depende da
 * própria parcela) e a equivalência com uma conta ingênua e exata. A semente é fixa, então uma
 * falha se reproduz; a mensagem traz o número do caso.
 */
class MotorCalculoPropriedadesTest {

    private static final int CASOS = 300;
    private static final long SEMENTE = 524L;

    private static final YearMonth INICIO_SERIES = YearMonth.of(2000, 1);
    private static final YearMonth FIM_SERIES = YearMonth.of(2026, 12);
    private static final YearMonth VIRADA_IPCA = YearMonth.of(2016, 1);
    private static final BigDecimal JUROS_ANTES = new BigDecimal("1");
    private static final BigDecimal JUROS_DEPOIS = new BigDecimal("0.5");

    /** Duas regras, para que a troca de índice e de juros no meio do período também entre nos casos. */
    private static final RegimeAtualizacao REGIME = new RegimeAtualizacao("INPC + 1% / IPCA + 0,5%", List.of(
            new RegraPeriodo(INICIO_SERIES, VIRADA_IPCA.minusMonths(1), Indice.INPC, new Juros.TaxaFixa(JUROS_ANTES)),
            new RegraPeriodo(VIRADA_IPCA, null, Indice.IPCA, new Juros.TaxaFixa(JUROS_DEPOIS))));

    private final SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
    private final MotorCalculo motor = new MotorCalculo(serie);

    MotorCalculoPropriedadesTest() {
        SplittableRandom aleatorio = new SplittableRandom(SEMENTE);
        for (YearMonth mes = INICIO_SERIES; !mes.isAfter(FIM_SERIES); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, variacao(aleatorio));
            serie.com(Indice.IPCA, mes, variacao(aleatorio));
        }
    }

    @Test
    void totaisFechamComAsLinhas() {
        paraCadaCaso((n, parametros) -> {
            Demonstrativo d = motor.calcular(parametros);

            Dinheiro somaCorrigido = Dinheiro.ZERO;
            Dinheiro somaJuros = Dinheiro.ZERO;
            for (LinhaDemonstrativo linha : d.linhas()) {
                assertThat(linha.subtotal()).as("caso %d: subtotal da linha", n)
                        .isEqualTo(linha.valorCorrigido().mais(linha.valorJuros()));
                somaCorrigido = somaCorrigido.mais(linha.valorCorrigido());
                somaJuros = somaJuros.mais(linha.valorJuros());
            }
            assertThat(d.totalCorrigido()).as("caso %d: total corrigido", n).isEqualTo(somaCorrigido);
            assertThat(d.totalJuros()).as("caso %d: total de juros", n).isEqualTo(somaJuros);
            assertThat(d.debitoAtualizado()).as("caso %d: débito", n).isEqualTo(somaCorrigido.mais(somaJuros));

            Dinheiro dezPorCento = parametros.aplicarArt523()
                    ? new Dinheiro(d.debitoAtualizado().valor().multiply(new BigDecimal("0.10")))
                    : Dinheiro.ZERO;
            assertThat(d.multaArt523()).as("caso %d: multa", n).isEqualTo(dezPorCento);
            assertThat(d.honorariosArt523()).as("caso %d: honorários", n).isEqualTo(dezPorCento);
            assertThat(d.total()).as("caso %d: total", n)
                    .isEqualTo(d.debitoAtualizado().mais(d.multaArt523()).mais(d.honorariosArt523()));
        });
    }

    @Test
    void fatorEJurosBatemComAContaIngenuaEExata() {
        paraCadaCaso((n, parametros) -> {
            Demonstrativo d = motor.calcular(parametros);
            YearMonth mesBase = YearMonth.from(parametros.dataBase());

            for (LinhaDemonstrativo linha : d.linhas()) {
                Parcela parcela = linha.parcela();

                // produto sem MathContext: exato, sem nenhum arredondamento
                BigDecimal fatorExato = BigDecimal.ONE;
                for (YearMonth mes = YearMonth.from(parcela.inicioCorrecao()); mes.isBefore(mesBase); mes = mes.plusMonths(1)) {
                    Indice indice = mes.isBefore(VIRADA_IPCA) ? Indice.INPC : Indice.IPCA;
                    fatorExato = fatorExato.multiply(BigDecimal.ONE.add(serie.variacaoMensal(indice, mes).movePointLeft(2)));
                }
                // DECIMAL128 arredonda cada produto a 34 dígitos; em até 324 meses o erro relativo
                // acumulado fica abaixo de 1E-31
                BigDecimal erroRelativo = linha.fatorCorrecao().subtract(fatorExato).abs()
                        .divide(fatorExato, MathContext.DECIMAL64);
                assertThat(erroRelativo).as("caso %d: fator", n).isLessThan(new BigDecimal("1E-30"));

                BigDecimal jurosEsperados = BigDecimal.ZERO;
                if (parcela.inicioJuros() != null) {
                    for (YearMonth mes = YearMonth.from(parcela.inicioJuros()); mes.isBefore(mesBase); mes = mes.plusMonths(1)) {
                        jurosEsperados = jurosEsperados.add(mes.isBefore(VIRADA_IPCA) ? JUROS_ANTES : JUROS_DEPOIS);
                    }
                }
                assertThat(linha.percentualJuros()).as("caso %d: percentual de juros", n)
                        .isEqualByComparingTo(jurosEsperados);
                assertThat(linha.valorCorrigido()).as("caso %d: valor corrigido", n)
                        .isEqualTo(parcela.valor().vezes(linha.fatorCorrecao()));
            }
        });
    }

    @Test
    void cadaLinhaSoDependeDaPropriaParcela() {
        paraCadaCaso((n, parametros) -> {
            Demonstrativo juntas = motor.calcular(parametros);

            for (int i = 0; i < parametros.parcelas().size(); i++) {
                Demonstrativo sozinha = motor.calcular(new ParametrosCalculo(
                        List.of(parametros.parcelas().get(i)), parametros.dataBase(), parametros.regime(),
                        parametros.aplicarArt523()));
                assertThat(sozinha.linhas().getFirst()).as("caso %d, parcela %d", n, i)
                        .isEqualTo(juntas.linhas().get(i));
            }

            List<Parcela> invertidas = new ArrayList<>(parametros.parcelas()).reversed();
            Demonstrativo naOrdemInversa = motor.calcular(new ParametrosCalculo(
                    invertidas, parametros.dataBase(), parametros.regime(), parametros.aplicarArt523()));
            assertThat(naOrdemInversa.total()).as("caso %d: total independe da ordem", n).isEqualTo(juntas.total());
        });
    }

    @Test
    void recalcularDaOMesmoDemonstrativo() {
        paraCadaCaso((n, parametros) ->
                assertThat(motor.calcular(parametros)).as("caso %d", n).isEqualTo(motor.calcular(parametros)));
    }

    private interface Propriedade {
        void conferir(int caso, ParametrosCalculo parametros);
    }

    private void paraCadaCaso(Propriedade propriedade) {
        SplittableRandom aleatorio = new SplittableRandom(SEMENTE + 1);
        for (int n = 0; n < CASOS; n++) {
            propriedade.conferir(n, sortear(aleatorio));
        }
    }

    private static ParametrosCalculo sortear(SplittableRandom aleatorio) {
        LocalDate dataBase = dia(aleatorio, LocalDate.of(2001, 2, 1), FIM_SERIES.plusMonths(1).atDay(1));
        int quantidade = aleatorio.nextInt(1, 7);
        List<Parcela> parcelas = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            LocalDate inicioCorrecao = dia(aleatorio, INICIO_SERIES.atDay(1), dataBase);
            LocalDate inicioJuros = aleatorio.nextInt(10) < 3 ? null : dia(aleatorio, inicioCorrecao, dataBase);
            Dinheiro valor = new Dinheiro(BigDecimal.valueOf(aleatorio.nextLong(1, 10_000_000_00L), 2));
            parcelas.add(new Parcela("Parcela " + (i + 1), valor, inicioCorrecao, inicioJuros));
        }
        return new ParametrosCalculo(parcelas, dataBase, REGIME, aleatorio.nextBoolean());
    }

    /** Dia sorteado entre {@code de} e {@code ate}, inclusive. */
    private static LocalDate dia(SplittableRandom aleatorio, LocalDate de, LocalDate ate) {
        return de.plusDays(aleatorio.nextLong(0, ate.toEpochDay() - de.toEpochDay() + 1));
    }

    /** Variação mensal em pontos, com duas casas como o IBGE publica, entre −1,00 e 2,00. */
    private static BigDecimal variacao(SplittableRandom aleatorio) {
        return BigDecimal.valueOf(aleatorio.nextInt(-100, 201), 2);
    }
}
