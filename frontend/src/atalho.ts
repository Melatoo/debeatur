/** O que o atalho precisa saber da tecla; `KeyboardEvent` tem tudo isso. */
export interface Tecla {
  key: string
  ctrlKey: boolean
  metaKey: boolean
  altKey: boolean
  shiftKey: boolean
  repeat: boolean
  isComposing: boolean
}

/** Mac e iPad usam a tecla Command onde os outros sistemas usam Ctrl. */
export function ehApple(plataforma: string): boolean {
  return /mac|iphone|ipad|ipod/i.test(plataforma)
}

/**
 * Ctrl+Enter (⌘+Enter no Mac) calcula de qualquer campo do formulário, como nos sistemas dos
 * tribunais. Com Alt ou Shift junto, é outra combinação; segurar a tecla não repete o cálculo, e
 * o Enter que confirma uma composição (acento, IME) não conta.
 */
export function ehAtalhoDeCalcular(tecla: Tecla, apple: boolean): boolean {
  if (tecla.key !== 'Enter' || tecla.repeat || tecla.isComposing || tecla.altKey || tecla.shiftKey) return false
  return apple ? tecla.metaKey && !tecla.ctrlKey : tecla.ctrlKey && !tecla.metaKey
}

/** Como o atalho aparece na tela e no `aria-keyshortcuts`. */
export function rotuloDoAtalho(apple: boolean): { tela: string; aria: string } {
  return apple ? { tela: '⌘+Enter', aria: 'Meta+Enter' } : { tela: 'Ctrl+Enter', aria: 'Control+Enter' }
}
