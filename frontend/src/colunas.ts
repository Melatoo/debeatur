/** Colunas da memória de cálculo que a pessoa pode esconder. Parcela e Atualizado ficam sempre. */
export type ColunaOpcional =
  | 'vencimento'
  | 'salarioMinimo'
  | 'devido'
  | 'pago'
  | 'fator'
  | 'corrigido'
  | 'percentualJuros'
  | 'juros'

const OPCIONAIS: readonly ColunaOpcional[] = [
  'vencimento',
  'salarioMinimo',
  'devido',
  'pago',
  'fator',
  'corrigido',
  'percentualJuros',
  'juros',
]

export const CHAVE_COLUNAS = 'debeatur:colunas-ocultas'

type Armazenamento = Pick<Storage, 'getItem' | 'setItem'>

/**
 * As colunas escondidas da última vez. É uma preferência de quem usa o navegador, e não um dado do
 * caso: vale para todos os cálculos e não sai com o "Limpar". Um valor estranho vira lista vazia.
 */
export function lerColunasOcultas(armazenamento: Armazenamento | null): ColunaOpcional[] {
  try {
    const lidas: unknown = JSON.parse(armazenamento?.getItem(CHAVE_COLUNAS) ?? '[]')
    return Array.isArray(lidas) ? OPCIONAIS.filter((c) => lidas.includes(c)) : []
  } catch {
    return []
  }
}

export function salvarColunasOcultas(armazenamento: Armazenamento | null, ocultas: ColunaOpcional[]): void {
  try {
    armazenamento?.setItem(CHAVE_COLUNAS, JSON.stringify(ocultas))
  } catch {
    // Sem armazenamento, a escolha vale até recarregar a página.
  }
}
