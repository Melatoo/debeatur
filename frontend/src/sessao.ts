import { createContext, useContext } from 'react'

export interface SessaoAtual {
  email: string
  /**
   * Conta os logins desta página: muda quando a pessoa entra de novo depois de a sessão expirar,
   * para o que falhou com a sessão vencida (a lista dos cálculos salvos) buscar de novo.
   */
  geracao: number
  /**
   * Encerra a sessão e volta à tela de login; o rascunho fica no navegador.
   * @throws Error se o servidor não respondeu; a sessão continua, e quem chamou mostra a falha
   */
  sair: () => Promise<void>
}

/** Preenchido pelo `Acesso` enquanto há sessão. */
export const ContextoSessao = createContext<SessaoAtual | null>(null)

/** Quem está logado, ou `null` fora do `Acesso`. */
export function useSessao(): SessaoAtual | null {
  return useContext(ContextoSessao)
}
