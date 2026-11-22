package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.AcrescimosArt523;
import br.com.debeatur.core.calculo.BaseHonorarios;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;

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
 *   <li>Quais parcelas vão para a prisão depende da {@link RegraRitos regra}. Pela lei, as três
 *       de vencimento mais recente até o ajuizamento, inclusive a que vence no próprio dia, e
 *       todas as que vencem depois dele. Pela planilha da DPMG, as três de vencimento mais
 *       recente do cálculo. Os métodos sem a regra seguem a lei.</li>
 *   <li>Conta-se por parcela, não por saldo: uma parcela quitada entre as três últimas ocupa o
 *       lugar dela, com saldo zero, e não puxa uma parcela mais antiga para a prisão.</li>
 *   <li>Os {@link Abatimento abatimentos} saem do débito da penhora pelo valor nominal, como na
 *       planilha da DPMG, que os põe na planilha desse rito; multa e honorários incidem sobre o
 *       saldo. O que passar do débito da penhora sai do da prisão, que não tem acréscimos.</li>
 * </ul>
 *
 * @param ajuizamento      data do ajuizamento da execução
 * @param ritoDasLinhas    rito de cada linha do demonstrativo, na mesma ordem
 * @param abatimentos      valores recebidos, na ordem informada
 * @param abatidoPenhora   parte dos abatimentos que saiu do débito da penhora
 * @param abatidoPrisao    parte dos abatimentos que passou do débito da penhora e saiu do da prisão
 * @param multaArt523      10% do saldo da penhora, ou zero
 * @param honorariosArt523 10% do saldo da penhora (ou dele mais a multa), ou zero
 * @param total            saldo dos dois ritos mais multa e honorários
 */
public record DemonstrativoPorRito(LocalDate ajuizamento, List<Rito> ritoDasLinhas, Subtotal prisao,
                                   Subtotal penhora, List<Abatimento> abatimentos, Dinheiro abatidoPenhora,
                                   Dinheiro abatidoPrisao, Dinheiro multaArt523, Dinheiro honorariosArt523,
                                   Dinheiro total) {

    /** Parcelas que vão para a prisão: as últimas até o ajuizamento (lei) ou as últimas do cálculo (DPMG). */
    public static final int PARCELAS_ANTERIORES_NA_PRISAO = 3;

    public DemonstrativoPorRito {
        Objects.requireNonNull(ajuizamento, "ajuizamento");
        ritoDasLinhas = List.copyOf(ritoDasLinhas);
        Objects.requireNonNull(prisao, "prisao");
        Objects.requireNonNull(penhora, "penhora");
        abatimentos = List.copyOf(abatimentos);
        Objects.requireNonNull(abatidoPenhora, "abatidoPenhora");
        Objects.requireNonNull(abatidoPrisao, "abatidoPrisao");
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

    /** Separa os ritos sem abatimentos. */
    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento,
                                               boolean aplicarArt523) {
        return separar(demonstrativo, ajuizamento, aplicarArt523, List.of());
    }

    /**
     * @param aplicarArt523 inclui multa e honorários de 10% sobre o saldo da penhora
     * @param abatimentos   valores recebidos, que saem primeiro da penhora
     * @throws IllegalArgumentException se o demonstrativo já tiver multa ou honorários do
     *                                  art. 523, que aqui incidiriam sobre o débito todo, ou se
     *                                  os abatimentos superarem o débito dos dois ritos
     */
    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento,
                                               boolean aplicarArt523, List<Abatimento> abatimentos) {
        return separar(demonstrativo, ajuizamento, aplicarArt523, abatimentos, BaseHonorarios.DEBITO);
    }

    /**
     * @param baseHonorarios sobre o que incidem os honorários da penhora
     * @throws IllegalArgumentException nos mesmos casos de
     *                                  {@link #separar(Demonstrativo, LocalDate, boolean, List)}
     */
    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento,
                                               boolean aplicarArt523, List<Abatimento> abatimentos,
                                               BaseHonorarios baseHonorarios) {
        return separar(demonstrativo, ajuizamento, RegraRitos.LEI, aplicarArt523, abatimentos, baseHonorarios);
    }

    /**
     * @param regra quais parcelas vão para a prisão; com {@link RegraRitos#ULTIMOS_MESES_DO_CALCULO},
     *              o ajuizamento não muda a separação
     * @throws IllegalArgumentException nos mesmos casos de
     *                                  {@link #separar(Demonstrativo, LocalDate, boolean, List)}
     */
    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento, RegraRitos regra,
                                               boolean aplicarArt523, List<Abatimento> abatimentos,
                                               BaseHonorarios baseHonorarios) {
        Objects.requireNonNull(demonstrativo, "demonstrativo");
        Objects.requireNonNull(ajuizamento, "ajuizamento");
        Objects.requireNonNull(regra, "regra");
        Objects.requireNonNull(abatimentos, "abatimentos");
        if (!demonstrativo.multaArt523().equals(Dinheiro.ZERO)
                || !demonstrativo.honorariosArt523().equals(Dinheiro.ZERO)) {
            throw new IllegalArgumentException(
                    "Nos alimentos o art. 523 incide só sobre a penhora; calcule o demonstrativo sem ele");
        }

        // Pela lei, contam só as anteriores ao ajuizamento, e as do curso do processo vão todas
        // para a prisão; pela DPMG, contam as três últimas do cálculo, seja qual for o ajuizamento.
        boolean pelaLei = regra == RegraRitos.LEI;
        // por posição: duas linhas iguais (mesmo valor e datas) são parcelas distintas
        List<LinhaDemonstrativo> linhas = demonstrativo.linhas();
        Set<Integer> ultimasNaPrisao = IntStream.range(0, linhas.size()).boxed()
                .filter(i -> !pelaLei || !vencimento(linhas.get(i)).isAfter(ajuizamento))
                .sorted(Comparator.comparing((Integer i) -> vencimento(linhas.get(i))).reversed())
                .limit(PARCELAS_ANTERIORES_NA_PRISAO)
                .collect(Collectors.toSet());

        List<Rito> ritos = new ArrayList<>();
        List<LinhaDemonstrativo> prisao = new ArrayList<>();
        List<LinhaDemonstrativo> penhora = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            LinhaDemonstrativo linha = linhas.get(i);
            boolean noCurso = pelaLei && vencimento(linha).isAfter(ajuizamento);
            Rito rito = noCurso || ultimasNaPrisao.contains(i) ? Rito.PRISAO : Rito.PENHORA;
            ritos.add(rito);
            (rito == Rito.PRISAO ? prisao : penhora).add(linha);
        }

        Subtotal subtotalPrisao = Subtotal.de(prisao);
        Subtotal subtotalPenhora = Subtotal.de(penhora);
        Dinheiro debitoPenhora = subtotalPenhora.debitoAtualizado();
        Dinheiro debitoPrisao = subtotalPrisao.debitoAtualizado();
        Dinheiro abatido = Abatimento.total(abatimentos);
        if (abatido.compareTo(debitoPenhora.mais(debitoPrisao)) > 0) {
            throw new IllegalArgumentException("Os abatimentos (" + abatido + ") superam o débito atualizado ("
                    + debitoPenhora.mais(debitoPrisao) + ")");
        }
        Dinheiro abatidoPenhora = abatido.compareTo(debitoPenhora) > 0 ? debitoPenhora : abatido;
        Dinheiro abatidoPrisao = abatido.menos(abatidoPenhora);
        Dinheiro saldoPenhora = debitoPenhora.menos(abatidoPenhora);
        AcrescimosArt523 art523 = AcrescimosArt523.se(aplicarArt523, saldoPenhora, baseHonorarios);
        Dinheiro total = debitoPrisao.menos(abatidoPrisao).mais(saldoPenhora).mais(art523.total());
        return new DemonstrativoPorRito(ajuizamento, ritos, subtotalPrisao, subtotalPenhora, abatimentos,
                abatidoPenhora, abatidoPrisao, art523.multa(), art523.honorarios(), total);
    }

    /** Saldo da penhora mais multa e honorários do art. 523: o que se cobra nesse rito. */
    public Dinheiro totalPenhora() {
        return penhora.debitoAtualizado().menos(abatidoPenhora).mais(multaArt523).mais(honorariosArt523);
    }

    /** Débito da prisão menos o que dos abatimentos passou da penhora: o que se cobra nesse rito. */
    public Dinheiro totalPrisao() {
        return prisao.debitoAtualizado().menos(abatidoPrisao);
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
