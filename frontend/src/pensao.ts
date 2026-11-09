import type { InicioJuros, PagamentoRequest, PensaoRequest } from './api'
import { exigirDecimal } from './numero'

/** Pensão como o formulário a guarda: campos em texto, como os inputs devolvem. */
export interface PensaoEditavel {
  percentual: string
  diaVencimento: string
  primeiroMes: string
  ultimoMes: string
  inicioJuros: InicioJuros
  citacao: string
  pagamentos: PagamentoRequest[]
  ajuizamento: string
}

export const pensaoVazia = (): PensaoEditavel => ({
  percentual: '',
  diaVencimento: '10',
  primeiroMes: '',
  ultimoMes: '',
  inicioJuros: 'VENCIMENTO',
  citacao: '',
  pagamentos: [],
  ajuizamento: '',
})

/** Mensagem para o campo do dia do vencimento, ou '' se o dia vale. Vazio fica a cargo do `required`. */
export function erroDoDiaVencimento(texto: string): string {
  const dia = texto.trim()
  if (!dia) return ''
  if (!/^\d{1,2}$/.test(dia) || Number(dia) < 1 || Number(dia) > 31) {
    return 'Informe um dia de 1 a 31, só com números (ex.: 10).'
  }
  return ''
}

/** @param separarRitos sem a separação, o ajuizamento não vai para a API */
export function paraPensao(p: PensaoEditavel, separarRitos: boolean): PensaoRequest {
  return {
    percentual: exigirDecimal(p.percentual, '% do salário mínimo', false),
    diaVencimento: Number(p.diaVencimento.trim()),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.inicioJuros === 'CITACAO' ? p.citacao : null,
    pagamentos: p.pagamentos.map((pg) => ({ ...pg, valor: exigirDecimal(pg.valor, 'Valor pago') })),
    ajuizamento: separarRitos && p.ajuizamento ? p.ajuizamento : null,
  }
}
