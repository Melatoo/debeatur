package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Linhas de um demonstrativo de alimentos separadas entre o rito da prisão e o da penhora.
 * <p>
 * A separação não altera o valor de nenhuma linha: classifica as linhas já calculadas pelo
 * vencimento da parcela ({@link br.com.debeatur.core.calculo.Parcela#inicioCorrecao()}) e soma
 * cada grupo. Os dois subtotais somam exatamente o débito atualizado do demonstrativo.
 * <p>
 * Multa e honorários de 10% do art. 523, §1º, do CPC incidem só sobre o subtotal da penhora
 * (art. 528, §8º); no rito da prisão eles não se aplicam. Por isso o demonstrativo de origem
 * deve vir sem eles.
 * <p>
 * Convenções:
 * <ul>
 *   <li>Vão para a prisão as três parcelas de vencimento mais recente até o ajuizamento,
 *       inclusive a que vence no próprio dia, e todas as que vencem depois dele.</li>
 *   <li>Conta-se por parcela, não por saldo: uma parcela quitada entre as três últimas ocupa o
 *       lugar dela, com saldo zero, e não puxa uma parcela mais antiga para a prisão.</li>
 * </ul>
 *
 * @param ajuizamento      data do ajuizamento da execução
 * @param ritoDasLinhas    rito de cada linha do demonstrativo, na mesma ordem
 * @param multaArt523      10% do débito da penhora, ou zero
 * @param honorariosArt523 10% do débito da penhora, ou zero
 * @param total            débito dos dois ritos mais multa e honorários
 */
public record DemonstrativoPorRito(LocalDate ajuizamento, List<Rito> ritoDasLinhas, Subtotal prisao,
                                   Subtotal penhora, Dinheiro multaArt523, Dinheiro honorariosArt523,
                                   Dinheiro total) {

    /** Parcelas anteriores ao ajuizamento que podem ser cobradas sob pena de prisão. */
    public static final int PARCELAS_ANTERIORES_NA_PRISAO = 3;

    private static final BigDecimal DEZ_PORCENTO = BigDecimal.TEN;

    public DemonstrativoPorRito {
        Objects.requireNonNull(ajuizamento, "ajuizamento");
        ritoDasLinhas = List.copyOf(ritoDasLinhas);
        Objects.requireNonNull(prisao, "prisao");
        Objects.requireNonNull(penhora, "penhora");
        Objects.requireNonNull(multaArt523, "multaArt523");
        Objects.requireNonNull(honorariosArt523, "honorariosArt523");
        Objects.requireNonNull(total, "total");
    }

    /** Linhas de um rito, na ordem do demonstrativo, e os totais delas. */
    public record Subtotal(List<LinhaDemonstrativo> linhas, Dinheiro totalCorrigido, Dinheiro totalJuros,
                           Dinheiro debitoAtualizado) {

        public Subtotal {
            linhas = List.copyOf(linhas);
        }

        static Subtotal de(List<LinhaDemonstrativo> linhas) {
            Dinheiro corrigido = Dinheiro.ZERO;
            Dinheiro juros = Dinheiro.ZERO;
            for (LinhaDemonstrativo linha : linhas) {
                corrigido = corrigido.mais(linha.valorCorrigido());
                juros = juros.mais(linha.valorJuros());
            }
            return new Subtotal(linhas, corrigido, juros, corrigido.mais(juros));
        }
    }

    /**
     * @param aplicarArt523 inclui multa e honorários de 10% sobre o débito da penhora
     * @throws IllegalArgumentException se o demonstrativo já tiver multa ou honorários do
     *                                  art. 523, que aqui incidiriam sobre o débito todo
     */
    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento,
                                               boolean aplicarArt523) {
        Objects.requireNonNull(demonstrativo, "demonstrativo");
        Objects.requireNonNull(ajuizamento, "ajuizamento");
        if (!demonstrativo.multaArt523().equals(Dinheiro.ZERO)
                || !demonstrativo.honorariosArt523().equals(Dinheiro.ZERO)) {
            throw new IllegalArgumentException(
                    "Nos alimentos o art. 523 incide só sobre a penhora; calcule o demonstrativo sem ele");
        }

        // por posição: duas linhas iguais (mesmo valor e datas) são parcelas distintas
        List<LinhaDemonstrativo> linhas = demonstrativo.linhas();
        Set<Integer> anterioresNaPrisao = IntStream.range(0, linhas.size()).boxed()
                .filter(i -> !vencimento(linhas.get(i)).isAfter(ajuizamento))
                .sorted(Comparator.comparing((Integer i) -> vencimento(linhas.get(i))).reversed())
                .limit(PARCELAS_ANTERIORES_NA_PRISAO)
                .collect(Collectors.toSet());

        List<Rito> ritos = new ArrayList<>();
        List<LinhaDemonstrativo> prisao = new ArrayList<>();
        List<LinhaDemonstrativo> penhora = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            LinhaDemonstrativo linha = linhas.get(i);
            boolean noCurso = vencimento(linha).isAfter(ajuizamento);
            Rito rito = noCurso || anterioresNaPrisao.contains(i) ? Rito.PRISAO : Rito.PENHORA;
            ritos.add(rito);
            (rito == Rito.PRISAO ? prisao : penhora).add(linha);
        }

        Subtotal subtotalPrisao = Subtotal.de(prisao);
        Subtotal subtotalPenhora = Subtotal.de(penhora);
        Dinheiro debitoPenhora = subtotalPenhora.debitoAtualizado();
        Dinheiro multa = aplicarArt523 ? debitoPenhora.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;
        Dinheiro honorarios = aplicarArt523 ? debitoPenhora.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;
        Dinheiro total = subtotalPrisao.debitoAtualizado().mais(debitoPenhora).mais(multa).mais(honorarios);
        return new DemonstrativoPorRito(ajuizamento, ritos, subtotalPrisao, subtotalPenhora, multa, honorarios, total);
    }

    public Subtotal subtotal(Rito rito) {
        return switch (rito) {
            case PRISAO -> prisao;
            case PENHORA -> penhora;
        };
    }

    private static LocalDate vencimento(LinhaDemonstrativo linha) {
        return linha.parcela().inicioCorrecao();
    }
}
