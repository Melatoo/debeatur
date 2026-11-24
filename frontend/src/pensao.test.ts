import { describe, expect, it } from 'vitest'
import type { PensaoRequest } from './api'
import { descreverPensao, erroDasMudancasDosRendimentos, erroDoDiaVencimento, erroDosValoresFixos, paraPensao, pensaoVazia } from './pensao'

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

describe('paraPensao', () => {
  const base = { ...pensaoVazia(), primeiroMes: '2025-01', ultimoMes: '2025-12' }

  it('em salário mínimo, sem 13º nem abatimentos, não manda os campos novos', () => {
    const pedido = paraPensao({ ...base, percentual: '30' }, false)
    expect(pedido).not.toHaveProperty('base')
    expect(pedido).not.toHaveProperty('decimoTerceiro')
    expect(pedido).not.toHaveProperty('abatimentos')
    expect(pedido.percentual).toBe('30')
  })

  it('em rendimentos, manda o percentual, os rendimentos e o 13º', () => {
    const pedido = paraPensao({ ...base, base: 'RENDIMENTOS', percentual: '33,33', rendimentos: '3.000,00', decimoTerceiro: true }, false)
    expect(pedido).toMatchObject({ base: 'RENDIMENTOS', percentual: '33.33', rendimentos: '3000.00', decimoTerceiro: true })
    expect(pedido).not.toHaveProperty('valoresFixos')
    expect(pedido).not.toHaveProperty('mudancasRendimentos')
  })

  it('em rendimentos, manda as mudanças; nas outras bases, não', () => {
    const mudancasRendimentos = [{ aPartirDe: '2025-06', valor: '3.500,00' }]
    const pedido = paraPensao({ ...base, base: 'RENDIMENTOS', percentual: '30', rendimentos: '3.000,00', mudancasRendimentos }, false)
    expect(pedido.mudancasRendimentos).toEqual([{ aPartirDe: '2025-06', valor: '3500.00' }])
    expect(paraPensao({ ...base, percentual: '30', mudancasRendimentos }, false)).not.toHaveProperty('mudancasRendimentos')
  })

  it('manda o destino do pagamento a maior só com pagamentos', () => {
    expect(paraPensao({ ...base, percentual: '30' }, false)).not.toHaveProperty('excedentePagamento')
    const pagamentos = [{ mes: '2025-02', valor: '1.000,00' }]
    expect(paraPensao({ ...base, percentual: '30', pagamentos }, false).excedentePagamento).toBe('CREDITO_NO_MES')
    expect(
      paraPensao({ ...base, percentual: '30', pagamentos, pagoAMaiorComoCredito: false }, false).excedentePagamento,
    ).toBe('RECUSAR')
  })

  it('em valor fixo, manda os períodos sem percentual', () => {
    const pedido = paraPensao(
      { ...base, base: 'VALOR_FIXO', percentual: '30', valoresFixos: [{ aPartirDe: '2025-01', valor: '1.200,50' }] },
      false,
    )
    expect(pedido).toMatchObject({ base: 'VALOR_FIXO', percentual: null, valoresFixos: [{ aPartirDe: '2025-01', valor: '1200.50' }] })
  })

  it('manda os abatimentos com a origem aparada', () => {
    const pedido = paraPensao(
      { ...base, percentual: '30', abatimentos: [{ data: '2026-11-10', valor: '500,00', origem: ' Bloqueio ' }] },
      false,
    )
    expect(pedido.abatimentos).toEqual([{ data: '2026-11-10', valor: '500.00', origem: 'Bloqueio' }])
  })
})

describe('erroDosValoresFixos', () => {
  it('aceita o primeiro valor no início da pensão ou antes', () => {
    expect(erroDosValoresFixos({ primeiroMes: '2025-01', valoresFixos: [{ aPartirDe: '2024-12', valor: '1' }] })).toBe('')
  })

  it('recusa o primeiro valor depois da primeira parcela', () => {
    expect(
      erroDosValoresFixos({
        primeiroMes: '2025-01',
        valoresFixos: [
          { aPartirDe: '2025-06', valor: '1' },
          { aPartirDe: '2025-02', valor: '1' },
        ],
      }),
    ).toBe('O primeiro valor começa em 02/2025, depois da primeira parcela (01/2025).')
  })

  it('recusa dois valores a partir do mesmo mês', () => {
    expect(
      erroDosValoresFixos({
        primeiroMes: '2025-01',
        valoresFixos: [
          { aPartirDe: '2025-01', valor: '1' },
          { aPartirDe: '2025-01', valor: '2' },
        ],
      }),
    ).toBe('Há dois valores a partir do mesmo mês.')
  })
})

describe('descreverPensao', () => {
  const comum: Omit<PensaoRequest, 'percentual'> = {
    diaVencimento: 10,
    primeiroMes: '2025-01',
    ultimoMes: '2025-12',
    inicioJuros: 'VENCIMENTO',
    citacao: null,
    pagamentos: [],
    ajuizamento: null,
  }

  it('descreve cada base', () => {
    expect(descreverPensao({ ...comum, percentual: '30' })).toBe('30% do salário mínimo vigente em cada mês')
    expect(descreverPensao({ ...comum, base: 'RENDIMENTOS', percentual: '30', rendimentos: '3000' })).toMatch(
      /^30% dos rendimentos de R\$\s3\.000,00, iguais em todos os meses$/,
    )
    expect(
      descreverPensao({
        ...comum,
        base: 'RENDIMENTOS',
        percentual: '30',
        rendimentos: '3000',
        mudancasRendimentos: [
          { aPartirDe: '2025-09', valor: '2000' },
          { aPartirDe: '2025-03', valor: '3500' },
        ],
      }),
    ).toMatch(/^30% dos rendimentos de R\$\s3\.000,00 até 02\/2025; R\$\s3\.500,00 a partir de 03\/2025; R\$\s2\.000,00 a partir de 09\/2025$/)
    expect(
      descreverPensao({
        ...comum,
        base: 'VALOR_FIXO',
        percentual: null,
        valoresFixos: [
          { aPartirDe: '2025-06', valor: '650' },
          { aPartirDe: '2025-01', valor: '500' },
        ],
      }),
    ).toMatch(/^Valor fixo, sem reajuste: R\$\s500,00 a partir de 01\/2025; R\$\s650,00 a partir de 06\/2025$/)
  })
})

describe('erroDasMudancasDosRendimentos', () => {
  const periodo = { primeiroMes: '2025-01', ultimoMes: '2025-12' }
  const valor = '1'

  it('aceita mudanças entre o mês seguinte à primeira parcela e a última', () => {
    expect(erroDasMudancasDosRendimentos({ ...periodo, mudancasRendimentos: [{ aPartirDe: '2025-02', valor }, { aPartirDe: '2025-12', valor }] })).toBe('')
  })

  it('recusa duas mudanças no mesmo mês', () => {
    expect(erroDasMudancasDosRendimentos({ ...periodo, mudancasRendimentos: [{ aPartirDe: '2025-03', valor }, { aPartirDe: '2025-03', valor }] })).toBe(
      'Há duas mudanças a partir do mesmo mês.',
    )
  })

  it('recusa mudança na primeira parcela, antes dela ou depois da última', () => {
    expect(erroDasMudancasDosRendimentos({ ...periodo, mudancasRendimentos: [{ aPartirDe: '2025-01', valor }] })).toBe(
      'A mudança deve começar depois da primeira parcela (01/2025); para o início, use o campo Rendimentos.',
    )
    expect(erroDasMudancasDosRendimentos({ ...periodo, mudancasRendimentos: [{ aPartirDe: '2024-06', valor }] })).toContain('depois da primeira parcela')
    expect(erroDasMudancasDosRendimentos({ ...periodo, mudancasRendimentos: [{ aPartirDe: '2026-01', valor }] })).toBe(
      'A mudança deve começar até a última parcela (12/2025).',
    )
  })
})
