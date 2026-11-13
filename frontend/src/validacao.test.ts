import { describe, expect, it } from 'vitest'
import { erroDoCampo, type CampoValidavel } from './validacao'

const VALIDO = {
  valid: true,
  valueMissing: false,
  badInput: false,
  customError: false,
  rangeUnderflow: false,
  rangeOverflow: false,
}

const campo = (
  type: string,
  validity: Partial<CampoValidavel['validity']>,
  extra: Partial<CampoValidavel> = {},
): CampoValidavel => ({
  type,
  validity: { ...VALIDO, valid: false, ...validity },
  validationMessage: 'balão do navegador',
  ...extra,
})

describe('erroDoCampo', () => {
  it('não acusa o campo válido', () => {
    expect(erroDoCampo({ type: 'text', validity: VALIDO, validationMessage: '' })).toBeNull()
  })

  it('usa a mensagem do setCustomValidity, que já diz o que fazer', () => {
    const c = campo('text', { customError: true }, { validationMessage: 'Informe um dia de 1 a 31, só com números (ex.: 10).' })
    expect(erroDoCampo(c)).toEqual({ mensagem: 'Informe um dia de 1 a 31, só com números (ex.: 10).', vazio: false })
  })

  it.each([
    ['date', 'Informe a data (ex.: 05/03/2025).'],
    ['month', 'Informe o mês e o ano (ex.: 03/2025).'],
    ['text', 'Preencha este campo.'],
  ])('troca o balão do campo %s vazio por uma mensagem com exemplo', (type, mensagem) => {
    expect(erroDoCampo(campo(type, { valueMissing: true }))).toEqual({ mensagem, vazio: true })
  })

  it('usa a mensagem própria do campo vazio, quando há uma', () => {
    expect(erroDoCampo(campo('text', { valueMissing: true }), 'Informe o percentual (ex.: 30).')).toEqual({
      mensagem: 'Informe o percentual (ex.: 30).',
      vazio: true,
    })
  })

  it('diz que a data digitada pela metade está incompleta, e não vazia', () => {
    const erro = erroDoCampo(campo('date', { badInput: true, valueMissing: true }))
    expect(erro).toEqual({ mensagem: 'Data incompleta ou inexistente. Informe dia, mês e ano (ex.: 05/03/2025).', vazio: false })
  })

  it('escreve os limites do mês como a tela', () => {
    expect(erroDoCampo(campo('month', { rangeUnderflow: true }, { min: '2024-01' }))?.mensagem).toBe('Informe 01/2024 ou depois.')
    expect(erroDoCampo(campo('month', { rangeOverflow: true }, { max: '2025-06' }))?.mensagem).toBe('Informe 06/2025 ou antes.')
  })

  it('escreve os limites da data como a tela', () => {
    expect(erroDoCampo(campo('date', { rangeUnderflow: true }, { min: '2024-08-01' }))?.mensagem).toBe(
      'Informe 01/08/2024 ou depois.',
    )
  })
})
