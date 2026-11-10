import { describe, expect, it } from 'vitest'
import { erroDoDiaVencimento } from './pensao'

describe('erroDoDiaVencimento', () => {
  it.each(['1', '10', '31', '05', ' 7 '])('aceita %j', (dia) => {
    expect(erroDoDiaVencimento(dia)).toBe('')
  })

  it('deixa o vazio para o required', () => {
    expect(erroDoDiaVencimento('')).toBe('')
  })

  it.each(['0', '32', '10,5', '1.0', 'dez', '-3', '100'])('recusa %j com exemplo', (dia) => {
    expect(erroDoDiaVencimento(dia)).toBe('Informe um dia de 1 a 31, só com números (ex.: 10).')
  })
})
