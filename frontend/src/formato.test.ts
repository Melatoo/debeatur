import { describe, expect, it } from 'vitest'
import { dataHora, percentual } from './formato'

describe('dataHora', () => {
  it('escreve dd/mm/aaaa, hh:mm', () => {
    expect(dataHora(new Date(2025, 2, 5, 9, 7))).toBe('05/03/2025, 09:07')
  })
})

describe('percentual', () => {
  it('escreve a taxa fixa com quatro casas', () => {
    expect(percentual(8)).toBe('8,0000%')
  })

  it('mantém as seis casas com que o BCB publica a taxa legal', () => {
    expect(percentual(0.512345)).toBe('0,512345%')
    expect(percentual(6.61086)).toBe('6,61086%')
  })
})
