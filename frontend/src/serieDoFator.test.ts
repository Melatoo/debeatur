import { describe, expect, it } from 'vitest'
import type { Regra, SerieUsada } from './api'
import { mesesDoFator, seriesDosMeses } from './serieDoFator'

const serie = (nome: string, valores: Record<string, number>): SerieUsada => ({
  serie: nome,
  descricao: `${nome} (IBGE)`,
  fonte: 'SGS/BCB',
  capturadaEm: null,
  snapshot: false,
  valores: Object.entries(valores).map(([mes, valor]) => ({ mes, valor })),
})

const regra = (inicio: string, fim: string | null, correcao: Regra['correcao']): Regra => ({
  inicio,
  fim,
  correcao,
  juros: 'TAXA_FIXA',
  percentualJurosMensal: 1,
  ultimoDiaTaxaFixa: null,
})

const inpc = serie('INPC', { '2024-06': 0.25, '2024-07': 0.26 })
const ipca = serie('IPCA', { '2024-08': -0.02, '2024-09': 0.44 })
const regras = [regra('2024-01', '2024-07', 'INPC'), regra('2024-08', null, 'IPCA')]

describe('mesesDoFator', () => {
  it('vai do mês da correção ao anterior à data-base, com o índice da regra de cada mês', () => {
    const meses = mesesDoFator('2024-06-10', '2024-10-15', regras, [inpc, ipca])

    expect(meses.map((m) => [m.mes, m.serie?.serie, m.variacao])).toEqual([
      ['2024-06', 'INPC', 0.25],
      ['2024-07', 'INPC', 0.26],
      ['2024-08', 'IPCA', -0.02],
      ['2024-09', 'IPCA', 0.44],
    ])
  })

  it('não tem mês quando a parcela vence no mês da data-base', () => {
    expect(mesesDoFator('2024-10-10', '2024-10-31', regras, [inpc, ipca])).toEqual([])
  })

  it('deixa em branco o que a resposta não traz', () => {
    const [mes] = mesesDoFator('2023-12-10', '2024-01-31', regras, [inpc, ipca])
    expect(mes).toEqual({ mes: '2023-12', serie: null, variacao: null })
    expect(mesesDoFator('2024-05-10', '2024-06-30', regras, [inpc, ipca])[0].variacao).toBeNull()
  })
})

describe('seriesDosMeses', () => {
  it('lista cada série uma vez, na ordem dos meses', () => {
    const meses = mesesDoFator('2024-06-10', '2024-10-15', regras, [ipca, inpc])
    expect(seriesDosMeses(meses).map((s) => s.serie)).toEqual(['INPC', 'IPCA'])
  })
})
