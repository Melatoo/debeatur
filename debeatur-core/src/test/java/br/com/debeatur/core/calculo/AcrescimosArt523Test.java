package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcrescimosArt523Test {

    @Test
    void honorariosSobreODebitoValemOMesmoQueAMulta() {
        AcrescimosArt523 art523 = AcrescimosArt523.sobre(Dinheiro.de("16105.94"), BaseHonorarios.DEBITO);

        assertThat(art523.multa()).isEqualTo(Dinheiro.de("1610.59"));
        assertThat(art523.honorarios()).isEqualTo(Dinheiro.de("1610.59"));
        assertThat(art523.total()).isEqualTo(Dinheiro.de("3221.18"));
    }

    @Test
    void honorariosSobreODebitoComMultaPartemDaMultaArredondada() {
        // 16.105,94 + 1.610,59 = 17.716,53; 10% = 1.771,653 → 1.771,65 (CADEJ, caso 1)
        AcrescimosArt523 art523 = AcrescimosArt523.sobre(Dinheiro.de("16105.94"), BaseHonorarios.DEBITO_COM_MULTA);

        assertThat(art523.multa()).isEqualTo(Dinheiro.de("1610.59"));
        assertThat(art523.honorarios()).isEqualTo(Dinheiro.de("1771.65"));
    }

    @Test
    void semArt523NaoHaAcrescimos() {
        assertThat(AcrescimosArt523.se(false, Dinheiro.de("100.00"), BaseHonorarios.DEBITO_COM_MULTA))
                .isEqualTo(AcrescimosArt523.NENHUM);
    }
}
