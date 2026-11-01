package br.com.debeatur.api.calculo;

import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.alimentos.Rito;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.regime.RegimeAtualizacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** @param rito nulo se o cálculo não separa os ritos */
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
            Rito rito) {

        static Linha de(LinhaDemonstrativo l, Rito rito) {
            return new Linha(l.parcela().descricao(), l.parcela().valor().valor(),
                    l.parcela().inicioCorrecao(), l.parcela().inicioJuros(),
                    l.fatorCorrecao(), l.valorCorrigido().valor(),
                    l.percentualJuros(), l.valorJuros().valor(), l.subtotal().valor(), rito);
        }
    }

    public record Ritos(LocalDate ajuizamento, Subtotal prisao, Subtotal penhora) {
    }

    public record Subtotal(int parcelas, BigDecimal totalCorrigido, BigDecimal totalJuros, BigDecimal debitoAtualizado) {

        static Subtotal de(DemonstrativoPorRito.Subtotal s) {
            return new Subtotal(s.linhas().size(), s.totalCorrigido().valor(), s.totalJuros().valor(),
                    s.debitoAtualizado().valor());
        }
    }

    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime) {
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime),
                d.linhas().stream().map(l -> Linha.de(l, null)).toList(),
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                d.multaArt523().valor(), d.honorariosArt523().valor(), d.total().valor(), null);
    }

    static DemonstrativoResponse de(Demonstrativo d, RegimeAtualizacao regime, DemonstrativoPorRito porRito) {
        List<Linha> linhas = new ArrayList<>();
        for (int i = 0; i < d.linhas().size(); i++) {
            linhas.add(Linha.de(d.linhas().get(i), porRito.ritoDasLinhas().get(i)));
        }
        Ritos ritos = new Ritos(porRito.ajuizamento(), Subtotal.de(porRito.prisao()), Subtotal.de(porRito.penhora()));
        return new DemonstrativoResponse(d.dataBase(), RegimeDto.de(regime), linhas,
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                porRito.multaArt523().valor(), porRito.honorariosArt523().valor(), porRito.total().valor(), ritos);
    }
}
