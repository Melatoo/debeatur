package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.alimentos.PensaoEmSalarioMinimo;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimo;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.RegraPeriodo;
import br.com.debeatur.api.serie.FonteSeries;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class CalculoService {

    private final FonteSeries series;

    public CalculoService(FonteSeries series) {
        this.series = series;
    }

    /**
     * Calcula o demonstrativo com as séries do cache do SGS.
     *
     * @throws IllegalArgumentException se o pedido for inconsistente
     */
    public DemonstrativoResponse calcular(CalculoRequest request) {
        return calcular(request, series);
    }

    /**
     * Calcula o demonstrativo com as séries da fonte informada; com o snapshot de um cálculo
     * salvo, refaz o demonstrativo daquele dia.
     */
    public DemonstrativoResponse calcular(CalculoRequest request, FonteSeries fonte) {
        ParcelasDaPensao pensao = request.pensao() == null
                ? ParcelasDaPensao.vazia()
                : parcelas(request.pensao(), request.dataBase(), fonte);
        ParametrosCalculo parametros = request.paraDominio(pensao.parcelas());
        Demonstrativo demonstrativo = calcular(parametros, fonte);
        if (!request.separaPorRito()) {
            return DemonstrativoResponse.de(demonstrativo, parametros.regime(), pensao.memoria());
        }
        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, request.pensao().ajuizamento(),
                request.aplicarArt523());
        return DemonstrativoResponse.de(demonstrativo, parametros.regime(), pensao.memoria(), porRito);
    }

    /**
     * Saldos da pensão e a memória de cada um.
     *
     * @param parcelas saldos que vão ao motor, na ordem dos meses
     * @param memoria  de onde veio cada saldo, indexada pela própria {@link Parcela} (por
     *                 identidade), que o motor repassa às linhas do demonstrativo; só leitura
     */
    record ParcelasDaPensao(List<Parcela> parcelas, Map<Parcela, DemonstrativoResponse.MesPensao> memoria) {

        public ParcelasDaPensao {
            parcelas = List.copyOf(parcelas);
            memoria = Collections.unmodifiableMap(memoria);
        }

        static ParcelasDaPensao vazia() {
            return new ParcelasDaPensao(List.of(), Map.of());
        }
    }

    /**
     * Parcelas da pensão, com o salário mínimo da fonte e os pagamentos já abatidos.
     *
     * @throws IllegalArgumentException se a última parcela vencer depois da data-base
     */
    private static ParcelasDaPensao parcelas(PensaoRequest pensao, LocalDate dataBase, FonteSeries fonte) {
        PensaoEmSalarioMinimo dominio = pensao.paraDominio();
        if (dominio.ultimoVencimento().isAfter(dataBase)) {
            throw new IllegalArgumentException("A última parcela da pensão vence em " + dominio.ultimoVencimento()
                    + ", depois da data-base (" + dataBase + ")");
        }
        SalarioMinimo salarioMinimo = fonte.carregarSalarioMinimo(dominio.primeiroMes(), dominio.ultimoMes());
        List<PensaoEmSalarioMinimo.ParcelaDoMes> devidas = dominio.parcelasPorMes(salarioMinimo);
        List<Parcela> saldos = pensao.pagamentosParaDominio()
                .abater(devidas.stream().map(PensaoEmSalarioMinimo.ParcelaDoMes::parcela).toList());
        if (saldos.size() != devidas.size()) {
            throw new IllegalStateException("O abatimento dos pagamentos deve manter uma parcela por mês");
        }
        Map<Parcela, DemonstrativoResponse.MesPensao> memoria = new IdentityHashMap<>();
        for (int i = 0; i < devidas.size(); i++) {
            PensaoEmSalarioMinimo.ParcelaDoMes devida = devidas.get(i);
            Dinheiro devido = devida.parcela().valor();
            Parcela saldo = saldos.get(i);
            memoria.put(saldo, new DemonstrativoResponse.MesPensao(devida.mes(), devida.salarioMinimo().valor(),
                    devido.valor(), devido.menos(saldo.valor()).valor()));
        }
        return new ParcelasDaPensao(saldos, memoria);
    }

    private static Demonstrativo calcular(ParametrosCalculo parametros, FonteSeries fonte) {
        YearMonth ultimoMes = YearMonth.from(parametros.dataBase()).minusMonths(1);
        YearMonth primeiroMes = parametros.parcelas().stream()
                .flatMap(p -> Stream.of(p.inicioCorrecao(), p.inicioJuros()))
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .min(YearMonth::compareTo)
                .orElseThrow();

        SerieIndicesEmMemoria serie = primeiroMes.isAfter(ultimoMes)
                ? new SerieIndicesEmMemoria()
                : fonte.carregar(indicesNecessarios(parametros), primeiroMes, ultimoMes);

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
