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

/** @param separarRitos sem a separação, o ajuizamento não vai para a API */
export function paraPensao(p: PensaoEditavel, separarRitos: boolean): PensaoRequest {
  return {
    percentual: exigirDecimal(p.percentual, '% do salário mínimo', false),
    diaVencimento: Number(p.diaVencimento),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.inicioJuros === 'CITACAO' ? p.citacao : null,
    pagamentos: p.pagamentos.map((pg) => ({ ...pg, valor: exigirDecimal(pg.valor, 'Valor pago') })),
    ajuizamento: separarRitos && p.ajuizamento ? p.ajuizamento : null,
  }
}
