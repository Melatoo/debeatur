package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * Roda o motor contra cada caso de gabarito e lista todas as divergências do caso de uma vez,
 * para registrar qual convenção provisória as causou.
 */
class GabaritoHomologadoTest {

    @TestFactory
    Stream<DynamicTest> motorReproduzOsCalculosHomologados() {
        return testes(CasosHomologados.todos(), "Sem cálculos homologados em CasosHomologados (#2)");
    }

    @TestFactory
    Stream<DynamicTest> motorReproduzOCadej() {
        return testes(CasosCadej.todos(), "Sem casos em CasosCadej");
    }

    private static Stream<DynamicTest> testes(List<CasoGabarito> casos, String semCasos) {
        if (casos.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest("nenhum caso cadastrado", () -> abort(semCasos)));
        }
        return casos.stream().map(caso -> DynamicTest.dynamicTest(caso.nome(), () -> {
            Demonstrativo demonstrativo = new MotorCalculo(caso.serie()).calcular(caso.parametros());

            assertThat(ComparacaoGabarito.problemas(caso, demonstrativo))
                    .as("Divergências com %s", caso.fonte())
                    .isEmpty();
        }));
    }
}
