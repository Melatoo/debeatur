package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static br.com.debeatur.core.gabarito.CasoGabarito.dinheiro;
import static br.com.debeatur.core.gabarito.CasoGabarito.numero;

/**
 * Cálculos feitos no CADEJ, o Sistema de Cálculo de Débitos Judiciais do TJMG que as
 * contadorias usam (Portaria 5.295/CGJ/2018), no módulo público "Cálculo Judicial
 * Simplificado" (versão 1.9.12S), em 24/10/2026.
 * <p>
 * Não são cálculos homologados nos autos: são simulações na ferramenta da contadoria, com
 * os valores transcritos da tela "Visualizar Cálculo". O módulo simplificado usa um único
 * índice e juros de taxa fixa por cálculo, então não cobre a virada da Lei 14.905/2024 nem a
 * taxa legal; esses casos ficam para os cálculos homologados ({@link CasosHomologados}).
 * <p>
 * O percentual de juros esperado é o número de meses mostrado pelo CADEJ vezes a taxa mensal
 * informada.
 */
final class CasosCadej {

    private static final String FONTE = "CADEJ/TJMG, Cálculo Judicial Simplificado 1.9.12S, simulado em 24/10/2026";

    private static final String FATOR = "O CADEJ parte do número-índice e o motor acumula as variações do SGS "
            + "com duas casas; o valor corrigido bate (docs/gabarito.md, divergência 1)";
    private static final String HONORARIOS = "O CADEJ calcula os honorários sobre o débito acrescido da multa; "
            + "o motor, sobre o débito (docs/gabarito.md, divergência 5)";

    private CasosCadej() {
    }

    static List<CasoGabarito> todos() {
        return List.of(
                icgjComJurosEArt523(),
                icgjVariasParcelasSemJuros(),
                ipcaComDataBaseNoMeioDoMes(),
                icgjVariasParcelasComArt523(),
                ipcaComJurosContratuais());
    }

    /** ICGJ + 1% a.m. desde o evento danoso, com multa e honorários de 10%. */
    private static CasoGabarito icgjComJurosEArt523() {
        return CasoGabarito.de("CADEJ 1: ICGJ, juros de 1% a.m. e art. 523", FONTE)
                .dataBase("2026-09-30")
                .regime(regime(Indice.INPC, "1"))
                .comArt523()
                .parcela(parcela("Danos morais", "10000.00", "2023-03-15", "2023-05-10"),
                        linha("1.1504243", "11504.24", "40", "4601.70", "16105.94"))
                .totais("16105.94", "1610.59", "1771.65", "19488.18")
                .divergenciaConhecida("fator de correção", FATOR)
                .divergenciaConhecida("honorários do art. 523", HONORARIOS)
                .divergenciaConhecida("total", HONORARIOS)
                .criar();
    }

    /** Sem data de início, o CADEJ não aplica juros (0 meses), mesmo com percentual informado. */
    private static CasoGabarito icgjVariasParcelasSemJuros() {
        return CasoGabarito.de("CADEJ 2: ICGJ, duas parcelas, sem juros", FONTE)
                .dataBase("2024-07-31")
                .regime(regime(Indice.INPC, "1"))
                .parcela(parcela("Danos materiais", "1234.56", "2019-07-20", null),
                        linha("1.3362614", "1649.69", "0", "0.00", "1649.69"))
                .parcela(parcela("Lucros cessantes", "789.01", "2021-01-31", null),
                        linha("1.2426223", "980.44", "0", "0.00", "980.44"))
                .totais("2630.13", "0.00", "0.00", "2630.13")
                .divergenciaConhecida("fator de correção", FATOR)
                .criar();
    }

    /**
     * Data-base em 15/06/2026: o CADEJ não usa pro rata e para no mês anterior (maio/2026).
     * O cálculo tem só a multa de 10%, sem honorários, o que o motor não modela (o art. 523
     * aplica os dois), então a comparação vai até o débito atualizado.
     */
    private static CasoGabarito ipcaComDataBaseNoMeioDoMes() {
        return CasoGabarito.de("CADEJ 3: IPCA, data-base no meio do mês", FONTE)
                .dataBase("2026-06-15")
                .regime(regime(Indice.IPCA, "1"))
                .parcela(parcela("Danos materiais", "5000.00", "2024-09-10", "2024-10-20"),
                        linha("1.0967002", "5483.50", "20", "1096.70", "6580.20"))
                .totais("6580.20", null, null, null)
                .divergenciaConhecida("fator de correção", FATOR)
                .criar();
    }

    /** Três parcelas com centavos quebrados e juros comuns desde a citação. */
    private static CasoGabarito icgjVariasParcelasComArt523() {
        return CasoGabarito.de("CADEJ 4: ICGJ, três parcelas, juros e art. 523", FONTE)
                .dataBase("2024-08-31")
                .regime(regime(Indice.INPC, "1"))
                .comArt523()
                .parcela(parcela("Conserto do veiculo", "3457.89", "2018-11-13", "2021-03-10"),
                        linha("1.3710796", "4741.04", "41", "1943.83", "6684.87"))
                .parcela(parcela("Despesas medicas", "612.35", "2019-02-27", "2021-03-10"),
                        linha("1.3676705", "837.49", "41", "343.37", "1180.86"))
                .parcela(parcela("Lucros cessantes", "1111.11", "2020-12-05", "2021-03-10"),
                        linha("1.2640430", "1404.49", "41", "575.84", "1980.33"))
                .totais("9846.06", "984.61", "1083.07", "11913.74")
                .divergenciaConhecida("fator de correção", FATOR)
                .divergenciaConhecida("honorários do art. 523", HONORARIOS)
                .divergenciaConhecida("total", HONORARIOS)
                .criar();
    }

    /** IPCA + 0,5% a.m. (juros contratuais fixados no título), sem art. 523. */
    private static CasoGabarito ipcaComJurosContratuais() {
        return CasoGabarito.de("CADEJ 5: IPCA, juros de 0,5% a.m.", FONTE)
                .dataBase("2026-09-30")
                .regime(regime(Indice.IPCA, "0.5"))
                .parcela(parcela("Saldo contratual", "25000.00", "2025-02-01", "2025-02-01"),
                        linha("1.0733096", "26832.74", "9.5", "2549.11", "29381.85"))
                .totais("29381.85", "0.00", "0.00", "29381.85")
                .divergenciaConhecida("fator de correção", FATOR)
                .criar();
    }

    /** O ICGJ é o INPC; o motor usa o INPC do SGS no lugar da tabela da CGJ. */
    private static RegimeAtualizacao regime(Indice correcao, String jurosMensais) {
        return new RegimeAtualizacao(correcao + " + " + jurosMensais + "% a.m.", List.of(
                new RegraPeriodo(YearMonth.of(2015, 1), null, correcao,
                        new Juros.TaxaFixa(new BigDecimal(jurosMensais)))));
    }

    private static Parcela parcela(String descricao, String valor, String inicioCorrecao, String inicioJuros) {
        return new Parcela(descricao, Dinheiro.de(valor), LocalDate.parse(inicioCorrecao),
                inicioJuros == null ? null : LocalDate.parse(inicioJuros));
    }

    private static CasoGabarito.LinhaEsperada linha(String fator, String corrigido, String percentualJuros,
                                                    String juros, String subtotal) {
        return new CasoGabarito.LinhaEsperada(numero(fator), dinheiro(corrigido), numero(percentualJuros),
                dinheiro(juros), dinheiro(subtotal));
    }
}
