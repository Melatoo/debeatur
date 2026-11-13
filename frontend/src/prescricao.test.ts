import { describe, expect, it } from 'vitest'
import { competenciasQuitadas, fimDoPrazo, parcelasAntigas, textoDoAviso, vencimento } from './prescricao'

const pensao = { primeiroMes: '2021-01', ultimoMes: '2024-06', diaVencimento: 10 }

describe('vencimento', () => {
  it('cai no dia escolhido', () => {
    expect(vencimento('2024-03', 10)).toBe('2024-03-10')
  })

  it('cai no último dia do mês sem o dia escolhido', () => {
    expect(vencimento('2023-02', 31)).toBe('2023-02-28')
    expect(vencimento('2024-02', 31)).toBe('2024-02-29')
  })
})

describe('fimDoPrazo', () => {
  it('acaba no mesmo dia, dois anos depois', () => {
    expect(fimDoPrazo('2022-02-28')).toBe('2024-02-28')
  })

  it('de 29/02, sem o dia correspondente, acaba em 01/03 (art. 132, §3º, do CC)', () => {
    expect(fimDoPrazo('2024-02-29')).toBe('2026-03-01')
  })
})

describe('parcelasAntigas', () => {
  it('pega as vencidas mais de dois anos antes do ajuizamento', () => {
    expect(parcelasAntigas(pensao, '2023-03-15', '2024-06-30')).toEqual({
      meses: ['2021-01', '2021-02', '2021-03'],
      marco: '2023-03-15',
      porAjuizamento: true,
    })
  })

  it('não pega a vencida exatamente dois anos antes: o prazo acaba no dia do ajuizamento', () => {
    expect(parcelasAntigas(pensao, '2023-01-10', '2024-06-30')).toBeNull()
    expect(parcelasAntigas(pensao, '2023-01-11', '2024-06-30')?.meses).toEqual(['2021-01'])
  })

  it('conta 29/02 como o art. 132, §3º, do CC', () => {
    // Vencida em 28/02/2022: o prazo acabou em 28/02/2024, antes do ajuizamento em 29/02/2024.
    const fevereiro = { primeiroMes: '2022-02', ultimoMes: '2022-02', diaVencimento: 28 }
    expect(parcelasAntigas(fevereiro, '2024-02-29', '2024-06-30')?.meses).toEqual(['2022-02'])
    // Vencida em 29/02/2024: o prazo acaba em 01/03/2026, o próprio dia do ajuizamento.
    const bissexto = { primeiroMes: '2024-02', ultimoMes: '2024-02', diaVencimento: 29 }
    expect(parcelasAntigas(bissexto, '2026-03-01', '2026-06-30')).toBeNull()
    expect(parcelasAntigas(bissexto, '2026-03-02', '2026-06-30')?.meses).toEqual(['2024-02'])
  })

  it('deixa de fora as quitadas, que não têm saldo a prescrever', () => {
    expect(parcelasAntigas(pensao, '2023-04-15', '2024-06-30', new Set(['2021-02']))?.meses).toEqual([
      '2021-01',
      '2021-03',
      '2021-04',
    ])
    expect(parcelasAntigas(pensao, '2023-01-15', '2024-06-30', new Set(['2021-01']))).toBeNull()
  })

  it('sem o ajuizamento, conta da data-base', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '2022-05' }, null, '2024-06-30')).toEqual({
      meses: ['2022-05', '2022-06'],
      marco: '2024-06-30',
      porAjuizamento: false,
    })
  })

  it('com os ritos separados e o ajuizamento ainda vazio, conta da data-base', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '2022-05' }, '', '2024-06-30')?.porAjuizamento).toBe(false)
  })

  it('não avisa sem parcela antiga', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '2023-01' }, '2024-09-15', '2025-06-30')).toBeNull()
  })

  it('não avisa enquanto o formulário não basta para dizer', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '' }, null, '2024-06-30')).toBeNull()
    expect(parcelasAntigas({ ...pensao, diaVencimento: Number.NaN }, null, '2024-06-30')).toBeNull()
    expect(parcelasAntigas({ ...pensao, diaVencimento: 10.5 }, null, '2024-06-30')).toBeNull()
    expect(parcelasAntigas(pensao, null, '')).toBeNull()
    expect(parcelasAntigas(pensao, '2023-03-15', '')?.marco).toBe('2023-03-15')
  })
})

describe('competenciasQuitadas', () => {
  it('pega só as parcelas da pensão pagas por inteiro', () => {
    const linha = (mes: string, valorOriginal: number, valorPago: number) => ({ valorOriginal, pensao: { mes, valorPago } })
    expect(
      competenciasQuitadas([
        linha('2021-01', 0, 400),
        linha('2021-02', 200, 200),
        linha('2021-03', 400, 0),
        { valorOriginal: 0, pensao: null },
      ]),
    ).toEqual(new Set(['2021-01']))
  })
})

describe('textoDoAviso', () => {
  it('diz quais parcelas, desde quando e as exceções', () => {
    const { fato, detalhe } = textoDoAviso({ meses: ['2021-01', '2021-02'], marco: '2023-03-15', porAjuizamento: true })
    expect(fato).toBe('2 parcelas (01/2021 a 02/2021) podem estar prescritas.')
    expect(detalhe).toMatch(/^Venceram mais de dois anos antes do ajuizamento \(15\/03\/2023\) \(art\. 206, §2º, do CC\)/)
    expect(detalhe).toContain('menor de 16 anos (arts. 198, I, e 3º do CC)')
    expect(detalhe).toContain('art. 197, II')
  })

  it('separa as faixas quando há quitadas no meio', () => {
    const { fato } = textoDoAviso({ meses: ['2021-01', '2021-03', '2021-04', '2021-06'], marco: '2023-08-15', porAjuizamento: true })
    expect(fato).toBe('4 parcelas (01/2021, 03/2021 a 04/2021 e 06/2021) podem estar prescritas.')
  })

  it('sem o ajuizamento, condiciona o aviso a a execução ainda não ter sido proposta', () => {
    const { fato, detalhe } = textoDoAviso({ meses: ['2022-05'], marco: '2024-06-30', porAjuizamento: false })
    expect(fato).toBe('Se a execução ainda não foi proposta, 1 parcela (05/2022) pode estar prescrita.')
    expect(detalhe).toMatch(/^Venceu mais de dois anos antes da data-base \(30\/06\/2024\)/)
    expect(detalhe).toContain('a inclui')
  })
})
