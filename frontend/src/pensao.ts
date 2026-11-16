import type { AbatimentoRequest, InicioJuros, PagamentoRequest, PensaoRequest, TipoBase, ValorFixoRequest } from './api'
import { exigirDecimal, paraCampo } from './numero'
import { moeda, mes } from './formato'

/** Pensão como o formulário a guarda: campos em texto, como os inputs devolvem. */
export interface PensaoEditavel {
  base: TipoBase
  /** Percentual do salário mínimo ou dos rendimentos; ignorado no valor fixo. */
  percentual: string
  /** Só na base RENDIMENTOS. */
  rendimentos: string
  /** Só na base VALOR_FIXO. */
  valoresFixos: ValorFixoRequest[]
  decimoTerceiro: boolean
  diaVencimento: string
  primeiroMes: string
  ultimoMes: string
  inicioJuros: InicioJuros
  citacao: string
  pagamentos: PagamentoRequest[]
  abatimentos: AbatimentoRequest[]
  ajuizamento: string
}

export const pensaoVazia = (): PensaoEditavel => ({
  base: 'SALARIO_MINIMO',
  percentual: '',
  rendimentos: '',
  valoresFixos: [{ aPartirDe: '', valor: '' }],
  decimoTerceiro: false,
  diaVencimento: '10',
  primeiroMes: '',
  ultimoMes: '',
  inicioJuros: 'VENCIMENTO',
  citacao: '',
  pagamentos: [],
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

/** @param separarRitos sem a separação, o ajuizamento não vai para a API */
export function paraPensao(p: PensaoEditavel, separarRitos: boolean): PensaoRequest {
  const comPercentual = p.base !== 'VALOR_FIXO'
  return {
    // Salário mínimo vai sem base, como nos cálculos salvos antes das outras.
    ...(p.base !== 'SALARIO_MINIMO' && { base: p.base }),
    percentual: comPercentual ? exigirDecimal(p.percentual, `% ${p.base === 'RENDIMENTOS' ? 'dos rendimentos' : 'do salário mínimo'}`, false) : null,
    ...(p.base === 'RENDIMENTOS' && { rendimentos: exigirDecimal(p.rendimentos, 'Rendimentos') }),
    ...(p.base === 'VALOR_FIXO' && {
      valoresFixos: p.valoresFixos.map((v) => ({ aPartirDe: v.aPartirDe, valor: exigirDecimal(v.valor, 'Valor fixo') })),
    }),
    ...(p.decimoTerceiro && { decimoTerceiro: true }),
    diaVencimento: Number(p.diaVencimento.trim()),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.inicioJuros === 'CITACAO' ? p.citacao : null,
    pagamentos: p.pagamentos.map((pg) => ({ ...pg, valor: exigirDecimal(pg.valor, 'Valor pago') })),
    ...(p.abatimentos.length > 0 && {
      abatimentos: p.abatimentos.map((a) => ({
        data: a.data,
        valor: exigirDecimal(a.valor, 'Valor abatido'),
        origem: a.origem.trim(),
      })),
    }),
    ajuizamento: separarRitos && p.ajuizamento ? p.ajuizamento : null,
  }
}

/** A pensão do pedido em uma frase, igual na tela e no PDF. */
export function descreverPensao(p: PensaoRequest): string {
  switch (p.base ?? 'SALARIO_MINIMO') {
    case 'SALARIO_MINIMO':
      return `${paraCampo(String(p.percentual))}% do salário mínimo vigente em cada mês`
    case 'RENDIMENTOS':
      return `${paraCampo(String(p.percentual))}% dos rendimentos de ${moeda(Number(p.rendimentos))}, iguais em todos os meses`
    case 'VALOR_FIXO':
      return `Valor fixo, sem reajuste: ${[...(p.valoresFixos ?? [])]
        .sort((a, b) => a.aPartirDe.localeCompare(b.aPartirDe))
        .map((v) => `${moeda(Number(v.valor))} a partir de ${mes(v.aPartirDe)}`)
        .join('; ')}`
  }
}
