import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.alimentos.Abatimento;
import br.com.debeatur.core.alimentos.BasePensao;
import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.alimentos.InicioJuros;
import br.com.debeatur.core.alimentos.PagamentosParciais;
import br.com.debeatur.core.alimentos.Pensao;
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

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Teste diferencial do motor: calcula casos sorteados com semente fixa e imprime cada resultado
 * por inteiro (fator com todos os dígitos, cada valor, cada total). O script
 * {@code diferencial-motor.sh} roda este arquivo contra o {@code debeatur-core} da base e o da
 * branch e compara as duas saídas: qualquer linha diferente é uma mudança de valor.
 * <p>
 * Usa só a API pública do core, para compilar contra as duas versões. Os índices e o salário
 * mínimo são sorteados: o que se compara é o motor, não as séries. Nada aqui é gabarito; os
 * valores certos continuam vindo dos testes de gabarito.
 * <p>
 * Uso: {@code java -cp debeatur-core.jar DiferencialMotor.java [casos]}
 */
public class DiferencialMotor {

    private static final long SEMENTE = 524_528L;
    private static final YearMonth INICIO = YearMonth.of(2000, 1);
    private static final YearMonth FIM = YearMonth.of(2026, 12);
    private static final YearMonth VIRADA = YearMonth.of(2016, 1);

    private static final RegimeAtualizacao REGIME = new RegimeAtualizacao("INPC + 1% / IPCA + 0,5%", List.of(
            new RegraPeriodo(INICIO, VIRADA.minusMonths(1), Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE)),
            new RegraPeriodo(VIRADA, null, Indice.IPCA, new Juros.TaxaFixa(new BigDecimal("0.5")))));

    public static void main(String[] args) {
        int casos = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);

        SplittableRandom aleatorio = new SplittableRandom(SEMENTE);
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        SalarioMinimoEmMemoria salarioMinimo = new SalarioMinimoEmMemoria();
        for (YearMonth mes = INICIO; !mes.isAfter(FIM); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, BigDecimal.valueOf(aleatorio.nextInt(-100, 201), 2));
            serie.com(Indice.IPCA, mes, BigDecimal.valueOf(aleatorio.nextInt(-100, 201), 2));
            salarioMinimo.com(mes, BigDecimal.valueOf(aleatorio.nextLong(150_00, 2_000_00), 2).toPlainString());
        }
        MotorCalculo motor = new MotorCalculo(serie);

        for (int n = 0; n < casos; n++) {
            // um gerador por caso: um valor que muda num caso não desloca o sorteio dos seguintes
            SplittableRandom sorteio = new SplittableRandom(SEMENTE + 1 + n);
            String caso = (n % 2 == 0 ? "geral " : "alimentos ") + n;
            try {
                if (n % 2 == 0) {
                    geral(out, caso, motor, sorteio);
                } else {
                    alimentos(out, caso, motor, salarioMinimo, sorteio);
                }
            } catch (RuntimeException e) {
                // uma entrada que passou a ser rejeitada (ou deixou de ser) também é diferença
                out.println(caso + " | erro " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        out.flush();
    }

    private static void geral(PrintStream out, String caso, MotorCalculo motor, SplittableRandom aleatorio) {
        LocalDate dataBase = dia(aleatorio, LocalDate.of(2000, 2, 1), FIM.plusMonths(1).atDay(1));
        List<Parcela> parcelas = new ArrayList<>();
        for (int i = aleatorio.nextInt(1, 7); i > 0; i--) {
            LocalDate correcao = dia(aleatorio, INICIO.atDay(1), dataBase);
            LocalDate juros = aleatorio.nextInt(10) < 3 ? null : dia(aleatorio, correcao, dataBase);
            parcelas.add(new Parcela("Parcela " + parcelas.size(), centavos(aleatorio, 1, 10_000_000_00L),
                    correcao, juros));
        }
        imprimir(out, caso, motor.calcular(new ParametrosCalculo(parcelas, dataBase, REGIME, aleatorio.nextBoolean())));
    }

    private static void alimentos(PrintStream out, String caso, MotorCalculo motor, SalarioMinimoEmMemoria salarioMinimo,
                                  SplittableRandom aleatorio) {
        YearMonth primeiro = mes(aleatorio, INICIO, FIM.minusMonths(2));
        YearMonth ultimo = mes(aleatorio, primeiro, primeiro.plusMonths(60).isBefore(FIM) ? primeiro.plusMonths(60) : FIM.minusMonths(1));
        int dia = aleatorio.nextInt(1, 32);
        LocalDate dataBase = dia(aleatorio, ultimo.atDay(Math.min(dia, ultimo.lengthOfMonth())), FIM.plusMonths(1).atDay(1));
        InicioJuros inicioJuros = aleatorio.nextBoolean() ? InicioJuros.VENCIMENTO : InicioJuros.CITACAO;
        LocalDate citacao = inicioJuros == InicioJuros.CITACAO ? dia(aleatorio, primeiro.atDay(1), dataBase) : null;

        BigDecimal percentual = BigDecimal.valueOf(aleatorio.nextInt(1, 10_001), 2);
        BasePensao base = switch (aleatorio.nextInt(3)) {
            case 0 -> new BasePensao.PercentualDoSalarioMinimo(percentual);
            case 1 -> new BasePensao.PercentualDosRendimentos(percentual, centavos(aleatorio, 1_000_00, 50_000_00));
            default -> {
                TreeMap<YearMonth, Dinheiro> valores = new TreeMap<>();
                valores.put(primeiro, centavos(aleatorio, 100_00, 10_000_00));
                for (int i = aleatorio.nextInt(0, 4); i > 0; i--) {
                    valores.put(mes(aleatorio, primeiro, ultimo), centavos(aleatorio, 100_00, 10_000_00));
                }
                yield new BasePensao.ValorFixo(valores);
            }
        };
        Pensao pensao = new Pensao(base, dia, primeiro, ultimo, inicioJuros, citacao, aleatorio.nextBoolean());
        List<Parcela> parcelas = pensao.parcelas(salarioMinimo);

        PagamentosParciais pagamentos = new PagamentosParciais();
        for (Parcela p : parcelas) {
            if (aleatorio.nextInt(10) < 3) {
                long maximo = p.valor().valor().movePointRight(2).longValueExact();
                pagamentos.com(YearMonth.from(p.inicioCorrecao()), centavos(aleatorio, 1, maximo));
            }
        }
        Demonstrativo d = motor.calcular(new ParametrosCalculo(pagamentos.abater(parcelas), dataBase, REGIME, false));
        imprimir(out, caso, d);

        LocalDate ajuizamento = dia(aleatorio, primeiro.atDay(1), dataBase);
        List<Abatimento> abatimentos = new ArrayList<>();
        long restante = d.debitoAtualizado().valor().movePointRight(2).longValueExact();
        for (int i = aleatorio.nextInt(0, 4); i > 0 && restante > 0; i--) {
            long valor = aleatorio.nextLong(1, restante + 1);
            restante -= valor;
            abatimentos.add(new Abatimento(dia(aleatorio, primeiro.atDay(1), dataBase),
                    new Dinheiro(BigDecimal.valueOf(valor, 2)), "Abatimento " + abatimentos.size()));
        }
        DemonstrativoPorRito r = DemonstrativoPorRito.separar(d, ajuizamento, aleatorio.nextBoolean(), abatimentos);
        out.println(caso + " | ritos " + r.ritoDasLinhas()
                + " | prisão " + r.prisao().debitoAtualizado().valor().toPlainString()
                + " | penhora " + r.penhora().debitoAtualizado().valor().toPlainString()
                + " | abatido " + r.abatidoPenhora().valor().toPlainString() + " + " + r.abatidoPrisao().valor().toPlainString()
                + " | multa " + r.multaArt523().valor().toPlainString()
                + " | honorários " + r.honorariosArt523().valor().toPlainString()
                + " | total " + r.total().valor().toPlainString());
    }

    private static void imprimir(PrintStream out, String caso, Demonstrativo d) {
        for (int i = 0; i < d.linhas().size(); i++) {
            LinhaDemonstrativo l = d.linhas().get(i);
            Parcela p = l.parcela();
            out.println(caso + " | linha " + i + " | " + p.descricao() + " | " + p.valor().valor().toPlainString()
                    + " | " + p.inicioCorrecao() + " | " + p.inicioJuros()
                    + " | fator " + l.fatorCorrecao().toPlainString()
                    + " | corrigido " + l.valorCorrigido().valor().toPlainString()
                    + " | juros " + l.percentualJuros().stripTrailingZeros().toPlainString() + "% "
                    + l.valorJuros().valor().toPlainString()
                    + " | subtotal " + l.subtotal().valor().toPlainString());
        }
        out.println(caso + " | totais | corrigido " + d.totalCorrigido().valor().toPlainString()
                + " | juros " + d.totalJuros().valor().toPlainString()
                + " | débito " + d.debitoAtualizado().valor().toPlainString()
                + " | multa " + d.multaArt523().valor().toPlainString()
                + " | honorários " + d.honorariosArt523().valor().toPlainString()
                + " | total " + d.total().valor().toPlainString());
    }

    private static Dinheiro centavos(SplittableRandom aleatorio, long de, long ate) {
        return new Dinheiro(BigDecimal.valueOf(aleatorio.nextLong(de, ate + 1), 2));
    }

    private static YearMonth mes(SplittableRandom aleatorio, YearMonth de, YearMonth ate) {
        return de.plusMonths(aleatorio.nextLong(0, de.until(ate, ChronoUnit.MONTHS) + 1));
    }

    private static LocalDate dia(SplittableRandom aleatorio, LocalDate de, LocalDate ate) {
        return de.plusDays(aleatorio.nextLong(0, ate.toEpochDay() - de.toEpochDay() + 1));
    }
}
