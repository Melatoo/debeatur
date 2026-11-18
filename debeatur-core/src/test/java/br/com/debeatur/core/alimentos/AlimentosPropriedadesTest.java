package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades da pensão alimentícia conferidas em casos sorteados: as parcelas que a
 * {@link Pensao} gera, o abatimento dos {@link PagamentosParciais}, a separação por
 * {@link DemonstrativoPorRito rito} e o fechamento com {@link TotalComAbatimentos abatimentos}.
 * <p>
 * Não substituem o gabarito da DPMG: nenhum valor esperado aqui é um valor de cálculo. São
 * invariantes que valem para qualquer entrada (o que se abate some do total na mesma medida, os
 * ritos repartem o débito sem sobra, a multa é 10% do saldo). A semente é fixa, então uma falha
 * se reproduz; a mensagem traz o número do caso.
 */
class AlimentosPropriedadesTest {

    private static final int CASOS = 300;
    private static final long SEMENTE = 528L;

    private static final YearMonth INICIO_SERIES = YearMonth.of(2010, 1);
    private static final YearMonth FIM_SERIES = YearMonth.of(2026, 12);
    private static final BigDecimal DEZ_PORCENTO = new BigDecimal("0.10");

    private static final RegimeAtualizacao REGIME = new RegimeAtualizacao("INPC + 1%", List.of(
            new RegraPeriodo(INICIO_SERIES, null, Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE))));

    private final SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
    private final SalarioMinimoEmMemoria salarioMinimo = new SalarioMinimoEmMemoria();
    private final MotorCalculo motor = new MotorCalculo(serie);

    AlimentosPropriedadesTest() {
        SplittableRandom aleatorio = new SplittableRandom(SEMENTE);
        for (YearMonth mes = INICIO_SERIES; !mes.isAfter(FIM_SERIES); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, BigDecimal.valueOf(aleatorio.nextInt(-100, 201), 2));
            salarioMinimo.com(mes, BigDecimal.valueOf(aleatorio.nextLong(500_00, 2_000_00), 2).toPlainString());
        }
    }

    @Test
    void umaParcelaPorMesComOVencimentoEOValorDoMes() {
        paraCadaCaso((n, caso) -> {
            Pensao pensao = caso.pensao();
            List<Pensao.ParcelaDoMes> parcelas = pensao.parcelasPorMes(salarioMinimo);

            YearMonth mes = pensao.primeiroMes();
            for (Pensao.ParcelaDoMes doMes : parcelas) {
                assertThat(doMes.mes()).as("caso %d: meses seguidos", n).isEqualTo(mes);
                Parcela parcela = doMes.parcela();
                LocalDate vencimento = mes.atDay(Math.min(pensao.diaVencimento(), mes.lengthOfMonth()));
                assertThat(parcela.inicioCorrecao()).as("caso %d, %s: vencimento", n, mes).isEqualTo(vencimento);

                LocalDate inicioJuros = pensao.inicioJuros() == InicioJuros.CITACAO && vencimento.isBefore(pensao.citacao())
                        ? pensao.citacao()
                        : vencimento;
                assertThat(parcela.inicioJuros()).as("caso %d, %s: início dos juros", n, mes).isEqualTo(inicioJuros);

                boolean com13 = pensao.decimoTerceiro() && mes.getMonth() == Month.DECEMBER;
                assertThat(doMes.comDecimoTerceiro()).as("caso %d, %s: 13º", n, mes).isEqualTo(com13);
                Dinheiro mensal = valorEsperadoDoMes(pensao.base(), mes);
                assertThat(parcela.valor()).as("caso %d, %s: valor", n, mes)
                        .isEqualTo(com13 ? mensal.mais(mensal) : mensal);
                mes = mes.plusMonths(1);
            }
            assertThat(mes).as("caso %d: até o último mês, inclusive", n).isEqualTo(pensao.ultimoMes().plusMonths(1));
        });
    }

    @Test
    void pagamentoParcialSaiDaParcelaDoMesSemMexerNasDatas() {
        paraCadaCaso((n, caso) -> {
            List<Parcela> parcelas = caso.pensao().parcelas(salarioMinimo);
            List<Parcela> saldos = caso.pagamentos().abater(parcelas);

            assertThat(saldos).as("caso %d: mesma quantidade de parcelas", n).hasSameSizeAs(parcelas);
            Dinheiro somaAntes = Dinheiro.ZERO;
            Dinheiro somaDepois = Dinheiro.ZERO;
            for (int i = 0; i < parcelas.size(); i++) {
                Parcela antes = parcelas.get(i);
                Parcela depois = saldos.get(i);
                assertThat(depois.inicioCorrecao()).as("caso %d, parcela %d: correção", n, i).isEqualTo(antes.inicioCorrecao());
                assertThat(depois.inicioJuros()).as("caso %d, parcela %d: juros", n, i).isEqualTo(antes.inicioJuros());
                Dinheiro pago = caso.pagos().getOrDefault(YearMonth.from(antes.inicioCorrecao()), Dinheiro.ZERO);
                assertThat(depois.valor()).as("caso %d, parcela %d: saldo", n, i).isEqualTo(antes.valor().menos(pago));
                somaAntes = somaAntes.mais(antes.valor());
                somaDepois = somaDepois.mais(depois.valor());
            }
            Dinheiro totalPago = caso.pagos().values().stream().reduce(Dinheiro.ZERO, Dinheiro::mais);
            assertThat(somaDepois.mais(totalPago)).as("caso %d: nada some nem aparece", n).isEqualTo(somaAntes);
        });
    }

    @Test
    void ritosRepartemODebitoPelasRegrasDaPrisao() {
        paraCadaCaso((n, caso) -> {
            Demonstrativo d = demonstrativo(caso);
            DemonstrativoPorRito r = DemonstrativoPorRito.separar(d, caso.ajuizamento(), caso.aplicarArt523(),
                    caso.abatimentos());

            assertThat(r.ritoDasLinhas()).as("caso %d: um rito por linha", n).hasSameSizeAs(d.linhas());
            assertThat(r.prisao().linhas().size() + r.penhora().linhas().size())
                    .as("caso %d: nenhuma linha em dois ritos", n).isEqualTo(d.linhas().size());
            assertThat(r.prisao().debitoAtualizado().mais(r.penhora().debitoAtualizado()))
                    .as("caso %d: os ritos somam o débito", n).isEqualTo(d.debitoAtualizado());

            int anteriores = 0;
            int anterioresNaPrisao = 0;
            LocalDate penhoraMaisRecente = null;
            LocalDate prisaoAnteriorMaisAntiga = null;
            for (int i = 0; i < d.linhas().size(); i++) {
                LocalDate vencimento = d.linhas().get(i).parcela().inicioCorrecao();
                Rito rito = r.ritoDasLinhas().get(i);
                if (vencimento.isAfter(caso.ajuizamento())) {
                    assertThat(rito).as("caso %d, linha %d: vencida no curso", n, i).isEqualTo(Rito.PRISAO);
                    continue;
                }
                anteriores++;
                if (rito == Rito.PRISAO) {
                    anterioresNaPrisao++;
                    prisaoAnteriorMaisAntiga = menor(prisaoAnteriorMaisAntiga, vencimento);
                } else {
                    penhoraMaisRecente = maior(penhoraMaisRecente, vencimento);
                }
            }
            assertThat(anterioresNaPrisao).as("caso %d: as três anteriores ao ajuizamento", n)
                    .isEqualTo(Math.min(anteriores, DemonstrativoPorRito.PARCELAS_ANTERIORES_NA_PRISAO));
            if (penhoraMaisRecente != null && prisaoAnteriorMaisAntiga != null) {
                assertThat(penhoraMaisRecente).as("caso %d: a penhora fica com as mais antigas", n)
                        .isBeforeOrEqualTo(prisaoAnteriorMaisAntiga);
            }
        });
    }

    @Test
    void abatimentosSaemPrimeiroDaPenhoraEAMultaIncideSobreOSaldo() {
        paraCadaCaso((n, caso) -> {
            Demonstrativo d = demonstrativo(caso);
            DemonstrativoPorRito r = DemonstrativoPorRito.separar(d, caso.ajuizamento(), caso.aplicarArt523(),
                    caso.abatimentos());
            Dinheiro abatido = Abatimento.total(caso.abatimentos());

            assertThat(r.abatidoPenhora().mais(r.abatidoPrisao())).as("caso %d: todo o abatido", n).isEqualTo(abatido);
            if (r.abatidoPrisao().compareTo(Dinheiro.ZERO) > 0) {
                assertThat(r.abatidoPenhora()).as("caso %d: a prisão só abate depois de zerar a penhora", n)
                        .isEqualTo(r.penhora().debitoAtualizado());
            }
            Dinheiro saldoPenhora = r.penhora().debitoAtualizado().menos(r.abatidoPenhora());
            Dinheiro dezPorCento = caso.aplicarArt523() ? saldoPenhora.vezes(DEZ_PORCENTO) : Dinheiro.ZERO;
            assertThat(r.multaArt523()).as("caso %d: multa", n).isEqualTo(dezPorCento);
            assertThat(r.honorariosArt523()).as("caso %d: honorários", n).isEqualTo(dezPorCento);
            assertThat(r.totalPrisao().mais(r.totalPenhora())).as("caso %d: total dos ritos", n).isEqualTo(r.total());
            assertThat(r.total()).as("caso %d: débito menos abatido mais acréscimos", n)
                    .isEqualTo(d.debitoAtualizado().menos(abatido).mais(r.multaArt523()).mais(r.honorariosArt523()));

            DemonstrativoPorRito semAbater = DemonstrativoPorRito.separar(d, caso.ajuizamento(), caso.aplicarArt523());
            assertThat(r.total()).as("caso %d: abater nunca aumenta o total", n).isLessThanOrEqualTo(semAbater.total());
        });
    }

    @Test
    void totalComAbatimentosFechaComOSaldo() {
        paraCadaCaso((n, caso) -> {
            Demonstrativo d = demonstrativo(caso);
            TotalComAbatimentos t = TotalComAbatimentos.de(d, caso.abatimentos(), caso.aplicarArt523());

            assertThat(t.totalAbatido()).as("caso %d: abatido", n).isEqualTo(Abatimento.total(caso.abatimentos()));
            assertThat(t.saldo()).as("caso %d: saldo", n).isEqualTo(d.debitoAtualizado().menos(t.totalAbatido()));
            Dinheiro dezPorCento = caso.aplicarArt523() ? t.saldo().vezes(DEZ_PORCENTO) : Dinheiro.ZERO;
            assertThat(t.multaArt523()).as("caso %d: multa", n).isEqualTo(dezPorCento);
            assertThat(t.honorariosArt523()).as("caso %d: honorários", n).isEqualTo(dezPorCento);
            assertThat(t.total()).as("caso %d: total", n)
                    .isEqualTo(t.saldo().mais(t.multaArt523()).mais(t.honorariosArt523()));
        });
    }

    @Test
    void recalcularDaOMesmoResultado() {
        paraCadaCaso((n, caso) -> {
            assertThat(caso.pensao().parcelasPorMes(salarioMinimo)).as("caso %d: parcelas", n)
                    .isEqualTo(caso.pensao().parcelasPorMes(salarioMinimo));
            Demonstrativo d = demonstrativo(caso);
            assertThat(DemonstrativoPorRito.separar(d, caso.ajuizamento(), caso.aplicarArt523(), caso.abatimentos()))
                    .as("caso %d: ritos", n)
                    .isEqualTo(DemonstrativoPorRito.separar(d, caso.ajuizamento(), caso.aplicarArt523(), caso.abatimentos()));
        });
    }

    /** Um caso sorteado: a pensão, os pagamentos por mês, o ajuizamento e os abatimentos. */
    private record Caso(Pensao pensao, TreeMap<YearMonth, Dinheiro> pagos, LocalDate dataBase,
                        LocalDate ajuizamento, List<Abatimento> abatimentos, boolean aplicarArt523) {

        PagamentosParciais pagamentos() {
            PagamentosParciais pagamentos = new PagamentosParciais();
            pagos.forEach(pagamentos::com);
            return pagamentos;
        }
    }

    private interface Propriedade {
        void conferir(int caso, Caso dados);
    }

    private void paraCadaCaso(Propriedade propriedade) {
        SplittableRandom aleatorio = new SplittableRandom(SEMENTE + 1);
        for (int n = 0; n < CASOS; n++) {
            propriedade.conferir(n, sortear(aleatorio));
        }
    }

    /** O demonstrativo das parcelas já com os pagamentos, sem o art. 523, que os ritos e o abatimento aplicam. */
    private Demonstrativo demonstrativo(Caso caso) {
        List<Parcela> saldos = caso.pagamentos().abater(caso.pensao().parcelas(salarioMinimo));
        return motor.calcular(new ParametrosCalculo(saldos, caso.dataBase(), REGIME, false));
    }

    private Caso sortear(SplittableRandom aleatorio) {
        YearMonth primeiroMes = mes(aleatorio, INICIO_SERIES, FIM_SERIES.minusMonths(2));
        YearMonth ultimoMes = mes(aleatorio, primeiroMes, min(primeiroMes.plusMonths(48), FIM_SERIES.minusMonths(1)));
        int diaVencimento = aleatorio.nextInt(1, 32);
        LocalDate ultimoVencimento = ultimoMes.atDay(Math.min(diaVencimento, ultimoMes.lengthOfMonth()));
        LocalDate dataBase = dia(aleatorio, ultimoVencimento, FIM_SERIES.plusMonths(1).atDay(1));

        InicioJuros inicioJuros = aleatorio.nextBoolean() ? InicioJuros.VENCIMENTO : InicioJuros.CITACAO;
        LocalDate citacao = inicioJuros == InicioJuros.CITACAO
                ? dia(aleatorio, primeiroMes.atDay(1), dataBase)
                : null;
        Pensao pensao = new Pensao(base(aleatorio, primeiroMes, ultimoMes), diaVencimento, primeiroMes, ultimoMes,
                inicioJuros, citacao, aleatorio.nextBoolean());

        TreeMap<YearMonth, Dinheiro> pagos = new TreeMap<>();
        for (Pensao.ParcelaDoMes doMes : pensao.parcelasPorMes(salarioMinimo)) {
            if (aleatorio.nextInt(10) < 3) {
                long centavos = doMes.parcela().valor().valor().movePointRight(2).longValueExact();
                pagos.put(doMes.mes(), new Dinheiro(BigDecimal.valueOf(aleatorio.nextLong(1, centavos + 1), 2)));
            }
        }

        LocalDate ajuizamento = dia(aleatorio, primeiroMes.atDay(1), dataBase);
        boolean aplicarArt523 = aleatorio.nextBoolean();
        Caso semAbatimentos = new Caso(pensao, pagos, dataBase, ajuizamento, List.of(), aplicarArt523);
        Dinheiro debito = demonstrativo(semAbatimentos).debitoAtualizado();
        List<Abatimento> abatimentos = abatimentos(aleatorio, debito, primeiroMes.atDay(1), dataBase);
        return new Caso(pensao, pagos, dataBase, ajuizamento, abatimentos, aplicarArt523);
    }

    private static BasePensao base(SplittableRandom aleatorio, YearMonth primeiroMes, YearMonth ultimoMes) {
        BigDecimal percentual = BigDecimal.valueOf(aleatorio.nextInt(1, 10_001), 2);
        return switch (aleatorio.nextInt(3)) {
            case 0 -> new BasePensao.PercentualDoSalarioMinimo(percentual);
            case 1 -> new BasePensao.PercentualDosRendimentos(percentual,
                    new Dinheiro(BigDecimal.valueOf(aleatorio.nextLong(1_000_00, 50_000_00), 2)));
            default -> {
                TreeMap<YearMonth, Dinheiro> valores = new TreeMap<>();
                valores.put(mes(aleatorio, primeiroMes.minusMonths(6), primeiroMes), valorFixo(aleatorio));
                for (int i = aleatorio.nextInt(0, 4); i > 0; i--) {
                    valores.put(mes(aleatorio, primeiroMes, ultimoMes), valorFixo(aleatorio));
                }
                yield new BasePensao.ValorFixo(valores);
            }
        };
    }

    /** Valor do mês comum calculado à parte, sem passar pela {@link BasePensao}. */
    private Dinheiro valorEsperadoDoMes(BasePensao base, YearMonth mes) {
        return switch (base) {
            case BasePensao.PercentualDoSalarioMinimo b ->
                    new Dinheiro(salarioMinimo.vigente(mes).valor().multiply(b.percentual()).movePointLeft(2));
            case BasePensao.PercentualDosRendimentos b ->
                    new Dinheiro(b.rendimentos().valor().multiply(b.percentual()).movePointLeft(2));
            case BasePensao.ValorFixo b -> {
                Dinheiro vigente = null;
                for (var e : b.valoresAPartirDe().entrySet()) {
                    if (!e.getKey().isAfter(mes)) {
                        vigente = e.getValue();
                    }
                }
                yield vigente;
            }
        };
    }

    /** De zero a três abatimentos que, somados, não passam do débito. */
    private static List<Abatimento> abatimentos(SplittableRandom aleatorio, Dinheiro debito, LocalDate de,
                                                LocalDate ate) {
        List<Abatimento> abatimentos = new ArrayList<>();
        long restante = debito.valor().movePointRight(2).longValueExact();
        for (int i = aleatorio.nextInt(0, 4); i > 0 && restante > 0; i--) {
            long centavos = aleatorio.nextLong(1, restante + 1);
            restante -= centavos;
            abatimentos.add(new Abatimento(dia(aleatorio, de, ate), new Dinheiro(BigDecimal.valueOf(centavos, 2)),
                    "Abatimento " + (abatimentos.size() + 1)));
        }
        return abatimentos;
    }

    private static Dinheiro valorFixo(SplittableRandom aleatorio) {
        return new Dinheiro(BigDecimal.valueOf(aleatorio.nextLong(100_00, 10_000_00), 2));
    }

    private static YearMonth mes(SplittableRandom aleatorio, YearMonth de, YearMonth ate) {
        long meses = de.until(ate, java.time.temporal.ChronoUnit.MONTHS);
        return de.plusMonths(aleatorio.nextLong(0, meses + 1));
    }

    /** Dia sorteado entre {@code de} e {@code ate}, inclusive. */
    private static LocalDate dia(SplittableRandom aleatorio, LocalDate de, LocalDate ate) {
        return de.plusDays(aleatorio.nextLong(0, ate.toEpochDay() - de.toEpochDay() + 1));
    }

    private static YearMonth min(YearMonth a, YearMonth b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate menor(LocalDate atual, LocalDate outra) {
        return atual == null || outra.isBefore(atual) ? outra : atual;
    }

    private static LocalDate maior(LocalDate atual, LocalDate outra) {
        return atual == null || outra.isAfter(atual) ? outra : atual;
    }
}
