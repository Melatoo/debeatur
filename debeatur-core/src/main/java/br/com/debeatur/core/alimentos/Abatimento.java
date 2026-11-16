package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Valor recebido pelo credor em qualquer data, mesmo depois do período cobrado (bloqueio em
 * conta, depósito judicial), que sai do total do débito.
 * <p>
 * Convenção da planilha de alimentos da DPMG: o abatimento entra pelo valor nominal, sem
 * correção nem juros, e a data serve só para identificá-lo. Diferente do
 * {@link PagamentosParciais pagamento parcial}, que sai da parcela do mesmo mês antes da
 * correção, o abatimento sai do débito já atualizado, e multa e honorários do art. 523
 * incidem sobre o saldo.
 *
 * @param data   data do recebimento
 * @param valor  valor recebido, positivo
 * @param origem de onde veio (ex.: "Bloqueio Sisbajud"); pode ser vazia
 */
public record Abatimento(LocalDate data, Dinheiro valor, String origem) {

    public Abatimento {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(valor, "valor");
        if (valor.compareTo(Dinheiro.ZERO) <= 0) {
            throw new IllegalArgumentException("O abatimento de " + data + " deve ser positivo");
        }
        origem = origem == null ? "" : origem.strip();
    }

    /** Soma nominal dos abatimentos. */
    public static Dinheiro total(List<Abatimento> abatimentos) {
        return abatimentos.stream().map(Abatimento::valor).reduce(Dinheiro.ZERO, Dinheiro::mais);
    }
}
