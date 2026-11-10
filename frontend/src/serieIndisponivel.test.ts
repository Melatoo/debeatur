import { describe, expect, it } from 'vitest'
import { avisoDeSerie, lerSerieIndisponivel, type SerieIndisponivel } from './serieIndisponivel'

// 10/11/2026: o índice de outubro pode ainda não ter saído.
const hoje = new Date(2026, 10, 10)
const ipca = (mes: string): SerieIndisponivel => ({ serie: 'IPCA', descricao: 'IPCA (IBGE)', mes })

describe('lerSerieIndisponivel', () => {
  it('lê o problema da série indisponível', () => {
    const problema = { type: '/problemas/serie-indisponivel', status: 422, serie: 'IPCA', descricao: 'IPCA (IBGE)', mes: '2026-10' }
    expect(lerSerieIndisponivel(problema)).toEqual(ipca('2026-10'))
  })

  it('ignora outros problemas e os incompletos', () => {
    expect(lerSerieIndisponivel({ type: 'about:blank', detail: 'x' })).toBeNull()
    expect(lerSerieIndisponivel({ type: '/problemas/serie-indisponivel', serie: 'IPCA' })).toBeNull()
    expect(lerSerieIndisponivel({ type: '/problemas/serie-indisponivel', serie: 'IPCA', descricao: 'IPCA', mes: '10/2026' })).toBeNull()
    expect(lerSerieIndisponivel(null)).toBeNull()
    expect(lerSerieIndisponivel('erro')).toBeNull()
  })
})

describe('avisoDeSerie', () => {
  it('sugere o último dia do mês que falta como data-base', () => {
    expect(avisoDeSerie(ipca('2026-10'), '2026-11-30', hoje)).toEqual({
      fato: 'IPCA (IBGE) de 10/2026 ainda não divulgado.',
      detalhe:
        'Os índices de um mês saem por volta do dia 10 do mês seguinte. Com a data-base até 31/10/2026, o cálculo não depende dele.',
      recente: true,
      dataBaseSugerida: '2026-10-31',
    })
  })

  it('acerta o último dia de fevereiro e o do mês corrente', () => {
    expect(avisoDeSerie(ipca('2024-02'), '2024-03-15', new Date(2024, 2, 5)).dataBaseSugerida).toBe('2024-02-29')
    expect(avisoDeSerie(ipca('2026-11'), '2026-12-31', hoje).dataBaseSugerida).toBe('2026-11-30')
  })

  it('não sugere data-base que não seja anterior à do pedido', () => {
    const aviso = avisoDeSerie(ipca('2026-10'), '2026-10-31', hoje)
    expect(aviso.recente).toBe(true)
    expect(aviso.dataBaseSugerida).toBeNull()
    expect(aviso.detalhe).not.toContain('data-base')
  })

  it('trata mês antigo como lacuna da série, sem sugestão', () => {
    const aviso = avisoDeSerie(ipca('2026-08'), '2026-10-31', hoje)
    expect(aviso).toMatchObject({ recente: false, dataBaseSugerida: null, fato: 'IPCA (IBGE) sem valor para 08/2026.' })
    expect(aviso.detalhe).toContain('regras de correção')
  })

  it('não manda recuar a data-base por causa do salário mínimo', () => {
    const salario: SerieIndisponivel = { serie: 'SALARIO_MINIMO', descricao: 'Salário mínimo', mes: '2026-11' }
    expect(avisoDeSerie(salario, '2026-11-30', hoje)).toMatchObject({
      fato: 'Salário mínimo de 11/2026 ainda não publicado.',
      recente: true,
      dataBaseSugerida: null,
    })
  })
})
