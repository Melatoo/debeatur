import { describe, expect, it } from 'vitest'
import { CHAVE_COLUNAS, lerColunasOcultas, salvarColunasOcultas } from './colunas'

function memoria() {
  const dados = new Map<string, string>()
  return { getItem: (k: string) => dados.get(k) ?? null, setItem: (k: string, v: string) => void dados.set(k, v) }
}

describe('colunas ocultas', () => {
  it('guarda e lê as colunas escondidas, na ordem da tabela', () => {
    const armazenamento = memoria()
    salvarColunasOcultas(armazenamento, ['juros', 'fator'])
    expect(lerColunasOcultas(armazenamento)).toEqual(['fator', 'juros'])
  })

  it('ignora colunas desconhecidas ou fixas e valores corrompidos', () => {
    const armazenamento = memoria()
    armazenamento.setItem(CHAVE_COLUNAS, JSON.stringify(['fator', 'parcela', 'atualizado', 7]))
    expect(lerColunasOcultas(armazenamento)).toEqual(['fator'])
    armazenamento.setItem(CHAVE_COLUNAS, '{')
    expect(lerColunasOcultas(armazenamento)).toEqual([])
    expect(lerColunasOcultas(null)).toEqual([])
  })
})
