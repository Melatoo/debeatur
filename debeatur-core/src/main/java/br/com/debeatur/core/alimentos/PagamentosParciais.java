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
 * Pagamento maior que a parcela do mês é rejeitado: a compensação de alimentos pagos a maior
 * com outras parcelas não é automática.
 */
public final class PagamentosParciais {

    private final Map<YearMonth, Dinheiro> pagos = new HashMap<>();

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
     * de juros. A parcela quitada fica no demonstrativo com saldo zero.
     *
     * @throws IllegalArgumentException se um pagamento não tiver parcela no mês, se o mês tiver
     *                                  mais de uma parcela ou se o pagamento exceder a parcela
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
            if (pago.compareTo(parcela.valor()) > 0) {
                throw new IllegalArgumentException("Pagamento de " + mes + " (" + pago
                        + ") maior que a parcela (" + parcela.valor() + ")");
            }
            Dinheiro saldo = parcela.valor().menos(pago);
            String situacao = saldo.equals(Dinheiro.ZERO) ? " (quitada)" : " (saldo após pagamento parcial)";
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
