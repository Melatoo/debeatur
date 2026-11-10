import { describe, expect, it } from 'vitest'
import { ehApple, ehAtalhoDeCalcular, rotuloDoAtalho, type Tecla } from './atalho'

const tecla = (parcial: Partial<Tecla>): Tecla => ({
  key: 'Enter',
  ctrlKey: false,
  metaKey: false,
  altKey: false,
  shiftKey: false,
  repeat: false,
  isComposing: false,
  ...parcial,
})

describe('ehAtalhoDeCalcular', () => {
  it('aceita Ctrl+Enter fora do Mac e ⌘+Enter no Mac', () => {
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true }), false)).toBe(true)
    expect(ehAtalhoDeCalcular(tecla({ metaKey: true }), true)).toBe(true)
  })

  it('não troca as teclas entre os sistemas', () => {
    expect(ehAtalhoDeCalcular(tecla({ metaKey: true }), false)).toBe(false)
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true }), true)).toBe(false)
  })

  it('ignora o Enter sozinho, que segue com o comportamento do campo', () => {
    expect(ehAtalhoDeCalcular(tecla({}), false)).toBe(false)
  })

  it('ignora outras combinações, a repetição e a composição', () => {
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true, shiftKey: true }), false)).toBe(false)
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true, altKey: true }), false)).toBe(false)
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true, repeat: true }), false)).toBe(false)
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true, isComposing: true }), false)).toBe(false)
    expect(ehAtalhoDeCalcular(tecla({ ctrlKey: true, key: 'a' }), false)).toBe(false)
  })
})

describe('ehApple', () => {
  it('reconhece Mac e iPad', () => {
    expect(ehApple('MacIntel')).toBe(true)
    expect(ehApple('iPad')).toBe(true)
    expect(ehApple('Win32')).toBe(false)
    expect(ehApple('Linux x86_64')).toBe(false)
  })
})

describe('rotuloDoAtalho', () => {
  it('usa os nomes de tecla do aria-keyshortcuts', () => {
    expect(rotuloDoAtalho(false)).toEqual({ tela: 'Ctrl+Enter', aria: 'Control+Enter' })
    expect(rotuloDoAtalho(true)).toEqual({ tela: '⌘+Enter', aria: 'Meta+Enter' })
  })
})
