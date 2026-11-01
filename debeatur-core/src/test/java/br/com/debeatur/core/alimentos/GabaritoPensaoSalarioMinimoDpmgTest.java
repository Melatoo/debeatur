package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.gabarito.SeriesPublicadas;
import br.com.debeatur.core.indice.SalarioMinimo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: aba "EVOLUÇÃO SALÁRIO MÍNIMO" da planilha modelo de alimentos da DPMG
 * ({@code PLANILHA_MODELO_OUTUBRO_2026-TAXA-LEGAL.xls}), com o valor da pensão de 10% a 95% do
 * salário mínimo em cada vigência de 04/2001 a 01/2026 (29 vigências, 18 percentuais).
 * <p>
 * O salário mínimo vem da série 1619 do SGS, fixada em {@link SeriesPublicadas}, e não da
 * própria planilha.
 */
class GabaritoPensaoSalarioMinimoDpmgTest {

    private static final SalarioMinimo SALARIO_MINIMO = SeriesPublicadas.salarioMinimo();

    @ParameterizedTest(name = "{0}: {1}% do SM = {2}")
    @CsvFileSource(resources = "/gabarito/pensao-salario-minimo-dpmg-2026-10.csv", numLinesToSkip = 1)
    void valorDaParcelaReproduzAPlanilha(YearMonth mes, BigDecimal percentual, String valor) {
        PensaoEmSalarioMinimo pensao = new PensaoEmSalarioMinimo(percentual, 1, mes, mes,
                InicioJuros.VENCIMENTO, null);

        List<Parcela> parcelas = pensao.parcelas(SALARIO_MINIMO);

        assertThat(parcelas).singleElement()
                .extracting(Parcela::valor)
                .isEqualTo(Dinheiro.de(valor));
    }
}
