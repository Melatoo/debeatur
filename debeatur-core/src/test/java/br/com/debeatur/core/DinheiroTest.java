package br.com.debeatur.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DinheiroTest {

    @Test
    void subtraiEmDuasCasas() {
        assertThat(Dinheiro.de("423.60").menos(Dinheiro.de("200.005"))).isEqualTo(Dinheiro.de("223.59"));
        assertThat(Dinheiro.de("10.00").menos(Dinheiro.de("10.00"))).isEqualTo(Dinheiro.ZERO);
    }
}
