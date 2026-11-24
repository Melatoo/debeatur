package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.alimentos.Abatimento;
import br.com.debeatur.core.alimentos.BasePensao;
import br.com.debeatur.core.alimentos.ExcedentePagamento;
import br.com.debeatur.core.alimentos.InicioJuros;
import br.com.debeatur.core.alimentos.PagamentosParciais;
import br.com.debeatur.core.alimentos.Pensao;
import br.com.debeatur.core.alimentos.RegraRitos;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.TreeMap;

/**
 * Pensão alimentícia com uma parcela por mês de {@code primeiroMes} a {@code ultimoMes}.
 *
 * @param base           como o título fixou o valor; nulo vale {@link TipoBase#SALARIO_MINIMO},
 *                       o único tipo dos cálculos salvos antes dos outros
 * @param percentual     percentual do salário mínimo ou dos rendimentos, em pontos (ex.:
 *                       {@code 30} para 30%); obrigatório nessas bases
 * @param rendimentos    rendimentos mensais do início da pensão até a primeira mudança;
 *                       obrigatório na base {@link TipoBase#RENDIMENTOS}
 * @param mudancasRendimentos rendimentos a partir de cada mês, se mudaram depois do início; só
 *                       na base {@link TipoBase#RENDIMENTOS}
 * @param valoresFixos   valor mensal a partir de cada mês; obrigatório na base
 *                       {@link TipoBase#VALOR_FIXO}, com o primeiro no início da pensão ou antes
 * @param decimoTerceiro se a pensão alcança o 13º salário, que dobra a parcela de dezembro
 * @param citacao        obrigatória se {@code inicioJuros} for {@link InicioJuros#CITACAO}
 * @param pagamentos     valores pagos, abatidos da parcela do mesmo mês antes da correção
 * @param excedentePagamento o que fazer com o pagamento maior que a parcela do mês; nulo vale
 *                       {@link ExcedentePagamento#RECUSAR}, o comportamento dos cálculos salvos
 *                       antes da opção. A tela manda sempre a escolha, com a da DPMG como padrão
 * @param abatimentos    valores recebidos em qualquer data (bloqueio, depósito), que saem do
 *                       débito atualizado pelo valor nominal; com os ritos separados, saem da
 *                       penhora
 * @param ajuizamento    data do ajuizamento da execução; se informada, as linhas do
 *                       demonstrativo, inclusive as avulsas, são separadas entre o rito da prisão
 *                       e o da penhora pelo vencimento, e o art. 523 incide só sobre a penhora;
 *                       exige no máximo uma parcela, da pensão ou avulsa, por mês de vencimento
 * @param regraRitos     quais parcelas vão para a prisão ao separar os ritos; nula vale
 *                       {@link RegraRitos#LEI}, a regra dos cálculos salvos antes da opção, para
 *                       que eles não mudem ao reabrir. A tela manda sempre a regra escolhida, com
 *                       a da DPMG como padrão
 * <p>
 * Os campos da base, das mudanças dos rendimentos, do 13º, dos abatimentos, da regra dos ritos e do
 * excedente dos pagamentos são omitidos do JSON quando nulos: uma pensão em salário mínimo sem eles continua gravada no formato 1 dos cálculos salvos,
 * e os pedidos já gravados, que não os têm, são lidos com os padrões acima.
 */
public record PensaoRequest(
        @JsonInclude(JsonInclude.Include.NON_NULL) TipoBase base,
        @Positive BigDecimal percentual,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Positive BigDecimal rendimentos,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<@Valid MudancaRendimentosRequest> mudancasRendimentos,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<@Valid ValorFixoRequest> valoresFixos,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean decimoTerceiro,
        @NotNull @Min(1) @Max(31) Integer diaVencimento,
        @NotNull YearMonth primeiroMes,
        @NotNull YearMonth ultimoMes,
        @NotNull InicioJuros inicioJuros,
        LocalDate citacao,
        List<@Valid PagamentoRequest> pagamentos,
        @JsonInclude(JsonInclude.Include.NON_NULL) ExcedentePagamento excedentePagamento,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<@Valid AbatimentoRequest> abatimentos,
        LocalDate ajuizamento,
        @JsonInclude(JsonInclude.Include.NON_NULL) RegraRitos regraRitos) {

    /** Como o título fixou o valor mensal da pensão. */
    public enum TipoBase {
        /** Percentual do salário mínimo vigente em cada mês. */
        SALARIO_MINIMO,
        /** Percentual de um valor de rendimentos informado, que pode mudar por período. */
        RENDIMENTOS,
        /** Valores fixos por período, sem reajuste. */
        VALOR_FIXO
    }

    public record PagamentoRequest(@NotNull YearMonth mes, @NotNull @Positive BigDecimal valor) {
    }

    public record ValorFixoRequest(@NotNull YearMonth aPartirDe, @NotNull @Positive BigDecimal valor) {
    }

    public record MudancaRendimentosRequest(@NotNull YearMonth aPartirDe, @NotNull @Positive BigDecimal valor) {
    }

    public record AbatimentoRequest(@NotNull LocalDate data, @NotNull @Positive BigDecimal valor,
                                    @Size(max = 200) String origem) {
    }

    /** A base, com o padrão dos cálculos salvos antes de haver outras. */
    public TipoBase tipoBase() {
        return base == null ? TipoBase.SALARIO_MINIMO : base;
    }

    /** A regra dos ritos, com a dos cálculos salvos antes da opção. */
    public RegraRitos regraDosRitos() {
        return regraRitos == null ? RegraRitos.LEI : regraRitos;
    }

    /** O que fazer com o pagamento a maior, com o padrão dos cálculos salvos antes da opção. */
    public ExcedentePagamento excedenteDosPagamentos() {
        return excedentePagamento == null ? ExcedentePagamento.RECUSAR : excedentePagamento;
    }

    public boolean comDecimoTerceiro() {
        return Boolean.TRUE.equals(decimoTerceiro);
    }

    public List<MudancaRendimentosRequest> mudancasDosRendimentos() {
        return mudancasRendimentos == null ? List.of() : mudancasRendimentos;
    }

    public List<AbatimentoRequest> abatimentosInformados() {
        return abatimentos == null ? List.of() : abatimentos;
    }

    /** @throws IllegalArgumentException se faltar o dado que a base exige */
    Pensao paraDominio() {
        return new Pensao(baseParaDominio(), diaVencimento, primeiroMes, ultimoMes, inicioJuros, citacao,
                comDecimoTerceiro());
    }

    private BasePensao baseParaDominio() {
        return switch (tipoBase()) {
            case SALARIO_MINIMO -> new BasePensao.PercentualDoSalarioMinimo(exigir(percentual,
                    "Informe o percentual do salário mínimo"));
            case RENDIMENTOS -> {
                TreeMap<YearMonth, Dinheiro> mudancas = new TreeMap<>();
                for (MudancaRendimentosRequest m : mudancasDosRendimentos()) {
                    if (mudancas.put(m.aPartirDe(), new Dinheiro(m.valor())) != null) {
                        throw new IllegalArgumentException("Rendimentos a partir de " + m.aPartirDe()
                                + " informados mais de uma vez");
                    }
                }
                yield new BasePensao.PercentualDosRendimentos(
                        exigir(percentual, "Informe o percentual dos rendimentos"),
                        new Dinheiro(exigir(rendimentos, "Informe os rendimentos mensais")), mudancas);
            }
            case VALOR_FIXO -> {
                if (valoresFixos == null || valoresFixos.isEmpty()) {
                    throw new IllegalArgumentException("Informe ao menos um valor fixo");
                }
                TreeMap<YearMonth, Dinheiro> valores = new TreeMap<>();
                for (ValorFixoRequest v : valoresFixos) {
                    if (valores.put(v.aPartirDe(), new Dinheiro(v.valor())) != null) {
                        throw new IllegalArgumentException("Valor fixo a partir de " + v.aPartirDe()
                                + " informado mais de uma vez");
                    }
                }
                yield new BasePensao.ValorFixo(valores);
            }
        };
    }

    PagamentosParciais pagamentosParaDominio() {
        PagamentosParciais pagos = new PagamentosParciais(excedenteDosPagamentos());
        if (pagamentos != null) {
            pagamentos.forEach(p -> pagos.com(p.mes(), new Dinheiro(p.valor())));
        }
        return pagos;
    }

    List<Abatimento> abatimentosParaDominio() {
        return abatimentosInformados().stream()
                .map(a -> new Abatimento(a.data(), new Dinheiro(a.valor()), a.origem()))
                .toList();
    }

    private static <T> T exigir(T valor, String mensagem) {
        if (valor == null) {
            throw new IllegalArgumentException(mensagem);
        }
        return valor;
    }
}
