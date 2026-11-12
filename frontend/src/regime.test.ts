import { describe, expect, it } from 'vitest'
import { agostoNaViradaDaSelic, paraRegime, type RegraEditavel } from './regime'

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

describe('agostoNaViradaDaSelic', () => {
  const selicAte = (fim: string, correcao: RegraEditavel['correcao'] = 'SELIC'): RegraEditavel => ({
    inicio: '2003-01',
    fim,
    correcao,
    juros: 'SEM_JUROS',
    percentual: '',
    ultimoDiaTaxaFixa: '',
  })

  it('deixa agosto/2024 com a SELIC quando ela vai até 08/2024', () => {
    expect(agostoNaViradaDaSelic(selicAte('2024-08'))).toBe('selic')
  })

  it('passa agosto/2024 para a regra seguinte quando a SELIC termina em 07/2024', () => {
    expect(agostoNaViradaDaSelic(selicAte('2024-07'))).toBe('regra-seguinte')
  })

  it('não se aplica a outros meses nem a outros índices', () => {
    expect(agostoNaViradaDaSelic(selicAte('2024-09'))).toBeNull()
    expect(agostoNaViradaDaSelic(selicAte(''))).toBeNull()
    expect(agostoNaViradaDaSelic(selicAte('2024-08', 'INPC'))).toBeNull()
  })
})
