package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.alimentos.Rito;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.regime.RegimeAtualizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @param ritos nulo se o cálculo não separa os ritos; se separa, multa, honorários e total
 *              são os do {@link DemonstrativoPorRito}, com o art. 523 só sobre a penhora
 */
public record DemonstrativoResponse(
        LocalDate dataBase,
        RegimeDto regime,
        List<Linha> linhas,
        BigDecimal totalCorrigido,
        BigDecimal totalJuros,
        BigDecimal debitoAtualizado,
        BigDecimal multaArt523,
        BigDecimal honorariosArt523,
        BigDecimal total,
        Ritos ritos) {

    /**
     * @param rito   nulo se o cálculo não separa os ritos
     * @param pensao de onde veio a parcela da pensão; nulo nas parcelas avulsas
     */
    public record Linha(
            String descricao,
            BigDecimal valorOriginal,
            LocalDate inicioCorrecao,
            LocalDate inicioJuros,
            BigDecimal fatorCorrecao,
            BigDecimal valorCorrigido,
            BigDecimal percentualJuros,
            BigDecimal valorJuros,
            BigDecimal subtotal,
            Rito rito,
            MesPensao pensao) {

        static Linha de(LinhaDemonstrativo l, Rito rito, MesPensao pensao) {
            return new Linha(l.parcela().descricao(), l.parcela().valor().valor(),
                    l.parcela().inicioCorrecao(), l.parcela().inicioJuros(),
                    l.fatorCorrecao(), l.valorCorrigido().valor(),
                    l.percentualJuros(), l.valorJuros().valor(), l.subtotal().valor(), rito, pensao);
        }
    }

    /**
     * Memória de uma parcela da pensão: o devido é o percentual do salário mínimo vigente no
     * mês, e o valor original da linha é o devido menos o pago.
     */
    public record MesPensao(YearMonth mes, BigDecimal salarioMinimo, BigDecimal valorDevido, BigDecimal valorPago) {
    }

    /** @param totalPenhora débito da penhora mais multa e honorários do art. 523 */
    public record Ritos(LocalDate ajuizamento, Subtotal prisao, Subtotal penhora, BigDecimal totalPenhora) {

        static Ritos de(DemonstrativoPorRito porRito) {
            Dinheiro totalPenhora = porRito.penhora().debitoAtualizado()
                    .mais(porRito.multaArt523()).mais(porRito.honorariosArt523());
            return new Ritos(porRito.ajuizamento(), Subtotal.de(porRito.prisao()), Subtotal.de(porRito.penhora()),
                    totalPenhora.valor());
        }
    }

    public record Subtotal(int parcelas, BigDecimal totalCorrigido, BigDecimal totalJuros, BigDecimal debitoAtualizado) {

        static Subtotal de(DemonstrativoPorRito.Subtotal s) {
            return new Subtotal(s.linhas().size(), s.totalCorrigido().valor(), s.totalJuros().valor(),
                    s.debitoAtualizado().valor());
        }
    }

    /**
     * @param memoria memória de cada parcela da pensão, indexada pela parcela que o motor
     *                repassa à linha; as parcelas avulsas não estão nela
     */
    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime, Map<Parcela, MesPensao> memoria) {
        List<Linha> linhas = new ArrayList<>();
        for (LinhaDemonstrativo linha : d.linhas()) {
            linhas.add(Linha.de(linha, null, memoria.get(linha.parcela())));
        }
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime), linhas,
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                d.multaArt523().valor(), d.honorariosArt523().valor(), d.total().valor(), null);
    }

    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime, Map<Parcela, MesPensao> memoria,
                                    DemonstrativoPorRito porRito) {
        List<Linha> linhas = new ArrayList<>();
        for (int i = 0; i < d.linhas().size(); i++) {
            LinhaDemonstrativo linha = d.linhas().get(i);
            linhas.add(Linha.de(linha, porRito.ritoDasLinhas().get(i), memoria.get(linha.parcela())));
        }
        Ritos ritos = Ritos.de(porRito);
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime), linhas,
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                porRito.multaArt523().valor(), porRito.honorariosArt523().valor(), porRito.total().valor(), ritos);
    }
}
