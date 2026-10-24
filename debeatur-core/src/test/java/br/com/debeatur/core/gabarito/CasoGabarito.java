package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.indice.SerieIndices;
import br.com.debeatur.core.regime.RegimeAtualizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Um cálculo homologado ou feito pela contadoria do TJMG, transcrito para o formato do motor.
 * <p>
 * Os valores esperados são os do documento de origem, nunca os do motor. Campos que o
 * documento não mostra ficam {@code null} e não são comparados.
 *
 * @param fonte       onde está o cálculo (processo, órgão, ID ou folhas)
 * @param tolerancia  diferença aceita nos valores monetários; acima de zero exige justificativa
 */
public record CasoGabarito(
        String nome,
        String fonte,
        ParametrosCalculo parametros,
        SerieIndices serie,
        List<LinhaEsperada> linhas,
        Totais totais,
        Dinheiro tolerancia,
        String justificativa) {

    public CasoGabarito {
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(parametros, "parametros");
        Objects.requireNonNull(serie, "serie");
        Objects.requireNonNull(totais, "totais");
        Objects.requireNonNull(tolerancia, "tolerancia");
        linhas = List.copyOf(linhas);
        if (linhas.size() != parametros.parcelas().size()) {
            throw new IllegalArgumentException(nome + ": informe uma linha esperada por parcela");
        }
        if (tolerancia.compareTo(Dinheiro.ZERO) > 0 && (justificativa == null || justificativa.isBlank())) {
            throw new IllegalArgumentException(nome + ": tolerância acima de zero exige justificativa");
        }
    }

    /**
     * Valores de uma parcela no documento de origem. Fator e percentual são comparados na
     * precisão em que foram impressos.
     */
    public record LinhaEsperada(BigDecimal fatorCorrecao, Dinheiro valorCorrigido,
                                BigDecimal percentualJuros, Dinheiro valorJuros, Dinheiro subtotal) {
    }

    public record Totais(Dinheiro debitoAtualizado, Dinheiro multaArt523,
                         Dinheiro honorariosArt523, Dinheiro total) {
    }

    public static Construtor de(String nome, String fonte) {
        return new Construtor(nome, fonte);
    }

    public static final class Construtor {

        private final String nome;
        private final String fonte;
        private final List<Parcela> parcelas = new ArrayList<>();
        private final List<LinhaEsperada> linhas = new ArrayList<>();
        private LocalDate dataBase;
        private RegimeAtualizacao regime;
        private boolean aplicarArt523;
        private SerieIndices serie;
        private Totais totais;
        private Dinheiro tolerancia = Dinheiro.ZERO;
        private String justificativa;

        private Construtor(String nome, String fonte) {
            this.nome = nome;
            this.fonte = fonte;
        }

        public Construtor dataBase(String data) {
            this.dataBase = LocalDate.parse(data);
            return this;
        }

        public Construtor regime(RegimeAtualizacao regime) {
            this.regime = regime;
            return this;
        }

        public Construtor comArt523() {
            this.aplicarArt523 = true;
            return this;
        }

        /** Série própria do caso; por padrão, as {@link SeriesPublicadas}. */
        public Construtor serie(SerieIndices serie) {
            this.serie = serie;
            return this;
        }

        public Construtor parcela(Parcela parcela, LinhaEsperada esperada) {
            parcelas.add(parcela);
            linhas.add(esperada);
            return this;
        }

        public Construtor totais(String debitoAtualizado, String multa, String honorarios, String total) {
            this.totais = new Totais(dinheiro(debitoAtualizado), dinheiro(multa), dinheiro(honorarios), dinheiro(total));
            return this;
        }

        public Construtor tolerancia(String valor, String justificativa) {
            this.tolerancia = Dinheiro.de(valor);
            this.justificativa = justificativa;
            return this;
        }

        public CasoGabarito criar() {
            return new CasoGabarito(nome, fonte,
                    new ParametrosCalculo(parcelas, dataBase, regime, aplicarArt523),
                    serie != null ? serie : SeriesPublicadas.carregar(),
                    linhas, totais, tolerancia, justificativa);
        }
    }

    /** Atalho para transcrever valores impressos; {@code null} quando o documento não mostra. */
    public static Dinheiro dinheiro(String valor) {
        return valor == null ? null : Dinheiro.de(valor);
    }

    public static BigDecimal numero(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }
}
