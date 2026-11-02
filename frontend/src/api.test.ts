import { describe, expect, it } from 'vitest'
import { nomeDoAnexo } from './api'

describe('nomeDoAnexo', () => {
  it('prefere o filename* em UTF-8', () => {
    const cabecalho =
      'attachment; filename="demonstrativo-2026-10-31.pdf"; filename*=UTF-8\'\'demonstrativo-a%C3%A7%C3%A3o-2026-10-31.pdf'
    expect(nomeDoAnexo(cabecalho, 'padrao.pdf')).toBe('demonstrativo-ação-2026-10-31.pdf')
  })

  it('usa o filename simples, com ou sem aspas', () => {
    expect(nomeDoAnexo('attachment; filename="demonstrativo.pdf"', 'padrao.pdf')).toBe('demonstrativo.pdf')
    expect(nomeDoAnexo('attachment; filename=demonstrativo.pdf', 'padrao.pdf')).toBe('demonstrativo.pdf')
  })

  it('cai no filename simples se o estendido vier malformado', () => {
    expect(nomeDoAnexo('attachment; filename="ok.pdf"; filename*=UTF-8\'\'%E0%A4%A.pdf', 'padrao.pdf')).toBe('ok.pdf')
  })

  it('sem cabeçalho ou sem nome, fica o padrão', () => {
    expect(nomeDoAnexo(null, 'padrao.pdf')).toBe('padrao.pdf')
    expect(nomeDoAnexo('attachment', 'padrao.pdf')).toBe('padrao.pdf')
  })
})
