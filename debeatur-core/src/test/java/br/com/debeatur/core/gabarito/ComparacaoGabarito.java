package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Compara o demonstrativo do motor com o documento de origem e descreve cada divergência. */
final class ComparacaoGabarito {

    private ComparacaoGabarito() {
    }

    /**
     * O que faz o caso falhar: divergências sem causa registrada e divergências conhecidas que
     * deixaram de ocorrer (o registro ficou desatualizado).
     */
    static List<String> problemas(CasoGabarito caso, Demonstrativo demonstrativo) {
        List<String> divergencias = divergencias(caso, demonstrativo);
        List<String> problemas = new ArrayList<>();
        for (String divergencia : divergencias) {
            if (caso.divergenciasConhecidas().stream().noneMatch(c -> c.explica(divergencia))) {
                problemas.add(divergencia);
            }
        }
        for (CasoGabarito.DivergenciaConhecida conhecida : caso.divergenciasConhecidas()) {
            if (divergencias.stream().noneMatch(conhecida::explica)) {
                problemas.add("divergência conhecida não ocorreu mais em " + conhecida.campo()
                        + " (" + conhecida.motivo() + ")");
            }
        }
        return problemas;
    }

    static List<String> divergencias(CasoGabarito caso, Demonstrativo demonstrativo) {
        List<String> divergencias = new ArrayList<>();
        Dinheiro tolerancia = caso.tolerancia();

        for (int i = 0; i < caso.linhas().size(); i++) {
            CasoGabarito.LinhaEsperada esperada = caso.linhas().get(i);
            LinhaDemonstrativo obtida = demonstrativo.linhas().get(i);
            String parcela = "parcela " + (i + 1) + " (" + obtida.parcela().descricao() + ")";

            comparar(divergencias, parcela + ", fator de correção", esperada.fatorCorrecao(), obtida.fatorCorrecao());
            comparar(divergencias, parcela + ", valor corrigido", esperada.valorCorrigido(), obtida.valorCorrigido(), tolerancia);
            comparar(divergencias, parcela + ", percentual de juros", esperada.percentualJuros(), obtida.percentualJuros());
            comparar(divergencias, parcela + ", juros", esperada.valorJuros(), obtida.valorJuros(), tolerancia);
            comparar(divergencias, parcela + ", subtotal", esperada.subtotal(), obtida.subtotal(), tolerancia);
        }

        CasoGabarito.Totais totais = caso.totais();
        comparar(divergencias, "débito atualizado", totais.debitoAtualizado(), demonstrativo.debitoAtualizado(), tolerancia);
        comparar(divergencias, "multa do art. 523", totais.multaArt523(), demonstrativo.multaArt523(), tolerancia);
        comparar(divergencias, "honorários do art. 523", totais.honorariosArt523(), demonstrativo.honorariosArt523(), tolerancia);
        comparar(divergencias, "total", totais.total(), demonstrativo.total(), tolerancia);
        return divergencias;
    }

    private static void comparar(List<String> divergencias, String campo,
                                 Dinheiro esperado, Dinheiro obtido, Dinheiro tolerancia) {
        if (esperado == null) {
            return;
        }
        BigDecimal diferenca = obtido.valor().subtract(esperado.valor());
        if (diferenca.abs().compareTo(tolerancia.valor()) > 0) {
            divergencias.add("%s: esperado %s, motor %s (diferença %s)".formatted(
                    campo, esperado, obtido, new Dinheiro(diferenca)));
        }
    }

    /** Fatores e percentuais são comparados na precisão em que o documento os imprimiu. */
    private static void comparar(List<String> divergencias, String campo, BigDecimal esperado, BigDecimal obtido) {
        if (esperado == null) {
            return;
        }
        BigDecimal arredondado = obtido.setScale(Math.max(esperado.scale(), 0), RoundingMode.HALF_UP);
        if (arredondado.compareTo(esperado) != 0) {
            divergencias.add("%s: esperado %s, motor %s (%s sem arredondar)".formatted(
                    campo, esperado.toPlainString(), arredondado.toPlainString(),
                    obtido.stripTrailingZeros().toPlainString()));
        }
    }
}
