/** O pedaço do `Storage` que o rascunho e as preferências usam; nos testes, um mapa em memória. */
export type Armazenamento = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

/**
 * `localStorage` some em janela anônima com o armazenamento bloqueado, e o próprio acesso à
 * propriedade pode lançar exceção: sem ele, a tela funciona e só não guarda nada.
 */
export function armazenamentoDoNavegador(): Armazenamento | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** Lê uma chave; armazenamento bloqueado vale como chave ausente. */
export function ler(armazenamento: Armazenamento | null, chave: string): string | null {
  try {
    return armazenamento?.getItem(chave) ?? null
  } catch {
    return null
  }
}

/** Grava uma chave; cota cheia ou armazenamento bloqueado só deixam de guardar. */
export function gravar(armazenamento: Armazenamento | null, chave: string, valor: string): void {
  try {
    armazenamento?.setItem(chave, valor)
  } catch {
    // A tela segue sem guardar.
  }
}

export function apagar(armazenamento: Armazenamento | null, chave: string): void {
  try {
    armazenamento?.removeItem(chave)
  } catch {
    // idem
  }
}
