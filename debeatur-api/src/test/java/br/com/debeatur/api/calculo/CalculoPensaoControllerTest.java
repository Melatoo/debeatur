package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.indices.SgsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pensão em percentual do salário mínimo pela API. Índices fictícios e sem juros: o que se
 * testa é a geração das parcelas e o abatimento chegarem ao motor, que têm gabarito no core.
 */
@WebMvcTest(CalculoController.class)
@Import(CalculoService.class)
class CalculoPensaoControllerTest {

    private static final String SEM_JUROS = """
            {"regras": [{"inicio": "2023-01", "correcao": "INPC", "juros": "SEM_JUROS"}]}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SgsClient sgs;

    @BeforeEach
    void indicesZerados() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        for (YearMonth mes = YearMonth.of(2023, 12); mes.isBefore(YearMonth.of(2024, 4)); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, "0");
        }
        when(sgs.carregar(any(), any(), any())).thenReturn(serie);
    }

    private MockMvcTester.MockMvcRequestBuilder calcular(String json) {
        return mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String requisicao(String pensao, String parcelas) {
        return """
                {"dataBase": "2024-03-15", "aplicarArt523": false, "regime": %s,
                 "pensao": %s%s}
                """.formatted(SEM_JUROS, pensao, parcelas == null ? "" : ", \"parcelas\": " + parcelas);
    }

    @Test
    void geraAsParcelasDaPensaoEAbateOsPagamentos() {
        when(sgs.carregarSalarioMinimo(YearMonth.of(2023, 12), YearMonth.of(2024, 2)))
                .thenReturn(new SalarioMinimoEmMemoria()
                        .com(YearMonth.of(2023, 12), "1320.00")
                        .com(YearMonth.of(2024, 1), "1412.00")
                        .com(YearMonth.of(2024, 2), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2023-12", "ultimoMes": "2024-02",
                 "inicioJuros": "VENCIMENTO", "pagamentos": [{"mes": "2024-02", "valor": 200.00}]}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.linhas", v -> v.assertThat().asArray().hasSize(3))
                .hasPathSatisfying("$.linhas[0].descricao", v -> v.assertThat().isEqualTo("Alimentos 12/2023"))
                .hasPathSatisfying("$.linhas[0].valorOriginal", v -> v.assertThat().asNumber().isEqualTo(396.0))
                .hasPathSatisfying("$.linhas[0].inicioCorrecao", v -> v.assertThat().isEqualTo("2023-12-10"))
                .hasPathSatisfying("$.linhas[1].valorOriginal", v -> v.assertThat().asNumber().isEqualTo(423.6))
                .hasPathSatisfying("$.linhas[2].descricao",
                        v -> v.assertThat().isEqualTo("Alimentos 02/2024 (saldo após pagamento parcial)"))
                .hasPathSatisfying("$.linhas[2].valorOriginal", v -> v.assertThat().asNumber().isEqualTo(223.6))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(1043.2));
    }

    @Test
    void parcelasAvulsasVemDepoisDasDaPensao() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 1), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "CITACAO", "citacao": "2024-02-05"}
                """;
        String avulsa = """
                [{"descricao": "Despesa escolar", "valor": 150.00, "inicioCorrecao": "2024-02-01"}]
                """;

        assertThat(calcular(requisicao(pensao, avulsa))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.linhas[0].descricao", v -> v.assertThat().isEqualTo("Alimentos 01/2024"))
                .hasPathSatisfying("$.linhas[0].inicioJuros", v -> v.assertThat().isEqualTo("2024-02-05"))
                .hasPathSatisfying("$.linhas[1].descricao", v -> v.assertThat().isEqualTo("Despesa escolar"))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(573.6));
    }

    @Test
    void comAjuizamentoSeparaOsRitosEAplicaOArt523SoNaPenhora() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        for (YearMonth mes = YearMonth.of(2023, 10); mes.isBefore(YearMonth.of(2024, 3)); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, "0");
        }
        when(sgs.carregar(any(), any(), any())).thenReturn(serie);
        when(sgs.carregarSalarioMinimo(any(), any())).thenReturn(new SalarioMinimoEmMemoria()
                .com(YearMonth.of(2023, 10), "1320.00")
                .com(YearMonth.of(2023, 11), "1320.00")
                .com(YearMonth.of(2023, 12), "1320.00")
                .com(YearMonth.of(2024, 1), "1412.00")
                .com(YearMonth.of(2024, 2), "1412.00"));
        String json = """
                {"dataBase": "2024-03-15", "aplicarArt523": true, "regime": %s,
                 "pensao": {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2023-10", "ultimoMes": "2024-02",
                            "inicioJuros": "VENCIMENTO", "ajuizamento": "2024-02-01"}}
                """.formatted(SEM_JUROS);

        // prisão: 11/2023, 12/2023, 01/2024 (as três anteriores) e 02/2024 (no curso) = 1639.20
        // penhora: 10/2023 = 396.00; multa e honorários de 10% = 39.60 cada
        assertThat(calcular(json)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.linhas[0].rito", v -> v.assertThat().isEqualTo("PENHORA"))
                .hasPathSatisfying("$.linhas[1].rito", v -> v.assertThat().isEqualTo("PRISAO"))
                .hasPathSatisfying("$.linhas[4].rito", v -> v.assertThat().isEqualTo("PRISAO"))
                .hasPathSatisfying("$.ritos.ajuizamento", v -> v.assertThat().isEqualTo("2024-02-01"))
                .hasPathSatisfying("$.ritos.prisao.parcelas", v -> v.assertThat().asNumber().isEqualTo(4))
                .hasPathSatisfying("$.ritos.prisao.debitoAtualizado", v -> v.assertThat().asNumber().isEqualTo(1639.2))
                .hasPathSatisfying("$.ritos.penhora.parcelas", v -> v.assertThat().asNumber().isEqualTo(1))
                .hasPathSatisfying("$.ritos.penhora.debitoAtualizado", v -> v.assertThat().asNumber().isEqualTo(396.0))
                .hasPathSatisfying("$.debitoAtualizado", v -> v.assertThat().asNumber().isEqualTo(2035.2))
                .hasPathSatisfying("$.multaArt523", v -> v.assertThat().asNumber().isEqualTo(39.6))
                .hasPathSatisfying("$.honorariosArt523", v -> v.assertThat().asNumber().isEqualTo(39.6))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(2114.4));
    }

    @Test
    void comAjuizamentoRecusaDuasParcelasNoMesmoMes() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 1), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "VENCIMENTO", "ajuizamento": "2024-02-01"}
                """;
        String avulsa = """
                [{"descricao": "Despesa escolar", "valor": 150.00, "inicioCorrecao": "2024-01-20"}]
                """;

        assertThat(calcular(requisicao(pensao, avulsa))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString()
                        .contains("no máximo uma parcela por mês de vencimento").contains("2024-01"));
    }

    @Test
    void semAjuizamentoAceitaDuasParcelasNoMesmoMes() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 1), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "VENCIMENTO"}
                """;
        String avulsa = """
                [{"descricao": "Despesa escolar", "valor": 150.00, "inicioCorrecao": "2024-01-20"}]
                """;

        assertThat(calcular(requisicao(pensao, avulsa))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.linhas", v -> v.assertThat().asArray().hasSize(2));
    }

    @Test
    void semAjuizamentoNaoSeparaOsRitos() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 1), "1412.00"));
        String json = """
                {"dataBase": "2024-03-15", "aplicarArt523": true, "regime": %s,
                 "pensao": {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                            "inicioJuros": "VENCIMENTO"}}
                """.formatted(SEM_JUROS);

        assertThat(calcular(json)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.ritos", v -> v.assertThat().isNull())
                .hasPathSatisfying("$.linhas[0].rito", v -> v.assertThat().isNull())
                .hasPathSatisfying("$.multaArt523", v -> v.assertThat().asNumber().isEqualTo(42.36));
    }

    @Test
    void recusaPensaoQueVaiAlemDaDataBase() {
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-04",
                 "inicioJuros": "VENCIMENTO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("vence em 2024-04-10, depois da data-base (2024-03-15)"));
        verify(sgs, never()).carregarSalarioMinimo(any(), any());
    }

    @Test
    void recusaPensaoNoMesDaDataBaseQueVenceDepoisDela() {
        String pensao = """
                {"percentual": 30, "diaVencimento": 20, "primeiroMes": "2024-01", "ultimoMes": "2024-03",
                 "inicioJuros": "VENCIMENTO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail",
                        v -> v.assertThat().asString().contains("vence em 2024-03-20, depois da data-base (2024-03-15)"));
        verify(sgs, never()).carregarSalarioMinimo(any(), any());
    }

    @Test
    void aceitaPensaoNoMesDaDataBaseQueVenceAteEla() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 3), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 15, "primeiroMes": "2024-03", "ultimoMes": "2024-03",
                 "inicioJuros": "VENCIMENTO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.linhas[0].inicioCorrecao", v -> v.assertThat().isEqualTo("2024-03-15"))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(423.6));
    }

    @Test
    void recusaJurosDesdeACitacaoSemACitacao() {
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-02",
                 "inicioJuros": "CITACAO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("citação"));
    }

    @Test
    void recusaPagamentoMaiorQueAParcela() {
        when(sgs.carregarSalarioMinimo(any(), any()))
                .thenReturn(new SalarioMinimoEmMemoria().com(YearMonth.of(2024, 1), "1412.00"));
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "VENCIMENTO", "pagamentos": [{"mes": "2024-01", "valor": 500.00}]}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("maior que a parcela"));
    }

    @Test
    void salarioMinimoIndisponivelDevolve422() {
        when(sgs.carregarSalarioMinimo(any(), any())).thenReturn(new SalarioMinimoEmMemoria());
        String pensao = """
                {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "VENCIMENTO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(422).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("Salário mínimo indisponível"));
    }

    @Test
    void recusaPercentualEDiaInvalidos() {
        String pensao = """
                {"percentual": 0, "diaVencimento": 32, "primeiroMes": "2024-01", "ultimoMes": "2024-01",
                 "inicioJuros": "VENCIMENTO"}
                """;

        assertThat(calcular(requisicao(pensao, null))).hasStatus(400);
    }

    @Test
    void recusaCalculoSemPensaoNemParcelas() {
        assertThat(calcular("""
                {"dataBase": "2024-03-15", "aplicarArt523": false}
                """)).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("ao menos uma parcela"));
    }
}
