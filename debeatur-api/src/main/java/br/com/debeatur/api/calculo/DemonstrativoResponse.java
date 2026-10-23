package br.com.debeatur.api.calculo;

import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DemonstrativoResponse(
        LocalDate dataBase,
        String regime,
        List<Linha> linhas,
        BigDecimal totalCorrigido,
        BigDecimal totalJuros,
        BigDecimal debitoAtualizado,
        BigDecimal multaArt523,
        BigDecimal honorariosArt523,
        BigDecimal total) {

    public record Linha(
            String descricao,
            BigDecimal valorOriginal,
            LocalDate inicioCorrecao,
            LocalDate inicioJuros,
            BigDecimal fatorCorrecao,
            BigDecimal valorCorrigido,
            BigDecimal percentualJuros,
            BigDecimal valorJuros,
            BigDecimal subtotal) {

        static Linha de(LinhaDemonstrativo l) {
            return new Linha(l.parcela().descricao(), l.parcela().valor().valor(),
                    l.parcela().inicioCorrecao(), l.parcela().inicioJuros(),
                    l.fatorCorrecao(), l.valorCorrigido().valor(),
                    l.percentualJuros(), l.valorJuros().valor(), l.subtotal().valor());
        }
    }

    static DemonstrativoResponse de(Demonstrativo d) {
        return new DemonstrativoResponse(d.dataBase(), d.regime(),
                d.linhas().stream().map(Linha::de).toList(),
                d.totalCorrigido().valor(), d.totalJuros().valor(), d.debitoAtualizado().valor(),
                d.multaArt523().valor(), d.honorariosArt523().valor(), d.total().valor());
    }
}
