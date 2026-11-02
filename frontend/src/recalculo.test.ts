import { describe, expect, it } from 'vitest'
import type { CalculoRequest } from './api'
import { chaveDoPedido } from './recalculo'

describe('chaveDoPedido', () => {
  const pedido: CalculoRequest = {
    dataBase: '2026-10-31',
    aplicarArt523: true,
    parcelas: [],
    processo: { numero: '1', juizo: '', exequente: '', executado: '' },
  }

  it('ignora o processo, que só identifica o PDF', () => {
    const outroProcesso = { ...pedido, processo: { numero: '2', juizo: 'Vara', exequente: '', executado: '' } }
    expect(chaveDoPedido(outroProcesso)).toBe(chaveDoPedido(pedido))
    expect(chaveDoPedido({ ...pedido, processo: undefined })).toBe(chaveDoPedido(pedido))
  })

  it('muda com qualquer dado do cálculo', () => {
    expect(chaveDoPedido({ ...pedido, dataBase: '2026-11-30' })).not.toBe(chaveDoPedido(pedido))
    expect(chaveDoPedido({ ...pedido, aplicarArt523: false })).not.toBe(chaveDoPedido(pedido))
  })
})
