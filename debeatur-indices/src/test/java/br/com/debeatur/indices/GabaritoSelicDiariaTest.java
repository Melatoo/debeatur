package br.com.debeatur.indices;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Gabarito: planilha "SELIC Acumulada" da DPMG, gerada no iadefensor em 18/11/2026, e uma
 * simulação no CADEJ do TJMG (divergência 6 do docs/gabarito.md), contra uma resposta gravada da
 * SELIC diária (série 11 do SGS) de 01/2024 a 10/2026.
 * <p>
 * As tolerâncias são as da divergência 6: as taxas mensais da aba "TJMG" batem com o produto da
 * SGS 11 em seis casas (março/2024 vem com cinco), e os fatores ficam a menos de 3 × 10⁻⁷, a mesma
 * ordem da divergência 1.
 */
class GabaritoSelicDiariaTest {

    private static final BigDecimal TOLERANCIA_TAXA = new BigDecimal("0.000005");
    private static final BigDecimal TOLERANCIA_FATOR = new BigDecimal("0.0000005");

    private final Map<YearMonth, BigDecimal> selic =
            new SgsClient().acumularPorMes(ler("/sgs/selic-diaria-11-2024-01-a-2026-10.json"));

    @Test
    void taxasMensaisBatemComAAbaTjmgDaPlanilha() {
        Map<YearMonth, BigDecimal> taxas = lerCsv("/gabarito/selic-acumulada-dpmg-taxas-2026-11.csv");
        int meses = 0;
        for (Map.Entry<YearMonth, BigDecimal> taxa : taxas.entrySet()) {
            if (taxa.getKey().isBefore(YearMonth.of(2024, 1))) {
                continue;
            }
            assertThat(selic.get(taxa.getKey())).as(taxa.getKey().toString())
                    .isCloseTo(taxa.getValue(), within(TOLERANCIA_TAXA));
            meses++;
        }
        assertThat(meses).isEqualTo(17);
    }

    @Test
    void fatorDeCadaParcelaBateComAPlanilha() {
        Map<YearMonth, BigDecimal> fatores = lerCsv("/gabarito/selic-acumulada-dpmg-caso-2026-11.csv");
        RegimeAtualizacao soSelic = new RegimeAtualizacao("SELIC", List.of(
                new RegraPeriodo(YearMonth.of(2024, 1), null, Indice.SELIC, new Juros.SemJuros())));
        List<Parcela> parcelas = fatores.keySet().stream()
                .map(mes -> new Parcela("Pensão " + mes, Dinheiro.de("1000.00"), mes.atDay(1), null))
                .toList();

        Demonstrativo demonstrativo = new MotorCalculo(new SerieIndicesEmMemoria().comTodos(Indice.SELIC, selic))
                .calcular(new ParametrosCalculo(parcelas, LocalDate.of(2026, 11, 1), soSelic, false));

        assertThat(demonstrativo.linhas()).hasSize(12);
        for (LinhaDemonstrativo linha : demonstrativo.linhas()) {
            YearMonth mes = YearMonth.from(linha.parcela().inicioCorrecao());
            assertThat(linha.fatorCorrecao()).as(mes.toString()).isCloseTo(fatores.get(mes), within(TOLERANCIA_FATOR));
        }
    }

    /**
     * O CADEJ do TJMG (versão 1.9.12S, simulação de 11/11/2026, divergência 6): R$ 10.000,00 de
     * 15/01/2024 até 29/08/2024 pela SELIC, fator 1,0617101, mostrado com sete casas.
     */
    @Test
    void fatorBateComOCadej() {
        RegimeAtualizacao soSelic = new RegimeAtualizacao("SELIC", List.of(
                new RegraPeriodo(YearMonth.of(2024, 1), null, Indice.SELIC, new Juros.SemJuros())));
        Parcela parcela = new Parcela("Débito", Dinheiro.de("10000.00"), LocalDate.of(2024, 1, 15), null);

        Demonstrativo demonstrativo = new MotorCalculo(new SerieIndicesEmMemoria().comTodos(Indice.SELIC, selic))
                .calcular(new ParametrosCalculo(List.of(parcela), LocalDate.of(2024, 8, 29), soSelic, false));

        assertThat(demonstrativo.linhas().getFirst().fatorCorrecao())
                .isCloseTo(new BigDecimal("1.0617101"), within(new BigDecimal("0.00000005")));
    }

    /** Linhas "AAAA-MM,valor" ou "AAAA-MM,valor,fator": a última coluna é o valor conferido. */
    private static Map<YearMonth, BigDecimal> lerCsv(String recurso) {
        Map<YearMonth, BigDecimal> valores = new TreeMap<>();
        ler(recurso).lines()
                .filter(linha -> !linha.isBlank() && !linha.startsWith("#") && !linha.startsWith("mes"))
                .map(linha -> linha.split(","))
                .forEach(campos -> valores.put(YearMonth.parse(campos[0]), new BigDecimal(campos[campos.length - 1])));
        return valores;
    }

    private static String ler(String recurso) {
        try (InputStream in = GabaritoSelicDiariaTest.class.getResourceAsStream(recurso)) {
            assertThat(in).as(recurso).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(recurso, e);
        }
    }
}
