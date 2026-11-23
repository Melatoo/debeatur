package br.com.debeatur.api.calculo;

import br.com.debeatur.api.calculo.DemonstrativoResponse.Linha;
import br.com.debeatur.api.calculo.DemonstrativoResponse.MesPensao;
import br.com.debeatur.api.calculo.RegimeDto.RegraDto;
import br.com.debeatur.api.calculo.RegimeDto.TipoJuros;
import br.com.debeatur.core.alimentos.InicioJuros;
import br.com.debeatur.core.alimentos.RegraRitos;
import br.com.debeatur.core.alimentos.Rito;
import br.com.debeatur.core.calculo.BaseHonorarios;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.indices.SgsClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * O que o demonstrativo diz, sem o formato do arquivo: os campos do cabeçalho e dos critérios,
 * as colunas e os grupos da memória, o quadro-resumo e as fontes dos índices. O PDF e a planilha
 * saem os dois daqui, e por isso trazem os mesmos dados.
 * <p>
 * Só apresenta o que já está em {@link DemonstrativoResponse}: nenhum valor é recalculado aqui,
 * e os totais são os somados pelo motor. O saldo depois dos abatimentos é a única diferença
 * feita fora do motor, como já era no PDF.
 */
final class ConteudoDemonstrativo {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");
    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    static final String TITULO = "Demonstrativo discriminado e atualizado do débito";
    static final String SEGREDO_DE_JUSTICA = "Segredo de justiça (art. 189, II, do CPC)";

    private static final Map<Rito, String[]> RITOS = Map.of(
            Rito.PRISAO, new String[]{"Rito da prisão",
                    "Art. 528, §3º, do CPC: as três parcelas vencidas até o ajuizamento e as que vencem depois dele."},
            Rito.PENHORA, new String[]{"Rito da penhora",
                    "Art. 528, §8º, do CPC: as parcelas mais antigas, com multa e honorários do art. 523, §1º."});

    private static final String PRISAO_PELA_DPMG =
            "As três últimas parcelas do cálculo, como a planilha da DPMG.";

    /** Colunas com total no rodapé, na ordem em que aparecem. */
    enum Total { CORRIGIDO, JUROS, ATUALIZADO }

    /** Como o valor de uma coluna se apresenta: o PDF o escreve, a planilha o guarda como número ou data. */
    enum Formato { TEXTO, DATA, REAIS, FATOR, PERCENTUAL }

    /**
     * Uma coluna da memória.
     *
     * @param valor o dado da linha: {@link BigDecimal}, {@link LocalDate} ou texto, conforme o
     *              formato, ou nulo quando a linha não o tem (sai "—" no PDF e na planilha)
     */
    record Coluna(String titulo, float largura, Formato formato, Function<Linha, Object> valor, Total total) {

        boolean numero() {
            return formato != Formato.TEXTO && formato != Formato.DATA;
        }

        /** O valor da linha como o PDF o escreve. */
        String texto(Linha linha) {
            Object dado = valor.apply(linha);
            if (dado == null) {
                return "—";
            }
            return switch (dado) {
                case String s -> s;
                case LocalDate d -> data(d);
                case BigDecimal b -> switch (formato) {
                    case FATOR -> fator(b);
                    case PERCENTUAL -> percentual(b);
                    default -> decimal(b);
                };
                default -> dado.toString();
            };
        }
    }

    /** Totais de um grupo de linhas, sempre os somados pelo motor. */
    record Totais(BigDecimal corrigido, BigDecimal juros, BigDecimal atualizado) {

        BigDecimal de(Total total) {
            return switch (total) {
                case CORRIGIDO -> corrigido;
                case JUROS -> juros;
                case ATUALIZADO -> atualizado;
            };
        }
    }

    /** Uma linha do fechamento de uma tabela, abaixo do subtotal: abatimentos, art. 523 e totais. */
    record LinhaDeTotal(String rotulo, BigDecimal valor, boolean destaque) {
    }

    /**
     * Um grupo da memória: todas as parcelas, ou as de um rito.
     *
     * @param titulo  o nome do rito, ou nulo se os ritos não são separados
     * @param regra   o dispositivo do rito, ou nulo
     * @param linhas  as parcelas, pela ordem do termo inicial da correção
     */
    record Grupo(String titulo, String regra, List<Linha> linhas, Totais totais, List<LinhaDeTotal> fechamento) {

        String rotuloDoSubtotal() {
            return "Subtotal, " + contar(linhas.size(), "parcela", "parcelas");
        }
    }

    private final CalculoRequest pedido;
    private final DemonstrativoResponse resultado;
    private final LocalDateTime emissao;
    /** Quando o cálculo foi salvo e as séries guardadas no snapshot; nulo se não foi salvo. */
    private final LocalDateTime salvoEm;
    private final List<Coluna> colunas;
    private final boolean comAcrescimos;

    ConteudoDemonstrativo(CalculoRequest pedido, DemonstrativoResponse resultado, LocalDateTime emissao,
                          LocalDateTime salvoEm) {
        this.pedido = pedido;
        this.resultado = resultado;
        this.emissao = emissao;
        this.salvoEm = salvoEm;
        this.colunas = colunas(resultado.linhas());
        this.comAcrescimos = resultado.multaArt523().signum() > 0 || resultado.honorariosArt523().signum() > 0;
    }

    /**
     * Ex.: {@code demonstrativo-5001234-56.2024.8.13.0024-2026-10-31.pdf}.
     *
     * @param extensao sem o ponto, como {@code pdf} ou {@code xlsx}
     */
    static String nomeDoArquivo(CalculoRequest pedido, String extensao) {
        String numero = pedido.processo() == null ? null : texto(pedido.processo().numero());
        String identificacao = numero == null ? "" : numero.replaceAll("[^0-9A-Za-z.-]+", "_") + "-";
        return "demonstrativo-" + identificacao + pedido.dataBase() + "." + extensao;
    }

    DemonstrativoResponse resultado() {
        return resultado;
    }

    LocalDateTime emissao() {
        return emissao;
    }

    List<Coluna> colunas() {
        return colunas;
    }

    // ---- cabeçalho -----------------------------------------------------------------------

    String subtitulo() {
        return pedido.pensao() == null
                ? "Cumprimento de sentença (art. 524 do CPC)"
                : "Execução de alimentos (arts. 524 e 528 do CPC)";
    }

    /** O número do processo, ou nulo se não foi informado. */
    String processo() {
        return pedido.processo() == null ? null : texto(pedido.processo().numero());
    }

    /** Identificação do processo, data-base e emissão, sem os campos vazios. */
    Map<String, String> cabecalho() {
        Map<String, String> campos = new LinkedHashMap<>();
        ProcessoRequest processo = pedido.processo();
        if (processo != null) {
            campos.put("Processo", texto(processo.numero()));
            campos.put("Juízo", texto(processo.juizo()));
            campos.put("Exequente", texto(processo.exequente()));
            campos.put("Executado", texto(processo.executado()));
        }
        campos.put("Data-base do cálculo", data(resultado.dataBase()));
        if (salvoEm != null) {
            campos.put("Calculado em", quando(salvoEm));
        }
        campos.put("Emitido em", quando(emissao));
        campos.values().removeIf(Objects::isNull);
        return campos;
    }

    /** "Demonstrativo atualizado até ... · emitido em ...", com o processo à frente se houver. */
    String identificacao() {
        String processo = processo();
        return (processo == null ? "" : "Processo " + processo + " · ")
                + "Demonstrativo atualizado até " + data(resultado.dataBase())
                + " · emitido em " + quando(emissao);
    }

    // ---- critérios -----------------------------------------------------------------------

    Map<String, String> criterios() {
        Map<String, String> campos = new LinkedHashMap<>();
        PensaoRequest pensao = pedido.pensao();
        int avulsas = pedido.parcelas() == null ? 0 : pedido.parcelas().size();
        if (pensao != null) {
            campos.put("Pensão", descricaoDaPensao(pensao) + ", com vencimento no dia " + pensao.diaVencimento());
            campos.put("13º salário", pensao.comDecimoTerceiro() ? "Incluído: a parcela de dezembro vale o dobro" : "Não incluído");
            campos.put("Parcelas", pensao.primeiroMes().format(MES) + " a " + pensao.ultimoMes().format(MES)
                    + (avulsas > 0 ? ", mais " + contar(avulsas, "parcela avulsa", "parcelas avulsas") : ""));
            campos.put("Juros", pensao.inicioJuros() == InicioJuros.CITACAO
                    ? "Desde a citação (" + data(pensao.citacao()) + ") para as parcelas vencidas antes dela; as seguintes, desde o vencimento"
                    : "Desde o vencimento de cada parcela");
            campos.put("Pagamentos parciais", pagamentos());
            campos.put("Abatimentos", abatimentos());
            campos.put("Ajuizamento", resultado.ritos() == null
                    ? "Ritos não separados" : data(resultado.ritos().ajuizamento()));
        } else {
            campos.put("Parcelas", contar(avulsas, "parcela", "parcelas"));
        }
        campos.put("Art. 523, §1º", art523());
        campos.put("Correção e juros", resultado.regime().nome());
        return campos;
    }

    /** As regras do regime no período do cálculo, uma frase por regra. */
    List<String> regras() {
        return regrasDoCalculo().stream()
                .map(r -> periodo(r) + ": " + nomeIndice(r.correcao()) + " + " + r.descricaoJuros())
                .toList();
    }

    private static String descricaoDaPensao(PensaoRequest pensao) {
        return switch (pensao.tipoBase()) {
            case SALARIO_MINIMO -> numeroLivre(pensao.percentual()) + "% do salário mínimo vigente em cada mês";
            case RENDIMENTOS -> numeroLivre(pensao.percentual()) + "% dos rendimentos de " + moeda(pensao.rendimentos())
                    + ", iguais em todos os meses";
            case VALOR_FIXO -> "Valor fixo, sem reajuste: " + String.join("; ", pensao.valoresFixos().stream()
                    .sorted(Comparator.comparing(PensaoRequest.ValorFixoRequest::aPartirDe))
                    .map(v -> moeda(v.valor()) + " a partir de " + v.aPartirDe().format(MES))
                    .toList());
        };
    }

    private String abatimentos() {
        if (resultado.abatimentos().isEmpty()) {
            return "Nenhum";
        }
        String destino = resultado.ritos() == null ? "do débito atualizado" : "do débito da penhora";
        return "Pelo valor nominal, sem correção, " + destino + ": " + String.join("; ",
                resultado.abatimentos().stream().map(ConteudoDemonstrativo::abatimento).toList());
    }

    private static String abatimento(DemonstrativoResponse.AbatimentoResponse a) {
        return data(a.data()) + origem(a) + ": " + moeda(a.valor());
    }

    private static String origem(DemonstrativoResponse.AbatimentoResponse a) {
        return a.origem() == null || a.origem().isBlank() ? "" : " (" + a.origem() + ")";
    }

    private String pagamentos() {
        List<MesPensao> pagos = resultado.linhas().stream()
                .map(Linha::pensao)
                .filter(p -> p != null && p.valorPago().signum() > 0)
                .sorted(Comparator.comparing(MesPensao::mes))
                .toList();
        if (pagos.isEmpty()) {
            return "Nenhum";
        }
        return String.join("; ", pagos.stream().map(p -> p.mes().format(MES) + ": " + moeda(p.valorPago())).toList());
    }

    private String art523() {
        boolean ritos = resultado.ritos() != null;
        if (comAcrescimos) {
            String saldo = (resultado.abatimentos().isEmpty() ? "o débito" : "o saldo, já abatido,")
                    + (ritos ? " da penhora" : " atualizado");
            return honorariosSobreAMulta()
                    ? "Multa de 10% sobre " + saldo + " e honorários de 10% sobre esse valor com a multa, como o CADEJ"
                    : "Multa de 10% e honorários de 10% sobre " + saldo;
        }
        return pedido.aplicarArt523()
                ? "Multa e honorários de 10%, sem débito " + (ritos ? "na penhora" : "a acrescer")
                : "Não aplicados";
    }

    private boolean honorariosSobreAMulta() {
        return pedido.baseHonorariosOuPadrao() == BaseHonorarios.DEBITO_COM_MULTA;
    }

    private String rotuloHonorarios() {
        return honorariosSobreAMulta()
                ? "Honorários de 10%, com a multa na base (art. 523, §1º, do CPC)"
                : "Honorários de 10% (art. 523, §1º, do CPC)";
    }

    /** Meses do cálculo, inclusive nas duas pontas. */
    private record Periodo(YearMonth de, YearMonth ate) {
    }

    /**
     * Regras que alcançam o período do cálculo, com a vigência recortada a ele: num cálculo de
     * 2024, a regra que vale desde 07/1994 aparece desde o primeiro mês do cálculo. A regra sem
     * fim continua sem fim. Sem período, ficam as regras do regime como estão.
     */
    private List<RegraDto> regrasDoCalculo() {
        return periodoDoCalculo()
                .map(p -> regrasEntre(p.de(), p.ate()).stream().map(r -> recortada(r, p)).toList())
                .orElseGet(() -> resultado.regime().regras());
    }

    private static RegraDto recortada(RegraDto regra, Periodo periodo) {
        YearMonth inicio = regra.inicio().isBefore(periodo.de()) ? periodo.de() : regra.inicio();
        YearMonth fim = regra.fim() != null && regra.fim().isAfter(periodo.ate()) ? periodo.ate() : regra.fim();
        return new RegraDto(inicio, fim, regra.correcao(), regra.juros(), regra.percentualJurosMensal(),
                regra.ultimoDiaTaxaFixa(), regra.descricaoJuros());
    }

    /**
     * Período do cálculo: do primeiro termo inicial, de correção ou de juros, ao mês anterior à
     * data-base, o último que o motor percorre. Se tudo vence no mês da data-base, é esse mês.
     * Vazio se nenhuma linha tem termo inicial.
     */
    private Optional<Periodo> periodoDoCalculo() {
        YearMonth ultimo = ultimoMesPercorrido();
        return Stream.of(primeiroMes(Linha::inicioCorrecao), primeiroMes(Linha::inicioJuros))
                .flatMap(Optional::stream)
                .min(Comparator.naturalOrder())
                .map(primeiro -> new Periodo(primeiro, primeiro.isAfter(ultimo) ? primeiro : ultimo));
    }

    /**
     * Regras dos meses em que o motor aplicou o termo: do mais antigo deles (inclusive) ao mês
     * da data-base (exclusive). Vazio se nenhuma linha percorre mês algum com esse termo.
     */
    private List<RegraDto> regrasAplicadas(Function<Linha, LocalDate> termo) {
        YearMonth ultimo = ultimoMesPercorrido();
        return primeiroMes(termo)
                .filter(primeiro -> !primeiro.isAfter(ultimo))
                .map(primeiro -> regrasEntre(primeiro, ultimo))
                .orElse(List.of());
    }

    /** O termo inicial mais antigo entre as linhas, sem as que não o têm (ex.: sem juros). */
    private Optional<YearMonth> primeiroMes(Function<Linha, LocalDate> termo) {
        return resultado.linhas().stream()
                .map(termo)
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .min(Comparator.naturalOrder());
    }

    /** O mês anterior ao da data-base: o motor corrige e conta juros até ele. */
    private YearMonth ultimoMesPercorrido() {
        return YearMonth.from(resultado.dataBase()).minusMonths(1);
    }

    private List<RegraDto> regrasEntre(YearMonth de, YearMonth ate) {
        return resultado.regime().regras().stream()
                .filter(r -> !r.inicio().isAfter(ate) && (r.fim() == null || !r.fim().isBefore(de)))
                .toList();
    }

    // ---- memória de cálculo --------------------------------------------------------------

    private static List<Coluna> colunas(List<Linha> linhas) {
        boolean comSalarioMinimo = linhas.stream().anyMatch(l -> l.pensao() != null && l.pensao().salarioMinimo() != null);
        boolean comPagamento = linhas.stream().anyMatch(l -> l.pensao() != null && l.pensao().valorPago().signum() > 0);
        List<Coluna> colunas = new ArrayList<>();
        colunas.add(new Coluna("Parcela", 1.6f, Formato.TEXTO, ConteudoDemonstrativo::nomeDaParcela, null));
        colunas.add(new Coluna("Correção desde", 1.0f, Formato.DATA, Linha::inicioCorrecao, null));
        colunas.add(new Coluna("Juros desde", 1.0f, Formato.DATA,
                l -> l.inicioJuros() == null ? "sem juros" : l.inicioJuros(), null));
        if (comSalarioMinimo) {
            colunas.add(new Coluna("Salário mínimo (R$)", 1.1f, Formato.REAIS,
                    l -> l.pensao() == null ? null : l.pensao().salarioMinimo(), null));
        }
        colunas.add(new Coluna("Devido (R$)", 1.1f, Formato.REAIS,
                l -> l.pensao() == null ? l.valorOriginal() : l.pensao().valorDevido(), null));
        if (comPagamento) {
            colunas.add(new Coluna("Pago (R$)", 1.0f, Formato.REAIS,
                    l -> l.pensao() == null || l.pensao().valorPago().signum() == 0 ? null : l.pensao().valorPago(),
                    null));
        }
        colunas.add(new Coluna("Fator de correção", 1.1f, Formato.FATOR, Linha::fatorCorrecao, null));
        colunas.add(new Coluna("Corrigido (R$)", 1.2f, Formato.REAIS, Linha::valorCorrigido, Total.CORRIGIDO));
        colunas.add(new Coluna("Juros (%)", 0.9f, Formato.PERCENTUAL, Linha::percentualJuros, null));
        colunas.add(new Coluna("Juros (R$)", 1.1f, Formato.REAIS, Linha::valorJuros, Total.JUROS));
        colunas.add(new Coluna("Atualizado (R$)", 1.2f, Formato.REAIS, Linha::subtotal, Total.ATUALIZADO));
        return List.copyOf(colunas);
    }

    private static String nomeDaParcela(Linha l) {
        if (l.pensao() == null) {
            return l.descricao();
        }
        boolean quitada = l.pensao().valorPago().signum() > 0 && l.valorOriginal().signum() == 0;
        return l.pensao().mes().format(MES) + (l.pensao().decimoTerceiro() ? " e 13º" : "") + (quitada ? " (quitada)" : "");
    }

    /** A posição da primeira coluna com total: o rótulo do subtotal ocupa as anteriores. */
    int primeiraColunaComTotal() {
        int primeira = 0;
        while (colunas.get(primeira).total() == null) {
            primeira++;
        }
        return primeira;
    }

    String explicacaoDasColunas() {
        String pensao = resultado.linhas().stream().anyMatch(l -> l.pensao() != null)
                ? "O devido é o valor da pensão no mês" + (pedido.pensao() != null && pedido.pensao().comDecimoTerceiro()
                        ? " (em dobro em dezembro, com o 13º salário)" : "")
                        + ", e o pago é abatido antes da correção. "
                : "";
        return pensao + "O fator de correção é o produto de (1 + variação) do índice em cada mês, do mês do termo inicial "
                + "da correção até o mês anterior ao da data-base, sem arredondamento. Os juros de mora são simples: "
                + "o percentual soma as taxas dos meses do termo inicial dos juros até o mês anterior ao da data-base "
                + "e incide sobre o valor corrigido. Valores monetários arredondados a duas casas (meio para cima).";
    }

    /** Os grupos da memória: um só sem ritos, ou o da prisão e o da penhora, nessa ordem. */
    List<Grupo> grupos() {
        DemonstrativoResponse.Ritos ritos = resultado.ritos();
        if (ritos == null) {
            Totais totais = new Totais(resultado.totalCorrigido(), resultado.totalJuros(), resultado.debitoAtualizado());
            return List.of(new Grupo(null, null, ordenadas(resultado.linhas()), totais, fechamento(totais, null)));
        }
        List<Grupo> grupos = new ArrayList<>();
        for (Rito rito : Rito.values()) {
            DemonstrativoResponse.Subtotal subtotal = rito == Rito.PRISAO ? ritos.prisao() : ritos.penhora();
            List<Linha> doRito = resultado.linhas().stream().filter(l -> l.rito() == rito).toList();
            Totais totais = new Totais(subtotal.totalCorrigido(), subtotal.totalJuros(), subtotal.debitoAtualizado());
            String regra = rito == Rito.PRISAO && pedido.pensao().regraDosRitos() == RegraRitos.ULTIMOS_MESES_DO_CALCULO
                    ? PRISAO_PELA_DPMG : RITOS.get(rito)[1];
            grupos.add(new Grupo(RITOS.get(rito)[0], regra, ordenadas(doRito), totais,
                    fechamento(totais, rito)));
        }
        return grupos;
    }

    private static List<Linha> ordenadas(List<Linha> linhas) {
        return linhas.stream().sorted(Comparator.comparing(Linha::inicioCorrecao)).toList();
    }

    /**
     * O que vem abaixo do subtotal: os abatimentos e a multa e os honorários do art. 523. Os dois
     * ficam no grupo sem ritos ou no da penhora; no da prisão, só o que dos abatimentos passou da
     * penhora.
     */
    private List<LinhaDeTotal> fechamento(Totais totais, Rito rito) {
        List<LinhaDeTotal> linhas = new ArrayList<>();
        DemonstrativoResponse.Ritos ritos = resultado.ritos();
        if (rito == Rito.PRISAO && ritos.abatidoPrisao().signum() > 0) {
            linhas.add(new LinhaDeTotal("Abatimentos que passaram do débito da penhora", ritos.abatidoPrisao().negate(), false));
            linhas.add(new LinhaDeTotal("Total da prisão", ritos.totalPrisao(), true));
        }
        if (rito == Rito.PRISAO) {
            return linhas;
        }
        if (!resultado.abatimentos().isEmpty()) {
            for (DemonstrativoResponse.AbatimentoResponse a : resultado.abatimentos()) {
                linhas.add(new LinhaDeTotal("Abatimento de " + data(a.data()) + origem(a), a.valor().negate(), false));
            }
            if (ritos != null && ritos.abatidoPrisao().signum() > 0) {
                linhas.add(new LinhaDeTotal("Parte dos abatimentos que sai da prisão", ritos.abatidoPrisao(), false));
            }
            BigDecimal abatido = ritos == null ? resultado.totalAbatido() : ritos.abatidoPenhora();
            linhas.add(new LinhaDeTotal("Saldo", totais.atualizado().subtract(abatido), true));
        }
        if (comAcrescimos) {
            linhas.add(new LinhaDeTotal("Multa de 10% (art. 523, §1º, do CPC)", resultado.multaArt523(), false));
            linhas.add(new LinhaDeTotal(rotuloHonorarios(), resultado.honorariosArt523(), false));
            if (ritos != null) {
                linhas.add(new LinhaDeTotal("Total da penhora", ritos.totalPenhora(), true));
            }
        }
        return linhas;
    }

    // ---- totais --------------------------------------------------------------------------

    /** O quadro-resumo: o débito de cada rito, os acréscimos, sem o total da execução. */
    Map<String, BigDecimal> resumo() {
        Map<String, BigDecimal> linhas = new LinkedHashMap<>();
        DemonstrativoResponse.Ritos ritos = resultado.ritos();
        if (ritos != null) {
            linhas.put("Rito da prisão: débito atualizado (" + contar(ritos.prisao().parcelas(), "parcela", "parcelas") + ")",
                    ritos.prisao().debitoAtualizado());
            if (ritos.abatidoPrisao().signum() > 0) {
                linhas.put("Rito da prisão: abatimentos que passaram da penhora", ritos.abatidoPrisao().negate());
            }
            linhas.put("Rito da penhora: débito atualizado (" + contar(ritos.penhora().parcelas(), "parcela", "parcelas") + ")",
                    ritos.penhora().debitoAtualizado());
            if (ritos.abatidoPenhora().signum() > 0) {
                linhas.put("Rito da penhora: abatimentos (valor nominal)", ritos.abatidoPenhora().negate());
            }
        } else {
            linhas.put("Valor corrigido", resultado.totalCorrigido());
            linhas.put("Juros de mora", resultado.totalJuros());
            linhas.put("Débito atualizado", resultado.debitoAtualizado());
            if (resultado.totalAbatido().signum() > 0) {
                linhas.put("Abatimentos (valor nominal)", resultado.totalAbatido().negate());
                linhas.put("Saldo", resultado.debitoAtualizado().subtract(resultado.totalAbatido()));
            }
        }
        if (comAcrescimos) {
            linhas.put("Multa de 10% (art. 523, §1º, do CPC)", resultado.multaArt523());
            linhas.put(rotuloHonorarios(), resultado.honorariosArt523());
            if (ritos != null) {
                linhas.put("Rito da penhora: total com os acréscimos", ritos.totalPenhora());
            }
        }
        return linhas;
    }

    // ---- fontes --------------------------------------------------------------------------

    /** A procedência de cada série que o cálculo usou, uma frase por série. */
    List<String> fontes() {
        // só os índices que o motor de fato aplicou: a correção nos meses corrigidos e a taxa
        // legal nos meses com juros, e não tudo o que a regra prevê
        List<RegraDto> deCorrecao = regrasAplicadas(Linha::inicioCorrecao);
        Set<Indice> indices = EnumSet.noneOf(Indice.class);
        deCorrecao.forEach(r -> indices.add(r.correcao()));
        if (regrasAplicadas(Linha::inicioJuros).stream()
                .anyMatch(r -> r.juros() == TipoJuros.TAXA_LEGAL || r.juros() == TipoJuros.TRANSICAO_TAXA_LEGAL)) {
            indices.add(Indice.TAXA_LEGAL);
        }
        List<String> itens = new ArrayList<>();
        for (Indice indice : indices) {
            String valores = indice.corrigeMoeda() ? "variações mensais" : "taxas mensais";
            // a série que deu os valores deste cálculo: a SELIC de um cálculo salvo antes da SGS 11 veio da 4390
            List<BigDecimal> usados = resultado.series().stream()
                    .filter(s -> s.serie().equals(indice.name()))
                    .flatMap(s -> s.valores().stream().map(DemonstrativoResponse.ValorMensal::valor))
                    .toList();
            itens.add(indice.descricao() + ": " + valores + " da " + SgsClient.fonte(indice, usados) + ".");
        }
        if (indices.isEmpty()) {
            itens.add("Nenhuma série de índice incidiu na correção ou nos juros deste cálculo.");
        }
        if (regimePadrao()) {
            // o período da tabela é o da regra do INPC no próprio regime, e não uma data fixa aqui
            deCorrecao.stream().filter(r -> r.correcao() == Indice.INPC).findFirst().ifPresent(r -> itens.add(
                    (r.fim() == null ? "O" : "Até " + r.fim().format(MES) + ", o")
                            + " regime padrão segue a tabela de fatores de atualização monetária da "
                            + "Corregedoria-Geral de Justiça do TJMG, de base INPC; o fator é composto das variações "
                            + "mensais do INPC."));
        }
        if (resultado.linhas().stream().anyMatch(l -> l.pensao() != null)) {
            itens.add("Salário mínimo nacional: valor vigente em cada mês, da série " + SgsClient.CODIGO_SALARIO_MINIMO
                    + " do SGS/BCB.");
        }
        return itens;
    }

    /** De onde e de quando são os valores das séries. */
    String consultaDasSeries() {
        // sem salvar, as séries vêm do cache local, atualizado todo dia a partir do SGS; salvo, do
        // snapshot, para que o arquivo saia igual mesmo depois de o BCB revisar uma série
        String valores = salvoEm == null
                ? "com os valores publicados e em uso em " + emissao.format(DATA) + "."
                : "com os valores em uso em " + salvoEm.format(DATA) + ", quando o cálculo foi salvo, guardados "
                        + "com ele e reaplicados nesta emissão.";
        return "Séries do Sistema Gerenciador de Séries Temporais do Banco Central do Brasil (api.bcb.gov.br), " + valores;
    }

    /** Se o cálculo usou as regras do regime cível padrão do TJMG, com ou sem o nome dele. */
    private boolean regimePadrao() {
        return resultado.regime().regras().equals(RegimeDto.de(RegimeAtualizacao.civelPadrao()).regras());
    }

    // ---- formatos ------------------------------------------------------------------------

    private static DecimalFormat formato(String padrao) {
        DecimalFormat formato = new DecimalFormat(padrao, DecimalFormatSymbols.getInstance(PT_BR));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    }

    static String decimal(BigDecimal valor) {
        return formato("#,##0.00").format(valor);
    }

    static String moeda(BigDecimal valor) {
        return "R$ " + decimal(valor);
    }

    /** Sete casas, como na tela; o fator completo, sem arredondar, é o que o motor aplica. */
    static String fator(BigDecimal valor) {
        return formato("0.0000000").format(valor);
    }

    /**
     * Quatro casas, como na tela, e até seis quando a taxa as tem: o BCB publica a taxa legal
     * com seis, e com menos quem confere os juros a partir do percentual impresso pode errar o
     * centavo. A taxa fixa sai "1,0000%", e não "1,000000%".
     */
    static String percentual(BigDecimal valor) {
        return formato("0.0000##").format(valor) + "%";
    }

    private static String numeroLivre(BigDecimal valor) {
        return formato("#,##0.##########").format(valor);
    }

    static String data(LocalDate data) {
        return data == null ? "—" : data.format(DATA);
    }

    private static String quando(LocalDateTime instante) {
        return instante.format(DATA) + " às " + instante.format(HORA);
    }

    private static String periodo(RegraDto regra) {
        if (regra.fim() == null) {
            return "desde " + regra.inicio().format(MES);
        }
        return regra.inicio().equals(regra.fim())
                ? regra.inicio().format(MES)
                : regra.inicio().format(MES) + " a " + regra.fim().format(MES);
    }

    private static String nomeIndice(Indice indice) {
        return switch (indice) {
            case IPCA_E -> "IPCA-E";
            default -> indice.name();
        };
    }

    static String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    /** O texto sem espaços nas pontas, ou nulo se vazio. */
    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
