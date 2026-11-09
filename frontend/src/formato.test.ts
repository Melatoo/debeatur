import { describe, expect, it } from 'vitest'
import { dataHora } from './formato'

describe('dataHora', () => {
  it('escreve dd/mm/aaaa, hh:mm', () => {
    expect(dataHora(new Date(2025, 2, 5, 9, 7))).toBe('05/03/2025, 09:07')
  })
})
