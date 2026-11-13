import { describe, expect, it } from 'vitest'
import { parcelasAntigas, textoDoAviso, vencimento } from './prescricao'

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

  it('sem o ajuizamento, conta da data-base', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '2022-05' }, null, '2024-06-30')).toEqual({
      meses: ['2022-05', '2022-06'],
      marco: '2024-06-30',
      porAjuizamento: false,
    })
  })

  it('não avisa sem parcela antiga', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '2023-01' }, '2024-09-15', '2025-06-30')).toBeNull()
  })

  it('não avisa enquanto o formulário não basta para dizer', () => {
    expect(parcelasAntigas({ ...pensao, primeiroMes: '' }, null, '2024-06-30')).toBeNull()
    expect(parcelasAntigas({ ...pensao, diaVencimento: Number.NaN }, null, '2024-06-30')).toBeNull()
    expect(parcelasAntigas(pensao, null, '')).toBeNull()
  })
})

describe('textoDoAviso', () => {
  it('diz quais parcelas, desde quando e as exceções', () => {
    const { fato, detalhe } = textoDoAviso({ meses: ['2021-01', '2021-02'], marco: '2023-03-15', porAjuizamento: true })
    expect(fato).toBe('2 parcelas de 01/2021 a 02/2021 podem estar prescritas.')
    expect(detalhe).toMatch(/^Venceram mais de dois anos antes do ajuizamento \(15\/03\/2023\) \(art\. 206, §2º, do CC\)/)
    expect(detalhe).toContain('art. 198, I, do CC')
    expect(detalhe).toContain('art. 197, II')
  })

  it('no singular, e com a data-base no lugar do ajuizamento', () => {
    const { fato, detalhe } = textoDoAviso({ meses: ['2022-05'], marco: '2024-06-30', porAjuizamento: false })
    expect(fato).toBe('1 parcela de 05/2022 pode estar prescrita.')
    expect(detalhe).toMatch(/^Venceu mais de dois anos antes da data-base \(30\/06\/2024\), que faz as vezes do ajuizamento/)
    expect(detalhe).toContain('a inclui')
  })
})
