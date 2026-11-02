import { gravar, ler, type Armazenamento } from './armazenamento'

/** Colunas da memória de cálculo que a pessoa pode esconder, na ordem da tabela. Parcela e Atualizado ficam sempre. */
const OPCIONAIS = ['vencimento', 'salarioMinimo', 'devido', 'pago', 'fator', 'corrigido', 'percentualJuros', 'juros'] as const

export type ColunaOpcional = (typeof OPCIONAIS)[number]

export const CHAVE_COLUNAS = 'debeatur:colunas-ocultas'

/**
 * As colunas escondidas da última vez. É uma preferência de quem usa o navegador, e não um dado do
 * caso: vale para todos os cálculos e não sai com o "Limpar". Um valor estranho vira lista vazia.
 */
export function lerColunasOcultas(armazenamento: Armazenamento | null): ColunaOpcional[] {
  try {
    const lidas: unknown = JSON.parse(ler(armazenamento, CHAVE_COLUNAS) ?? '[]')
    return Array.isArray(lidas) ? OPCIONAIS.filter((c) => lidas.includes(c)) : []
  } catch {
    return []
  }
}

/** Sem armazenamento, a escolha vale até recarregar a página. */
export function salvarColunasOcultas(armazenamento: Armazenamento | null, ocultas: ColunaOpcional[]): void {
  gravar(armazenamento, CHAVE_COLUNAS, JSON.stringify(ocultas))
}
