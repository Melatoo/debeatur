import type {
  AbatimentoRequest,
  InicioJuros,
  MudancaBaseRequest,
  PagamentoRequest,
  PensaoRequest,
  TipoBase,
  ValorFixoRequest,
} from './api'
import { exigirDecimal, paraCampo } from './numero'
import { moeda, mes } from './formato'

/** Base a partir de um mês, como o formulário a guarda: os campos que a base não usa ficam em branco. */
export interface MudancaBaseEditavel {
  aPartirDe: string
  base: TipoBase
  percentual: string
  rendimentos: string
  valor: string
}

export const mudancaBaseVazia = (): MudancaBaseEditavel => ({
  aPartirDe: '',
  base: 'SALARIO_MINIMO',
  percentual: '',
  rendimentos: '',
  valor: '',
})

/** Pensão como o formulário a guarda: campos em texto, como os inputs devolvem. */
export interface PensaoEditavel {
  base: TipoBase
  /** Percentual do salário mínimo ou dos rendimentos; ignorado no valor fixo. */
  percentual: string
  /** Só na base RENDIMENTOS. */
  rendimentos: string
  /** Só na base RENDIMENTOS: rendimentos a partir de cada mês, se mudaram. */
  mudancasRendimentos: ValorFixoRequest[]
  /** Só na base VALOR_FIXO. */
  valoresFixos: ValorFixoRequest[]
  /** Base a partir de cada mês, se ela mudou (revisional, acordo, definitivos); a base acima vale até a primeira. */
  mudancasBase: MudancaBaseEditavel[]
  decimoTerceiro: boolean
  diaVencimento: string
  primeiroMes: string
  ultimoMes: string
  inicioJuros: InicioJuros
  citacao: string
  pagamentos: PagamentoRequest[]
  /** O que passar da parcela do mês fica como crédito no próprio mês (DPMG); desmarcado, o cálculo recusa. */
  pagoAMaiorComoCredito: boolean
  abatimentos: AbatimentoRequest[]
  ajuizamento: string
}

export const pensaoVazia = (): PensaoEditavel => ({
  base: 'SALARIO_MINIMO',
  percentual: '',
  rendimentos: '',
  mudancasRendimentos: [],
  valoresFixos: [{ aPartirDe: '', valor: '' }],
  mudancasBase: [],
  decimoTerceiro: false,
  diaVencimento: '10',
  primeiroMes: '',
  ultimoMes: '',
  inicioJuros: 'VENCIMENTO',
  citacao: '',
  pagamentos: [],
  pagoAMaiorComoCredito: true,
  abatimentos: [],
  ajuizamento: '',
})

export const BASES: Record<TipoBase, string> = {
  SALARIO_MINIMO: 'Percentual do salário mínimo',
  RENDIMENTOS: 'Percentual dos rendimentos',
  VALOR_FIXO: 'Valor fixo',
}

/** Mensagem para o campo do dia do vencimento, ou '' se o dia vale. Vazio fica a cargo do `required`. */
export function erroDoDiaVencimento(texto: string): string {
  const dia = texto.trim()
  if (!dia) return ''
  if (!/^\d{1,2}$/.test(dia) || Number(dia) < 1 || Number(dia) > 31) {
    return 'Informe um dia de 1 a 31, só com números (ex.: 10).'
  }
  return ''
}

/**
 * Mensagem para o primeiro valor fixo, ou '' se ele cobre o início da pensão. Os outros podem
 * começar em qualquer mês: cada um vale até o próximo.
 */
export function erroDosValoresFixos(p: Pick<PensaoEditavel, 'valoresFixos' | 'primeiroMes'>): string {
  const meses = p.valoresFixos.map((v) => v.aPartirDe).filter(Boolean)
  if (meses.length === 0 || !p.primeiroMes) return ''
  if (new Set(meses).size !== meses.length) return 'Há dois valores a partir do mesmo mês.'
  const primeiro = [...meses].sort()[0]
  return primeiro > p.primeiroMes
    ? `O primeiro valor começa em ${mes(primeiro)}, depois da primeira parcela (${mes(p.primeiroMes)}).`
    : ''
}

/**
 * Mensagem para a primeira mudança dos rendimentos, ou '' se elas valem. Cada mudança fica depois
 * da primeira parcela, quando valem os rendimentos iniciais, e até a última.
 */
export function erroDasMudancasDosRendimentos(
  p: Pick<PensaoEditavel, 'mudancasRendimentos' | 'primeiroMes' | 'ultimoMes'>,
): string {
  const meses = p.mudancasRendimentos.map((m) => m.aPartirDe).filter(Boolean)
  if (new Set(meses).size !== meses.length) return 'Há duas mudanças a partir do mesmo mês.'
  if (p.primeiroMes && meses.some((m) => m <= p.primeiroMes)) {
    return `A mudança deve começar depois da primeira parcela (${mes(p.primeiroMes)}); para o início, use o campo Rendimentos.`
  }
  if (p.ultimoMes && meses.some((m) => m > p.ultimoMes)) {
    return `A mudança deve começar até a última parcela (${mes(p.ultimoMes)}).`
  }
  return ''
}

/**
 * Mensagem para a primeira mudança da base, ou '' se elas valem. Cada mudança fica depois da
 * primeira parcela e até a última, e as mudanças dos rendimentos e os valores fixos da base inicial
 * ficam antes da primeira mudança da base.
 */
export function erroDasMudancasDaBase(
  p: Pick<PensaoEditavel, 'base' | 'mudancasBase' | 'mudancasRendimentos' | 'valoresFixos' | 'primeiroMes' | 'ultimoMes'>,
): string {
  const meses = p.mudancasBase.map((m) => m.aPartirDe).filter(Boolean)
  if (meses.length === 0) return ''
  if (new Set(meses).size !== meses.length) return 'Há duas mudanças da base a partir do mesmo mês.'
  if (p.primeiroMes && meses.some((m) => m <= p.primeiroMes)) {
    return `A mudança deve começar depois da primeira parcela (${mes(p.primeiroMes)}); para o início, use o valor fixado no título.`
  }
  if (p.ultimoMes && meses.some((m) => m > p.ultimoMes)) {
    return `A mudança deve começar até a última parcela (${mes(p.ultimoMes)}).`
  }
  const primeira = [...meses].sort()[0]
  const internas = p.base === 'RENDIMENTOS' ? p.mudancasRendimentos : p.base === 'VALOR_FIXO' ? p.valoresFixos : []
  const depois = internas.map((m) => m.aPartirDe).find((m) => m && m >= primeira)
  if (depois) {
    const [oQue, pronome] = p.base === 'RENDIMENTOS' ? ['A mudança dos rendimentos', 'a'] : ['O valor fixo', 'o']
    return `${oQue} a partir de ${mes(depois)} cai depois da mudança da base em ${mes(primeira)}; informe-${pronome} como mudança da base.`
  }
  return ''
}

function paraMudancaBase(m: MudancaBaseEditavel): MudancaBaseRequest {
  const rotulo = `a partir de ${m.aPartirDe ? mes(m.aPartirDe) : '…'}`
  switch (m.base) {
    case 'SALARIO_MINIMO':
      return { aPartirDe: m.aPartirDe, base: m.base, percentual: exigirDecimal(m.percentual, `% do salário mínimo ${rotulo}`, false) }
    case 'RENDIMENTOS':
      return {
        aPartirDe: m.aPartirDe,
        base: m.base,
        percentual: exigirDecimal(m.percentual, `% dos rendimentos ${rotulo}`, false),
        rendimentos: exigirDecimal(m.rendimentos, `Rendimentos ${rotulo}`),
      }
    case 'VALOR_FIXO':
      return { aPartirDe: m.aPartirDe, base: m.base, valor: exigirDecimal(m.valor, `Valor fixo ${rotulo}`) }
  }
}

/** @param separarRitos sem a separação, o ajuizamento não vai para a API */
export function paraPensao(p: PensaoEditavel, separarRitos: boolean, ritosPelaLei = false): PensaoRequest {
  const comPercentual = p.base !== 'VALOR_FIXO'
  return {
    // Salário mínimo vai sem base, como nos cálculos salvos antes das outras.
    ...(p.base !== 'SALARIO_MINIMO' && { base: p.base }),
    percentual: comPercentual ? exigirDecimal(p.percentual, `% ${p.base === 'RENDIMENTOS' ? 'dos rendimentos' : 'do salário mínimo'}`, false) : null,
    ...(p.base === 'RENDIMENTOS' && { rendimentos: exigirDecimal(p.rendimentos, 'Rendimentos') }),
    ...(p.base === 'RENDIMENTOS' &&
      p.mudancasRendimentos.length > 0 && {
        mudancasRendimentos: p.mudancasRendimentos.map((m) => ({
          aPartirDe: m.aPartirDe,
          valor: exigirDecimal(m.valor, 'Rendimentos a partir de'),
        })),
      }),
    ...(p.base === 'VALOR_FIXO' && {
      valoresFixos: p.valoresFixos.map((v) => ({ aPartirDe: v.aPartirDe, valor: exigirDecimal(v.valor, 'Valor fixo') })),
    }),
    ...(p.mudancasBase.length > 0 && { mudancasBase: p.mudancasBase.map(paraMudancaBase) }),
    ...(p.decimoTerceiro && { decimoTerceiro: true }),
    diaVencimento: Number(p.diaVencimento.trim()),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.inicioJuros === 'CITACAO' ? p.citacao : null,
    pagamentos: p.pagamentos.map((pg) => ({ ...pg, valor: exigirDecimal(pg.valor, 'Valor pago') })),
    // Só com pagamentos, para que os pedidos sem eles fiquem como antes da opção.
    ...(p.pagamentos.length > 0 && { excedentePagamento: p.pagoAMaiorComoCredito ? 'CREDITO_NO_MES' : 'RECUSAR' }),
    ...(p.abatimentos.length > 0 && {
      abatimentos: p.abatimentos.map((a) => ({
        data: a.data,
        valor: exigirDecimal(a.valor, 'Valor abatido'),
        origem: a.origem.trim(),
      })),
    }),
    ajuizamento: separarRitos && p.ajuizamento ? p.ajuizamento : null,
    // A tela manda sempre a regra ao separar; sem ela, a API seguiria a lei.
    ...(separarRitos && p.ajuizamento && { regraRitos: ritosPelaLei ? 'LEI' : 'ULTIMOS_MESES_DO_CALCULO' }),
  }
}

/** O mês anterior, em AAAA-MM. */
function mesAnterior(aaaaMm: string): string {
  const [ano, m] = aaaaMm.split('-').map(Number)
  return m === 1 ? `${ano - 1}-12` : `${ano}-${String(m - 1).padStart(2, '0')}`
}

/** " até 01/2024; R$ 3.500,00 a partir de 02/2024", depois do valor inicial; sem mudanças, "iguais em todos os meses". */
function descreverMudancas(mudancas: ValorFixoRequest[]): string {
  if (mudancas.length === 0) return ', iguais em todos os meses'
  const ordenadas = [...mudancas].sort((a, b) => a.aPartirDe.localeCompare(b.aPartirDe))
  return ` até ${mes(mesAnterior(ordenadas[0].aPartirDe))}${ordenadas
    .map((m) => `; ${moeda(Number(m.valor))} a partir de ${mes(m.aPartirDe)}`)
    .join('')}`
}

/** "; a partir de 01/2026, 20% do salário mínimo vigente em cada mês", uma por mudança da base. */
function descreverMudancasDaBase(mudancas: MudancaBaseRequest[]): string {
  return [...mudancas]
    .sort((a, b) => a.aPartirDe.localeCompare(b.aPartirDe))
    .map((m) => {
      const base =
        m.base === 'SALARIO_MINIMO'
          ? `${paraCampo(String(m.percentual))}% do salário mínimo vigente em cada mês`
          : m.base === 'RENDIMENTOS'
            ? `${paraCampo(String(m.percentual))}% dos rendimentos de ${moeda(Number(m.rendimentos))}`
            : `valor fixo de ${moeda(Number(m.valor))}, sem reajuste`
      return `; a partir de ${mes(m.aPartirDe)}, ${base}`
    })
    .join('')
}

/** A pensão do pedido em uma frase, igual na tela e no PDF. */
export function descreverPensao(p: PensaoRequest): string {
  return descreverBaseInicial(p) + descreverMudancasDaBase(p.mudancasBase ?? [])
}

function descreverBaseInicial(p: PensaoRequest): string {
  const mudaABase = (p.mudancasBase ?? []).length > 0
  switch (p.base ?? 'SALARIO_MINIMO') {
    case 'SALARIO_MINIMO':
      return `${paraCampo(String(p.percentual))}% do salário mínimo vigente em cada mês`
    case 'RENDIMENTOS':
      return `${paraCampo(String(p.percentual))}% dos rendimentos de ${moeda(Number(p.rendimentos))}${
        mudaABase && !(p.mudancasRendimentos ?? []).length ? '' : descreverMudancas(p.mudancasRendimentos ?? [])
      }`
    case 'VALOR_FIXO':
      return `Valor fixo, sem reajuste: ${[...(p.valoresFixos ?? [])]
        .sort((a, b) => a.aPartirDe.localeCompare(b.aPartirDe))
        .map((v) => `${moeda(Number(v.valor))} a partir de ${mes(v.aPartirDe)}`)
        .join('; ')}`
  }
}
