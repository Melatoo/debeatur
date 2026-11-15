import { describe, expect, it } from 'vitest'
import type { LinhaDemonstrativo, Regra } from './api'
import { agostoNaViradaDaSelic, descreverPeriodo, paraRegime, regrasDoPeriodo, type RegraEditavel } from './regime'

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

describe('regrasDoPeriodo', () => {
  const regra = (inicio: string, fim: string | null): Regra => ({
    inicio,
    fim,
    correcao: 'INPC',
    juros: 'TAXA_FIXA',
    percentualJurosMensal: 1,
    ultimoDiaTaxaFixa: null,
  })
  const padrao = [regra('1994-07', '2024-07'), regra('2024-08', '2024-08'), regra('2024-09', null)]
  const vencendoEm = (...datas: string[]) =>
    datas.map((d) => ({ inicioCorrecao: d, inicioJuros: d }) as LinhaDemonstrativo)
  const periodos = (regras: Regra[]) => regras.map(descreverPeriodo)

  it('corta o início da regra antiga ao primeiro mês do cálculo', () => {
    expect(periodos(regrasDoPeriodo(padrao, vencendoEm('2024-01-10', '2024-06-10'), '2025-03-01'))).toEqual([
      '01/2024 a 07/2024',
      '08/2024',
      'desde 09/2024',
    ])
  })

  it('corta o fim ao mês anterior à data-base e deixa de fora as regras posteriores', () => {
    expect(periodos(regrasDoPeriodo(padrao, vencendoEm('2023-01-10'), '2024-03-15'))).toEqual(['01/2023 a 02/2024'])
  })

  it('começa a regra sem fim no primeiro mês do cálculo', () => {
    expect(periodos(regrasDoPeriodo(padrao, vencendoEm('2025-05-10'), '2025-10-01'))).toEqual(['desde 05/2025'])
  })

  it('mostra só o mês da data-base quando tudo vence nele', () => {
    expect(periodos(regrasDoPeriodo(padrao, vencendoEm('2024-03-05'), '2024-03-20'))).toEqual(['03/2024'])
  })

  it('conta o termo inicial dos juros quando ele vem antes do vencimento', () => {
    const linhas = [{ inicioCorrecao: '2024-05-10', inicioJuros: '2023-11-01' } as LinhaDemonstrativo]
    expect(periodos(regrasDoPeriodo(padrao, linhas, '2024-07-01'))).toEqual(['11/2023 a 06/2024'])
  })
})
