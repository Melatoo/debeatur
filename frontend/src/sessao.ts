import { createContext, useContext } from 'react'

export interface SessaoAtual {
  email: string
  /** Encerra a sessão e volta à tela de login; o rascunho fica no navegador. */
  sair: () => Promise<void>
}

/** Preenchido pelo `Acesso` enquanto há sessão. */
export const ContextoSessao = createContext<SessaoAtual | null>(null)

/** Quem está logado, ou `null` fora do `Acesso`. */
export function useSessao(): SessaoAtual | null {
  return useContext(ContextoSessao)
}
