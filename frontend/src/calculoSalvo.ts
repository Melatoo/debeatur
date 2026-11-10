import type { CalculoRequest, Regime } from './api'
import { data } from './formato'
import { fimDoMesAnterior, parcelaVazia } from './caso'
import { paraCampo } from './numero'
import { pensaoVazia, type PensaoEditavel } from './pensao'
import { iguais, type Rascunho } from './rascunho'
import { paraEditavel } from './regime'

/** O nome que o servidor dá a um regime informado sem nome (`RegimeDto.NOME_PADRAO`). */
const NOME_SEM_NOME = 'Critérios fixados no título'

/** Decimal da API, que no pedido salvo chega como número JSON, como o formulário o mostra. */
const campo = (valor: string | number) => paraCampo(String(valor))

/**
 * Valor em reais com ao menos duas casas, como foi digitado: o JSON perde o zero do fim, e
 * "350,5" no campo pareceria outro valor. Feito no texto, sem passar por ponto flutuante.
 */
const reais = (valor: string | number) => {
  const [inteiro, fracao = ''] = String(valor).split('.')
  return `${inteiro},${fracao.padEnd(2, '0')}`
}

function paraPensaoEditavel(p: NonNullable<CalculoRequest['pensao']>): PensaoEditavel {
  return {
    percentual: campo(p.percentual),
    diaVencimento: String(p.diaVencimento),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.citacao ?? '',
    pagamentos: (p.pagamentos ?? []).map((pg) => ({ mes: pg.mes, valor: reais(pg.valor) })),
    ajuizamento: p.ajuizamento ?? '',
  }
}

/**
 * O regime de um cálculo salvo comparado com o padrão de hoje. O servidor salva o regime resolvido,
 * então o padrão volta por extenso, e só o nome diz que ele foi o escolhido:
 * - `padrao`: as regras ainda são as do padrão de hoje;
 * - `padrao-anterior`: foi o padrão, mas o padrão mudou desde o salvamento;
 * - `desconhecido`: não deu para buscar o padrão de hoje e comparar;
 * - `proprio`: critérios fixados no título.
 *
 * @param padrao o regime de `GET /api/regimes/padrao`, ou nulo se não deu para buscá-lo
 */
export type RegimeSalvo = 'padrao' | 'padrao-anterior' | 'desconhecido' | 'proprio'

export function regimeSalvo(pedido: CalculoRequest, padrao: Regime | null): RegimeSalvo {
  if (!pedido.regime) return 'padrao'
  if (padrao === null) return 'desconhecido'
  if (pedido.regime.nome !== padrao.nome) return 'proprio'
  return iguais(paraEditavel(pedido.regime.regras), paraEditavel(padrao.regras)) ? 'padrao' : 'padrao-anterior'
}

/**
 * O pedido de um cálculo salvo como formulário, para abrir, duplicar ou atualizar.
 *
 * Se o regime salvo ainda for o padrão de hoje, o formulário volta a "Regime padrão do TJMG", como
 * foi preenchido. Se o padrão mudou desde então, ou não se sabe qual é (`padrao` nulo), as regras
 * salvas entram como critérios do título: reabrir não pode mudar o regime de um cálculo que já foi
 * para os autos. Ver `regimeSalvo`.
 *
 * @param padrao o regime de `GET /api/regimes/padrao`, ou nulo se não deu para buscá-lo
 */
export function paraRascunho(pedido: CalculoRequest, padrao: Regime | null): Rascunho {
  // Os campos do processo podem voltar nulos do servidor.
  const processo = pedido.processo
  const comPensao = pedido.pensao != null
  const parcelas = (pedido.parcelas ?? []).map((p) => ({
    descricao: p.descricao,
    valor: reais(p.valor),
    inicioCorrecao: p.inicioCorrecao,
    inicioJuros: p.inicioJuros ?? '',
  }))
  const regras = pedido.regime ? paraEditavel(pedido.regime.regras) : []
  const ehPadrao = regimeSalvo(pedido, padrao) === 'padrao'
  const nome = pedido.regime?.nome ?? ''
  return {
    processo: {
      numero: processo?.numero ?? '',
      juizo: processo?.juizo ?? '',
      exequente: processo?.exequente ?? '',
      executado: processo?.executado ?? '',
    },
    dataBase: pedido.dataBase,
    aplicarArt523: pedido.aplicarArt523,
    comPensao,
    // Sem pensão, a opção não aparece; marcada, ela volta como o formulário em branco a deixa.
    separarRitos: pedido.pensao ? pedido.pensao.ajuizamento != null : true,
    pensao: pedido.pensao ? paraPensaoEditavel(pedido.pensao) : pensaoVazia(),
    // Sem pensão, as parcelas avulsas são o cálculo, e o formulário pede ao menos uma.
    parcelas: !comPensao && parcelas.length === 0 ? [parcelaVazia()] : parcelas,
    outrasAbertas: comPensao && parcelas.length > 0,
    regimeProprio: !ehPadrao,
    nomeRegime: ehPadrao || nome === NOME_SEM_NOME ? '' : nome,
    regras: ehPadrao ? [] : regras,
  }
}

/** O título que o servidor dá a um cálculo salvo sem título; o campo o mostra como exemplo. */
export function tituloPadrao(pedido: Pick<CalculoRequest, 'dataBase' | 'processo'>): string {
  const numero = pedido.processo?.numero?.trim()
  return numero ? `Processo ${numero}` : `Cálculo com data-base em ${data(pedido.dataBase)}`
}

/**
 * A data-base de um cálculo salvo trazido para hoje: o fim do mês anterior, a mesma do formulário
 * em branco. Nula se o cálculo já vai até lá, e não há o que atualizar.
 */
export function novaDataBase(dataBase: string, hoje = new Date()): string | null {
  const nova = fimDoMesAnterior(hoje)
  return dataBase < nova ? nova : null
}
