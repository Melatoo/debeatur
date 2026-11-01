package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.alimentos.InicioJuros;
import br.com.debeatur.core.alimentos.PagamentosParciais;
import br.com.debeatur.core.alimentos.PensaoEmSalarioMinimo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Pensão alimentícia fixada em percentual do salário mínimo, com uma parcela por mês de
 * {@code primeiroMes} a {@code ultimoMes}.
 *
 * @param percentual percentual do salário mínimo, em pontos (ex.: {@code 30} para 30%)
 * @param citacao     obrigatória se {@code inicioJuros} for {@link InicioJuros#CITACAO}
 * @param pagamentos  valores pagos, abatidos da parcela do mesmo mês
 * @param ajuizamento data do ajuizamento da execução; se informada, as linhas do demonstrativo,
 *                    inclusive as avulsas, são separadas entre o rito da prisão e o da penhora
 *                    pelo vencimento, e o art. 523 incide só sobre a penhora
 */
public record PensaoRequest(
        @NotNull @Positive BigDecimal percentual,
        @NotNull @Min(1) @Max(31) Integer diaVencimento,
        @NotNull YearMonth primeiroMes,
        @NotNull YearMonth ultimoMes,
        @NotNull InicioJuros inicioJuros,
        LocalDate citacao,
        List<@Valid PagamentoRequest> pagamentos,
        LocalDate ajuizamento) {

    public record PagamentoRequest(@NotNull YearMonth mes, @NotNull @Positive BigDecimal valor) {
    }

    PensaoEmSalarioMinimo paraDominio() {
        return new PensaoEmSalarioMinimo(percentual, diaVencimento, primeiroMes, ultimoMes, inicioJuros, citacao);
    }

    PagamentosParciais pagamentosParaDominio() {
        PagamentosParciais pagos = new PagamentosParciais();
        if (pagamentos != null) {
            pagamentos.forEach(p -> pagos.com(p.mes(), new Dinheiro(p.valor())));
        }
        return pagos;
    }
}
