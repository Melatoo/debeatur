package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.SalarioMinimo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pensão alimentícia fixada em percentual do salário mínimo, com uma parcela por mês.
 * <p>
 * Cada parcela vale o percentual do salário mínimo vigente no mês do vencimento (Súmula 490
 * do STF), arredondado como {@link Dinheiro}. A correção corre desde o vencimento; os juros,
 * conforme {@link InicioJuros}. Se o mês não tem o dia do vencimento (ex.: dia 31 em
 * fevereiro), a parcela vence no último dia do mês.
 *
 * @param percentual    percentual do salário mínimo, em pontos (ex.: {@code 30} para 30%)
 * @param diaVencimento dia do mês em que a parcela vence, de 1 a 31
 * @param primeiroMes   mês da primeira parcela devida
 * @param ultimoMes     mês da última parcela devida (inclusive)
 * @param inicioJuros   termo inicial dos juros fixado no título
 * @param citacao       data da citação; obrigatória se os juros correm desde a citação
 */
public record PensaoEmSalarioMinimo(BigDecimal percentual, int diaVencimento, YearMonth primeiroMes,
                                    YearMonth ultimoMes, InicioJuros inicioJuros, LocalDate citacao) {

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MM/yyyy");

    public PensaoEmSalarioMinimo {
        Objects.requireNonNull(percentual, "percentual");
        Objects.requireNonNull(primeiroMes, "primeiroMes");
        Objects.requireNonNull(ultimoMes, "ultimoMes");
        Objects.requireNonNull(inicioJuros, "inicioJuros");
        if (percentual.signum() <= 0) {
            throw new IllegalArgumentException("O percentual do salário mínimo deve ser positivo");
        }
        if (diaVencimento < 1 || diaVencimento > 31) {
            throw new IllegalArgumentException("O dia do vencimento deve estar entre 1 e 31");
        }
        if (ultimoMes.isBefore(primeiroMes)) {
            throw new IllegalArgumentException("O último mês não pode ser anterior ao primeiro");
        }
        if (inicioJuros == InicioJuros.CITACAO && citacao == null) {
            throw new IllegalArgumentException("Informe a data da citação para juros desde a citação");
        }
    }

    /**
     * Parcela da pensão com o salário mínimo de que saiu, para a memória de cálculo.
     *
     * @param mes           mês da parcela
     * @param salarioMinimo salário mínimo vigente no mês, sobre o qual incide o percentual
     */
    public record ParcelaDoMes(YearMonth mes, Dinheiro salarioMinimo, Parcela parcela) {
    }

    public List<Parcela> parcelas(SalarioMinimo salarioMinimo) {
        return parcelasPorMes(salarioMinimo).stream().map(ParcelaDoMes::parcela).toList();
    }

    /** As mesmas parcelas de {@link #parcelas}, cada uma com o salário mínimo do mês. */
    public List<ParcelaDoMes> parcelasPorMes(SalarioMinimo salarioMinimo) {
        List<ParcelaDoMes> parcelas = new ArrayList<>();
        for (YearMonth mes = primeiroMes; !mes.isAfter(ultimoMes); mes = mes.plusMonths(1)) {
            LocalDate vencimento = vencimento(mes);
            Dinheiro vigente = salarioMinimo.vigente(mes);
            Parcela parcela = new Parcela("Alimentos " + mes.format(MES_ANO), vigente.percentual(percentual),
                    vencimento, inicioJuros(vencimento));
            parcelas.add(new ParcelaDoMes(mes, vigente, parcela));
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
