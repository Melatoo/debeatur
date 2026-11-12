import { describe, expect, it } from 'vitest'
import { nomeDoAnexo, tokenCsrf } from './api'

describe('tokenCsrf', () => {
  it('lê o cookie XSRF-TOKEN entre os outros', () => {
    expect(tokenCsrf('tema=escuro; XSRF-TOKEN=abc-123; outro=x')).toBe('abc-123')
    expect(tokenCsrf('XSRF-TOKEN=abc-123')).toBe('abc-123')
  })

  it('decodifica o valor e mantém o "=" do meio', () => {
    expect(tokenCsrf('XSRF-TOKEN=a%2Fb=c')).toBe('a/b=c')
  })

  it('sem o cookie, ou com nome só parecido, não há token', () => {
    expect(tokenCsrf('')).toBeNull()
    expect(tokenCsrf('MEU-XSRF-TOKEN=abc')).toBeNull()
  })
})

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
