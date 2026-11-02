import { describe, expect, it } from 'vitest'
import { pensaoVazia } from './pensao'
import { CHAVE_RASCUNHO, apagarRascunho, lerRascunho, salvarRascunho, type Rascunho } from './rascunho'

function memoria() {
  const dados = new Map<string, string>()
  return {
    dados,
    getItem: (k: string) => dados.get(k) ?? null,
    setItem: (k: string, v: string) => void dados.set(k, v),
    removeItem: (k: string) => void dados.delete(k),
  }
}

const vazio: Rascunho = {
  processo: { numero: '', juizo: '', exequente: '', executado: '' },
  dataBase: '2026-10-31',
  aplicarArt523: true,
  comPensao: true,
  separarRitos: true,
  pensao: pensaoVazia(),
  parcelas: [],
  outrasAbertas: false,
  regimeProprio: false,
  nomeRegime: '',
  regras: [],
}

const preenchido: Rascunho = {
  ...vazio,
  processo: { ...vazio.processo, exequente: 'Fulana' },
  pensao: { ...pensaoVazia(), percentual: '30', primeiroMes: '2025-01', ultimoMes: '2026-10' },
}

describe('rascunho', () => {
  it('devolve o que foi salvo, com o momento do salvamento', () => {
    const armazenamento = memoria()
    const agora = new Date('2026-11-02T14:30:00Z')
    salvarRascunho(armazenamento, preenchido, vazio, agora)
    expect(lerRascunho(armazenamento)).toEqual({ rascunho: preenchido, salvoEm: agora })
  })

  it('não guarda o formulário em branco, e apaga o que havia', () => {
    const armazenamento = memoria()
    salvarRascunho(armazenamento, preenchido, vazio)
    salvarRascunho(armazenamento, { ...vazio, dataBase: '2026-11-30' }, vazio)
    expect(armazenamento.dados.has(CHAVE_RASCUNHO)).toBe(false)
  })

  it('descarta rascunho corrompido, de outra versão ou com forma inesperada', () => {
    const armazenamento = memoria()
    for (const texto of [
      '{',
      'null',
      JSON.stringify({ versao: 0, salvoEm: '', rascunho: preenchido }),
      JSON.stringify({ versao: 1, salvoEm: '', rascunho: { ...preenchido, parcelas: 'x' } }),
      JSON.stringify({ versao: 1, salvoEm: '', rascunho: { ...preenchido, pensao: { ...preenchido.pensao, pagamentos: null } } }),
    ]) {
      armazenamento.setItem(CHAVE_RASCUNHO, texto)
      expect(lerRascunho(armazenamento)).toBeNull()
    }
  })

  it('segue sem armazenamento, ou com um que lança exceção', () => {
    const quebrado = {
      getItem: () => {
        throw new Error('bloqueado')
      },
      setItem: () => {
        throw new Error('cota')
      },
      removeItem: () => {
        throw new Error('bloqueado')
      },
    }
    expect(lerRascunho(null)).toBeNull()
    expect(lerRascunho(quebrado)).toBeNull()
    expect(() => salvarRascunho(quebrado, preenchido, vazio)).not.toThrow()
    expect(() => apagarRascunho(quebrado)).not.toThrow()
  })

  it('apaga o rascunho', () => {
    const armazenamento = memoria()
    salvarRascunho(armazenamento, preenchido, vazio)
    apagarRascunho(armazenamento)
    expect(lerRascunho(armazenamento)).toBeNull()
  })
})
