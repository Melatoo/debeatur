package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Valores pagos por mês de vencimento, abatidos das parcelas de alimentos daquele mês.
 * <p>
 * Convenção da planilha de alimentos da DPMG: o pagamento é abatido pelo valor nominal da
 * parcela do mesmo mês, antes da correção, e o saldo é que se corrige e recebe juros. Não há
 * imputação (art. 354 do CC) nem atualização do pagamento pela data em que foi feito.
 * <p>
 * O pagamento maior que a parcela do mês segue o {@link ExcedentePagamento} escolhido (#112):
 * por padrão é rejeitado, e a mensagem orienta lançar o excedente como {@link Abatimento}; como
 * crédito no mês, o saldo da parcela fica negativo, como na planilha da DPMG.
 */
public final class PagamentosParciais {

    private final Map<YearMonth, Dinheiro> pagos = new HashMap<>();
    private final ExcedentePagamento excedente;

    /** Pagamentos que recusam o excedente, o padrão dos cálculos salvos antes da opção. */
    public PagamentosParciais() {
        this(ExcedentePagamento.RECUSAR);
    }

    public PagamentosParciais(ExcedentePagamento excedente) {
        this.excedente = Objects.requireNonNull(excedente, "excedente");
    }

    public PagamentosParciais com(YearMonth mes, Dinheiro valor) {
        Objects.requireNonNull(mes, "mes");
        Objects.requireNonNull(valor, "valor");
        if (valor.compareTo(Dinheiro.ZERO) <= 0) {
            throw new IllegalArgumentException("O pagamento de " + mes + " deve ser positivo");
        }
        if (pagos.putIfAbsent(mes, valor) != null) {
            throw new IllegalArgumentException("Pagamento de " + mes + " informado mais de uma vez");
        }
        return this;
    }

    /**
     * Abate cada pagamento da parcela que vence no mesmo mês, mantendo as datas de correção e
     * de juros. A parcela quitada fica no demonstrativo com saldo zero; a paga a maior, com o
     * crédito como saldo negativo, se o excedente for {@link ExcedentePagamento#CREDITO_NO_MES}.
     *
     * @throws IllegalArgumentException se um pagamento não tiver parcela no mês, se o mês tiver
     *                                  mais de uma parcela ou se o pagamento exceder a parcela e o
     *                                  excedente for {@link ExcedentePagamento#RECUSAR}
     */
    public List<Parcela> abater(List<Parcela> parcelas) {
        Set<YearMonth> meses = new HashSet<>();
        List<Parcela> saldos = new ArrayList<>();
        for (Parcela parcela : parcelas) {
            YearMonth mes = YearMonth.from(parcela.inicioCorrecao());
            if (!meses.add(mes) && pagos.containsKey(mes)) {
                throw new IllegalArgumentException("Mais de uma parcela em " + mes + " para abater o pagamento");
            }
            Dinheiro pago = pagos.get(mes);
            if (pago == null) {
                saldos.add(parcela);
                continue;
            }
            if (pago.compareTo(parcela.valor()) > 0 && excedente == ExcedentePagamento.RECUSAR) {
                throw new IllegalArgumentException("Pagamento de " + mes + " (" + pago
                        + ") maior que a parcela (" + parcela.valor() + "). Lance no mês até o valor da parcela"
                        + " e o excedente (" + pago.menos(parcela.valor()) + ") como abatimento, que sai do"
                        + " débito atualizado pelo valor nominal");
            }
            Dinheiro saldo = parcela.valor().menos(pago);
            int sinal = saldo.compareTo(Dinheiro.ZERO);
            String situacao = sinal == 0 ? " (quitada)"
                    : sinal < 0 ? " (crédito do pagamento a maior)" : " (saldo após pagamento parcial)";
            saldos.add(new Parcela(parcela.descricao() + situacao, saldo,
                    parcela.inicioCorrecao(), parcela.inicioJuros()));
        }
        Set<YearMonth> semParcela = new HashSet<>(pagos.keySet());
        semParcela.removeAll(meses);
        if (!semParcela.isEmpty()) {
            throw new IllegalArgumentException("Pagamento sem parcela no mês: " + semParcela.stream().sorted().toList());
        }
        return saldos;
    }
}
