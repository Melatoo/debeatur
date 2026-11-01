package br.com.debeatur.indices;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.SalarioMinimo;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: tabela de vigências do salário mínimo da planilha modelo de alimentos da DPMG
 * (data-base 10/2026), conferida mês a mês contra uma resposta gravada da série 1619 do SGS,
 * de 04/2001 a 10/2026.
 */
class GabaritoSalarioMinimoDpmgTest {

    private static final YearMonth PRIMEIRO_MES = YearMonth.of(2001, 4);
    private static final YearMonth ULTIMO_MES = YearMonth.of(2026, 10);

    @Test
    void serieDoSgsCoincideComAsVigenciasDaPlanilha() throws IOException {
        NavigableMap<YearMonth, Dinheiro> vigencias = lerVigencias();
        SalarioMinimo salario = new SalarioMinimoEmMemoria()
                .comTodos(new SgsClient().interpretar(ler("/sgs/salario-minimo-1619.json")));

        int meses = 0;
        for (YearMonth mes = PRIMEIRO_MES; !mes.isAfter(ULTIMO_MES); mes = mes.plusMonths(1)) {
            assertThat(salario.vigente(mes)).as(mes.toString()).isEqualTo(vigencias.floorEntry(mes).getValue());
            meses++;
        }
        assertThat(meses).isEqualTo(307);
    }

    private static NavigableMap<YearMonth, Dinheiro> lerVigencias() throws IOException {
        NavigableMap<YearMonth, Dinheiro> vigencias = new TreeMap<>();
        String csv = ler("/gabarito/salario-minimo-dpmg-2026-10.csv");
        csv.lines()
                .filter(linha -> !linha.isBlank() && !linha.startsWith("#") && !linha.startsWith("inicio"))
                .map(linha -> linha.split(","))
                .forEach(campos -> vigencias.put(YearMonth.parse(campos[0]), Dinheiro.de(campos[1])));
        assertThat(vigencias.firstKey()).isEqualTo(PRIMEIRO_MES);
        return vigencias;
    }

    private static String ler(String recurso) throws IOException {
        try (InputStream in = GabaritoSalarioMinimoDpmgTest.class.getResourceAsStream(recurso)) {
            assertThat(in).as(recurso).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
