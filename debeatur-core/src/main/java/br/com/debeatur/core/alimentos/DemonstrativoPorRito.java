package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
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
 * A separação não altera nenhum valor: classifica as linhas já calculadas pelo vencimento da
 * parcela ({@link br.com.debeatur.core.calculo.Parcela#inicioCorrecao()}) e soma cada grupo.
 * Os dois subtotais somam exatamente o débito atualizado do demonstrativo; multa e honorários
 * do art. 523 continuam no demonstrativo.
 * <p>
 * Convenções:
 * <ul>
 *   <li>Vão para a prisão as três parcelas de vencimento mais recente até o ajuizamento,
 *       inclusive a que vence no próprio dia, e todas as que vencem depois dele.</li>
 *   <li>Conta-se por parcela, não por saldo: uma parcela quitada entre as três últimas ocupa o
 *       lugar dela, com saldo zero, e não puxa uma parcela mais antiga para a prisão.</li>
 * </ul>
 *
 * @param ajuizamento data do ajuizamento da execução
 */
public record DemonstrativoPorRito(LocalDate ajuizamento, Subtotal prisao, Subtotal penhora) {

    /** Parcelas anteriores ao ajuizamento que podem ser cobradas sob pena de prisão. */
    public static final int PARCELAS_ANTERIORES_NA_PRISAO = 3;

    public DemonstrativoPorRito {
        Objects.requireNonNull(ajuizamento, "ajuizamento");
        Objects.requireNonNull(prisao, "prisao");
        Objects.requireNonNull(penhora, "penhora");
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

    public static DemonstrativoPorRito separar(Demonstrativo demonstrativo, LocalDate ajuizamento) {
        Objects.requireNonNull(demonstrativo, "demonstrativo");
        Objects.requireNonNull(ajuizamento, "ajuizamento");

        // por posição: duas linhas iguais (mesmo valor e datas) são parcelas distintas
        List<LinhaDemonstrativo> linhas = demonstrativo.linhas();
        Set<Integer> anterioresNaPrisao = IntStream.range(0, linhas.size()).boxed()
                .filter(i -> !vencimento(linhas.get(i)).isAfter(ajuizamento))
                .sorted(Comparator.comparing((Integer i) -> vencimento(linhas.get(i))).reversed())
                .limit(PARCELAS_ANTERIORES_NA_PRISAO)
                .collect(Collectors.toSet());

        List<LinhaDemonstrativo> prisao = new ArrayList<>();
        List<LinhaDemonstrativo> penhora = new ArrayList<>();
        for (int i = 0; i < linhas.size(); i++) {
            LinhaDemonstrativo linha = linhas.get(i);
            boolean noCurso = vencimento(linha).isAfter(ajuizamento);
            (noCurso || anterioresNaPrisao.contains(i) ? prisao : penhora).add(linha);
        }
        return new DemonstrativoPorRito(ajuizamento, Subtotal.de(prisao), Subtotal.de(penhora));
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
