import { describe, expect, it } from 'vitest'
import { casasDecimais, lerDecimal, paraCampo } from './numero'
import { paraRegime, type RegraEditavel } from './regime'

describe('lerDecimal em reais', () => {
  it.each([
    ['1234', '1234'],
    ['1234,56', '1234.56'],
    ['1.234,56', '1234.56'],
    ['R$ 1.234,56', '1234.56'],
    ['R$1.234.567,8', '1234567.8'],
    ['1234.56', '1234.56'],
    ['1.23', '1.23'],
    ['0,50', '0.50'],
  ])('lê %j como %j', (texto, esperado) => {
    expect(lerDecimal(texto)).toBe(esperado)
  })

  it('lê o ponto seguido de três dígitos como milhar', () => {
    expect(lerDecimal('1.500')).toBe('1500')
    expect(lerDecimal('1.234')).toBe('1234')
  })

  it('não lê como milhar um primeiro grupo que começa com zero', () => {
    // Fica como decimal, e o campo o barra por ter casas demais.
    expect(lerDecimal('0.500')).toBe('0.500')
    expect(lerDecimal('00.500')).toBe('00.500')
  })

  it.each(['', 'abc', '1,234.56', '1.2.3', '-10', '10,', ',5', '1.23,4.5', '12.34,56'])('recusa %j', (texto) => {
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

describe('paraCampo', () => {
  it('mostra o decimal da API com vírgula', () => {
    expect(paraCampo(1)).toBe('1')
    expect(paraCampo(0.5)).toBe('0,5')
    expect(paraCampo('0.3333')).toBe('0,3333')
  })
})

describe('paraRegime', () => {
  const regra = (percentual: string): RegraEditavel => ({
    inicio: '2020-01',
    fim: '',
    correcao: 'INPC',
    juros: 'TAXA_FIXA',
    percentual,
    ultimoDiaTaxaFixa: '',
  })

  it('converte o percentual com vírgula', () => {
    expect(paraRegime('', [regra('0,5')]).regras[0].percentualJurosMensal).toBe(0.5)
  })

  it('não monta o pedido com percentual inválido', () => {
    expect(() => paraRegime('', [regra('meio')])).toThrow('Percentual de juros inválido: meio')
  })
})
