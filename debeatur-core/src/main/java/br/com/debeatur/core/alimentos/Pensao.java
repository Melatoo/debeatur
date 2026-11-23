package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.SalarioMinimo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pensão alimentícia com uma parcela por mês, no valor que a {@link BasePensao} dá ao mês.
 * <p>
 * A correção corre desde o vencimento; os juros, conforme {@link InicioJuros}. Se o mês não
 * tem o dia do vencimento (ex.: dia 31 em fevereiro), a parcela vence no último dia do mês.
 * <p>
 * Com o 13º salário, a parcela de dezembro vale o dobro do mês comum, como na planilha de
 * alimentos da DPMG: é uma parcela só, com o vencimento de dezembro. O dobro é do valor já
 * arredondado. Não há 13º proporcional: dezembro dobra inteiro mesmo que a pensão comece no
 * meio do ano, e a pensão que termina antes de dezembro fica sem 13º.
 *
 * @param base           como o título fixou o valor mensal
 * @param diaVencimento  dia do mês em que a parcela vence, de 1 a 31
 * @param primeiroMes    mês da primeira parcela devida
 * @param ultimoMes      mês da última parcela devida (inclusive)
 * @param inicioJuros    termo inicial dos juros fixado no título
 * @param citacao        data da citação; obrigatória se os juros correm desde a citação
 * @param decimoTerceiro se a pensão alcança o 13º salário, pago em dobro em dezembro
 */
public record Pensao(BasePensao base, int diaVencimento, YearMonth primeiroMes, YearMonth ultimoMes,
                     InicioJuros inicioJuros, LocalDate citacao, boolean decimoTerceiro) {

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final BigDecimal DOBRO = BigDecimal.TWO;

    public Pensao {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(primeiroMes, "primeiroMes");
        Objects.requireNonNull(ultimoMes, "ultimoMes");
        Objects.requireNonNull(inicioJuros, "inicioJuros");
        if (diaVencimento < 1 || diaVencimento > 31) {
            throw new IllegalArgumentException("O dia do vencimento deve estar entre 1 e 31");
        }
        if (ultimoMes.isBefore(primeiroMes)) {
            throw new IllegalArgumentException("O último mês não pode ser anterior ao primeiro");
        }
        if (inicioJuros == InicioJuros.CITACAO && citacao == null) {
            throw new IllegalArgumentException("Informe a data da citação para juros desde a citação");
        }
        if (base instanceof BasePensao.PercentualDosRendimentos r && !r.mudancas().isEmpty()) {
            YearMonth primeiraMudanca = r.mudancas().firstKey();
            YearMonth ultimaMudanca = r.mudancas().lastKey();
            if (!primeiraMudanca.isAfter(primeiroMes) || ultimaMudanca.isAfter(ultimoMes)) {
                throw new IllegalArgumentException("As mudanças dos rendimentos devem ficar depois do primeiro mês da pensão ("
                        + primeiroMes + ") e até o último (" + ultimoMes + ")");
            }
        }
        if (base instanceof BasePensao.ValorFixo fixo && fixo.inicio().isAfter(primeiroMes)) {
            throw new IllegalArgumentException("O primeiro valor fixo começa em " + fixo.inicio()
                    + ", depois do primeiro mês da pensão (" + primeiroMes + ")");
        }
    }

    /** Pensão em percentual do salário mínimo, sem 13º. */
    public static Pensao emSalarioMinimo(BigDecimal percentual, int diaVencimento, YearMonth primeiroMes,
                                         YearMonth ultimoMes, InicioJuros inicioJuros, LocalDate citacao) {
        return new Pensao(new BasePensao.PercentualDoSalarioMinimo(percentual), diaVencimento, primeiroMes,
                ultimoMes, inicioJuros, citacao, false);
    }

    /**
     * Parcela da pensão com a base de que saiu, para a memória de cálculo.
     *
     * @param mes                 mês da parcela
     * @param base                base de cálculo do mês (salário mínimo vigente, rendimentos
     *                            ou valor fixo), conforme {@link BasePensao#base}
     * @param comDecimoTerceiro   se a parcela inclui o 13º salário (dezembro, em dobro)
     */
    public record ParcelaDoMes(YearMonth mes, Dinheiro base, boolean comDecimoTerceiro, Parcela parcela) {
    }

    /** @param salarioMinimo pode ser nulo se a base não usa o salário mínimo */
    public List<Parcela> parcelas(SalarioMinimo salarioMinimo) {
        return parcelasPorMes(salarioMinimo).stream().map(ParcelaDoMes::parcela).toList();
    }

    /** As mesmas parcelas de {@link #parcelas}, cada uma com a base do mês. */
    public List<ParcelaDoMes> parcelasPorMes(SalarioMinimo salarioMinimo) {
        List<ParcelaDoMes> parcelas = new ArrayList<>();
        for (YearMonth mes = primeiroMes; !mes.isAfter(ultimoMes); mes = mes.plusMonths(1)) {
            LocalDate vencimento = vencimento(mes);
            boolean com13 = decimoTerceiro && mes.getMonth() == Month.DECEMBER;
            Dinheiro mensal = base.valor(mes, salarioMinimo);
            Dinheiro valor = com13 ? mensal.vezes(DOBRO) : mensal;
            String descricao = "Alimentos " + mes.format(MES_ANO) + (com13 ? " e 13º salário" : "");
            Parcela parcela = new Parcela(descricao, valor, vencimento, inicioJuros(vencimento));
            parcelas.add(new ParcelaDoMes(mes, base.base(mes, salarioMinimo), com13, parcela));
        }
        return parcelas;
    }

    /** Vencimento da última parcela, em {@code ultimoMes}. */
    public LocalDate ultimoVencimento() {
        return vencimento(ultimoMes);
    }

    private LocalDate vencimento(YearMonth mes) {
        return mes.atDay(Math.min(diaVencimento, mes.lengthOfMonth()));
    }

    private LocalDate inicioJuros(LocalDate vencimento) {
        return switch (inicioJuros) {
            case VENCIMENTO -> vencimento;
            case CITACAO -> vencimento.isBefore(citacao) ? citacao : vencimento;
        };
    }
}
