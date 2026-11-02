import { describe, expect, it } from 'vitest'
import { casasDecimais, exigirDecimal, lerDecimal, paraCampo, pontoAmbiguo } from './numero'

describe('lerDecimal em reais', () => {
  it.each([
    ['1234', '1234'],
    ['1234,56', '1234.56'],
    ['1.234,56', '1234.56'],
    ['R$ 1.234,56', '1234.56'],
    ['R$1.234.567,8', '1234567.8'],
    ['  R$ 1.234,56  ', '1234.56'],
    ['1.500,00', '1500.00'],
    ['1.500.000', '1500000'],
    ['1234.56', '1234.56'],
    ['1.23', '1.23'],
    ['0,50', '0.50'],
  ])('lê %j como %j', (texto, esperado) => {
    expect(lerDecimal(texto)).toBe(esperado)
  })

  it('recusa um só ponto seguido de três dígitos, que tanto é milhar quanto decimal', () => {
    expect(lerDecimal('1.500')).toBeNull()
    expect(lerDecimal('2.500')).toBeNull()
    expect(pontoAmbiguo('1.500')).toBe(true)
    expect(pontoAmbiguo('R$ 1.500')).toBe(true)
  })

  it('não acusa ambiguidade onde o ponto só tem uma leitura', () => {
    expect(pontoAmbiguo('1.500,00')).toBe(false)
    expect(pontoAmbiguo('1.50')).toBe(false)
    expect(pontoAmbiguo('0.500')).toBe(false)
  })

  it('não lê como milhar um primeiro grupo que começa com zero', () => {
    // Fica como decimal, e o campo o barra por ter casas demais.
    expect(lerDecimal('0.500')).toBe('0.500')
    expect(lerDecimal('00.500')).toBe('00.500')
  })

  it.each(['', 'abc', '1,234.56', '1.2.3', '-10', '10,', ',5', '1.23,4.5', '12.34,56', '100 50', '1 234,56', '12R$34', '10 R$'])('recusa %j', (texto) => {
    expect(lerDecimal(texto)).toBeNull()
  })
})

describe('lerDecimal em percentuais', () => {
  it('lê o ponto sempre como decimal', () => {
    expect(lerDecimal('1.5', false)).toBe('1.5')
    expect(lerDecimal('1.000', false)).toBe('1.000')
    expect(lerDecimal('0,5', false)).toBe('0.5')
  })

  it('recusa o ponto de milhar com vírgula decimal', () => {
    expect(lerDecimal('1.000,5', false)).toBeNull()
  })
})

describe('casasDecimais', () => {
  it('conta as casas do texto lido', () => {
    expect(casasDecimais('1234')).toBe(0)
    expect(casasDecimais('1234.5')).toBe(1)
    expect(casasDecimais('0.500')).toBe(3)
  })
})

describe('exigirDecimal', () => {
  it('converte o texto válido', () => {
    expect(exigirDecimal('1.234,56', 'Valor')).toBe('1234.56')
    expect(exigirDecimal('1.5', 'Percentual', false)).toBe('1.5')
  })

  it('não deixa o texto inválido seguir para a API', () => {
    expect(() => exigirDecimal('abc', 'Valor pago')).toThrow('Valor pago inválido: abc')
    expect(() => exigirDecimal('1.500', 'Valor pago')).toThrow('Valor pago inválido: 1.500')
  })
})

describe('paraCampo', () => {
  it('mostra o decimal da API com vírgula', () => {
    expect(paraCampo(1)).toBe('1')
    expect(paraCampo(0.5)).toBe('0,5')
    expect(paraCampo('0.3333')).toBe('0,3333')
  })
})
