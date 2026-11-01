package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Séries do SGS/BCB fixadas em {@code src/test/resources/gabarito/series}, para que os testes
 * de gabarito não dependam do SGS ao vivo.
 * <p>
 * Cada arquivo tem uma linha {@code AAAA-MM;variação} por mês, sem lacunas.
 */
public final class SeriesPublicadas {

    private static final Indice[] FIXADAS = {Indice.INPC, Indice.IPCA, Indice.TAXA_LEGAL};

    private SeriesPublicadas() {
    }

    public static SerieIndicesEmMemoria carregar() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        for (Indice indice : FIXADAS) {
            serie.comTodos(indice, ler(indice));
        }
        return serie;
    }

    static Map<YearMonth, BigDecimal> ler(Indice indice) {
        String arquivo = "/gabarito/series/" + indice.name() + ".csv";
        Map<YearMonth, BigDecimal> valores = new LinkedHashMap<>();
        try (InputStream in = SeriesPublicadas.class.getResourceAsStream(arquivo)) {
            if (in == null) {
                throw new IllegalStateException("Série não encontrada: " + arquivo);
            }
            BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            YearMonth anterior = null;
            for (String linha = leitor.readLine(); linha != null; linha = leitor.readLine()) {
                if (linha.isBlank() || linha.startsWith("#")) {
                    continue;
                }
                String[] campos = linha.split(";");
                YearMonth mes = YearMonth.parse(campos[0]);
                if (anterior != null && !mes.equals(anterior.plusMonths(1))) {
                    throw new IllegalStateException(arquivo + ": lacuna entre " + anterior + " e " + mes);
                }
                valores.put(mes, new BigDecimal(campos[1]));
                anterior = mes;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return valores;
    }
}
