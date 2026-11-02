import { describe, expect, it } from 'vitest'
import { armazenamentoEmMemoria } from './armazenamentoEmMemoria'
import { CHAVE_COLUNAS, lerColunasOcultas, salvarColunasOcultas } from './colunas'

describe('colunas ocultas', () => {
  it('guarda e lê as colunas escondidas, na ordem da tabela', () => {
    const armazenamento = armazenamentoEmMemoria()
    salvarColunasOcultas(armazenamento, ['juros', 'fator'])
    expect(lerColunasOcultas(armazenamento)).toEqual(['fator', 'juros'])
  })

  it('ignora colunas desconhecidas ou fixas e valores corrompidos', () => {
    const armazenamento = armazenamentoEmMemoria()
    armazenamento.setItem(CHAVE_COLUNAS, JSON.stringify(['fator', 'parcela', 'atualizado', 7]))
    expect(lerColunasOcultas(armazenamento)).toEqual(['fator'])
    armazenamento.setItem(CHAVE_COLUNAS, '{')
    expect(lerColunasOcultas(armazenamento)).toEqual([])
    expect(lerColunasOcultas(null)).toEqual([])
  })
})
