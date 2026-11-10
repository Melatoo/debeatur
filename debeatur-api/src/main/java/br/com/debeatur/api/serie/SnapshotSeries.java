package br.com.debeatur.api.serie;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SalarioMinimoIndisponivelException;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Valores das séries que um cálculo usou, guardados com ele para que o cálculo dê o mesmo
 * resultado mesmo depois de o BCB revisar uma série.
 * <p>
 * Como fonte, entrega só os valores do snapshot: um mês que não está nele é indisponível.
 *
 * @see #gravando(FonteSeries)
 */
public final class SnapshotSeries implements FonteSeries {

    /** Chave do salário mínimo; os índices usam o nome do {@link Indice}. */
    public static final String SALARIO_MINIMO = CacheSeries.SALARIO_MINIMO;

    private final Map<String, SortedMap<YearMonth, BigDecimal>> valores;
    /** Quando o cálculo foi salvo com estes valores; nulo num snapshot que ainda não foi gravado. */
    private final Instant guardadoEm;

    /** @param valores valor de cada mês, por série */
    public SnapshotSeries(Map<String, ? extends Map<YearMonth, BigDecimal>> valores) {
        this(valores, null);
    }

    /**
     * @param valores    valor de cada mês, por série
     * @param guardadoEm quando o cálculo foi salvo, que o demonstrativo diz ser a data dos valores
     */
    public SnapshotSeries(Map<String, ? extends Map<YearMonth, BigDecimal>> valores, Instant guardadoEm) {
        Map<String, SortedMap<YearMonth, BigDecimal>> copia = new HashMap<>();
        valores.forEach((serie, meses) -> copia.put(serie, Collections.unmodifiableSortedMap(new TreeMap<>(meses))));
        this.valores = Collections.unmodifiableMap(copia);
        this.guardadoEm = guardadoEm;
    }

    /** Os valores são todos do mesmo instante: o do salvamento. */
    @Override
    public Optional<Captura> captura(String serie) {
        return valores.containsKey(serie) && guardadoEm != null
                ? Optional.of(new Captura(guardadoEm, true))
                : Optional.empty();
    }

    /** Fonte que repassa as séries de {@code origem} e anota cada valor que entregou. */
    public static Gravacao gravando(FonteSeries origem) {
        return new Gravacao(origem);
    }

    /** Valor de cada mês, por série; só leitura. */
    public Map<String, SortedMap<YearMonth, BigDecimal>> valores() {
        return valores;
    }

    @Override
    public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        indices.forEach(indice -> serie.comTodos(indice, entre(indice.name(), de, ate)));
        return serie;
    }

    @Override
    public SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate) {
        return new SalarioMinimoEmMemoria().comTodos(entre(SALARIO_MINIMO, de, ate));
    }

    private Map<YearMonth, BigDecimal> entre(String serie, YearMonth de, YearMonth ate) {
        SortedMap<YearMonth, BigDecimal> meses = valores.get(serie);
        return meses == null ? Map.of() : meses.subMap(de, ate.plusMonths(1));
    }

    /**
     * Fonte que anota o que entregou, mês a mês. Só os meses do período pedido entram no
     * snapshot; o salário mínimo entra como o cálculo o usa, em reais com duas casas.
     */
    public static final class Gravacao implements FonteSeries {

        private final FonteSeries origem;
        private final Map<String, Map<YearMonth, BigDecimal>> anotados = new HashMap<>();

        private Gravacao(FonteSeries origem) {
            this.origem = origem;
        }

        @Override
        public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
            SerieIndicesEmMemoria serie = origem.carregar(indices, de, ate);
            for (Indice indice : indices) {
                for (YearMonth mes = de; !mes.isAfter(ate); mes = mes.plusMonths(1)) {
                    try {
                        anotar(indice.name(), mes, serie.variacaoMensal(indice, mes));
                    } catch (IndiceIndisponivelException e) {
                        // mês sem publicação: o motor só acusa se precisar dele
                    }
                }
            }
            return serie;
        }

        @Override
        public SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate) {
            SalarioMinimoEmMemoria salario = origem.carregarSalarioMinimo(de, ate);
            for (YearMonth mes = de; !mes.isAfter(ate); mes = mes.plusMonths(1)) {
                try {
                    anotar(SALARIO_MINIMO, mes, salario.vigente(mes).valor());
                } catch (SalarioMinimoIndisponivelException e) {
                    // idem: a geração das parcelas acusa o mês que faltar
                }
            }
            return salario;
        }

        @Override
        public Optional<Captura> captura(String serie) {
            return origem.captura(serie);
        }

        private void anotar(String serie, YearMonth mes, BigDecimal valor) {
            anotados.computeIfAbsent(serie, s -> new HashMap<>()).put(mes, valor);
        }

        /** Os valores entregues até aqui. */
        public SnapshotSeries snapshot() {
            return new SnapshotSeries(anotados);
        }
    }
}
