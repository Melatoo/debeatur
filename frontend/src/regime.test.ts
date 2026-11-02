import { describe, expect, it } from 'vitest'
import { paraRegime, type RegraEditavel } from './regime'

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
