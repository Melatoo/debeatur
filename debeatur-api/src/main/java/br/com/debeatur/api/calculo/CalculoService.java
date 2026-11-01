package br.com.debeatur.api.calculo;

import br.com.debeatur.core.alimentos.PagamentosParciais;
import br.com.debeatur.core.alimentos.PensaoEmSalarioMinimo;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimo;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.RegraPeriodo;
import br.com.debeatur.indices.SgsClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class CalculoService {

    private final SgsClient sgs;

    public CalculoService(SgsClient sgs) {
        this.sgs = sgs;
    }

    /** Parcelas da pensão, com o salário mínimo do SGS e os pagamentos já abatidos. */
    public List<Parcela> parcelas(PensaoRequest pensao, LocalDate dataBase) {
        PensaoEmSalarioMinimo dominio = pensao.paraDominio();
        if (dominio.ultimoVencimento().isAfter(dataBase)) {
            throw new IllegalArgumentException("A última parcela da pensão vence em " + dominio.ultimoVencimento()
                    + ", depois da data-base (" + dataBase + ")");
        }
        PagamentosParciais pagamentos = pensao.pagamentosParaDominio();
        SalarioMinimo salarioMinimo = sgs.carregarSalarioMinimo(dominio.primeiroMes(), dominio.ultimoMes());
        return pagamentos.abater(dominio.parcelas(salarioMinimo));
    }

    // TODO: persistir séries e snapshot dos índices usados, em vez de consultar o SGS a cada cálculo
    public Demonstrativo calcular(ParametrosCalculo parametros) {
        YearMonth ultimoMes = YearMonth.from(parametros.dataBase()).minusMonths(1);
        YearMonth primeiroMes = parametros.parcelas().stream()
                .flatMap(p -> Stream.of(p.inicioCorrecao(), p.inicioJuros()))
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .min(YearMonth::compareTo)
                .orElseThrow();

        SerieIndicesEmMemoria serie = primeiroMes.isAfter(ultimoMes)
                ? new SerieIndicesEmMemoria()
                : sgs.carregar(indicesNecessarios(parametros), primeiroMes, ultimoMes);

        return new MotorCalculo(serie).calcular(parametros);
    }

    private static Set<Indice> indicesNecessarios(ParametrosCalculo parametros) {
        Set<Indice> indices = EnumSet.noneOf(Indice.class);
        for (RegraPeriodo regra : parametros.regime().regras()) {
            indices.add(regra.correcao());
            if (regra.juros().usaTaxaLegal()) {
                indices.add(Indice.TAXA_LEGAL);
            }
        }
        return indices;
    }
}
