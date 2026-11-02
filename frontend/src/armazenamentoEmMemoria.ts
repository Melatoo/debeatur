import type { Armazenamento } from './armazenamento'

/** Armazenamento em memória para os testes, com o mapa à vista para conferir o que foi gravado. */
export function armazenamentoEmMemoria(): Armazenamento & { dados: Map<string, string> } {
  const dados = new Map<string, string>()
  return {
    dados,
    getItem: (k) => dados.get(k) ?? null,
    setItem: (k, v) => void dados.set(k, v),
    removeItem: (k) => void dados.delete(k),
  }
}
