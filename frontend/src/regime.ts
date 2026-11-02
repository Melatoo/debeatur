import type { IndiceCorrecao, Regime, Regra, TipoJuros } from './api'
import { mes } from './formato'
import { lerDecimal, paraCampo } from './numero'

export const INDICES: Record<IndiceCorrecao, string> = {
  INPC: 'INPC',
  IPCA: 'IPCA',
  IPCA_E: 'IPCA-E',
  SELIC: 'SELIC',
}

export const TIPOS_JUROS: Record<TipoJuros, string> = {
  TAXA_FIXA: 'Taxa fixa mensal',
  TAXA_LEGAL: 'Taxa legal (Lei 14.905/2024)',
  TRANSICAO_TAXA_LEGAL: 'Taxa fixa até o corte + taxa legal pro rata',
  SEM_JUROS: 'Sem juros',
}

/** Se o tipo de juros tem percentual fixo: a taxa fixa e a parte fixa do mês de transição. */
export const usaPercentual = (juros: TipoJuros) => juros === 'TAXA_FIXA' || juros === 'TRANSICAO_TAXA_LEGAL'

/** Regra como o formulário a edita: campos de texto, meses em AAAA-MM e fim vazio = até a data-base. */
export interface RegraEditavel {
  inicio: string
  fim: string
  correcao: IndiceCorrecao
  juros: TipoJuros
  percentual: string
  /** Último dia da taxa fixa (AAAA-MM-DD), só na transição para a taxa legal. */
  ultimoDiaTaxaFixa: string
}

export const regraVazia = (inicio = ''): RegraEditavel => ({
  inicio,
  fim: '',
  correcao: 'IPCA',
  juros: 'TAXA_LEGAL',
  percentual: '',
  ultimoDiaTaxaFixa: '',
})

export const paraEditavel = (regras: Regra[]): RegraEditavel[] =>
  regras.map((r) => ({
    inicio: r.inicio,
    fim: r.fim ?? '',
    correcao: r.correcao,
    juros: r.juros,
    percentual: r.percentualJurosMensal == null ? '' : paraCampo(r.percentualJurosMensal),
    ultimoDiaTaxaFixa: r.ultimoDiaTaxaFixa ?? '',
  }))

export const paraRegime = (nome: string, regras: RegraEditavel[]): Regime => ({
  nome: nome.trim() || null,
  regras: regras.map((r) => ({
    inicio: r.inicio,
    fim: r.fim || null,
    correcao: r.correcao,
    juros: r.juros,
    percentualJurosMensal: usaPercentual(r.juros) && r.percentual ? Number(lerDecimal(r.percentual, false) ?? NaN) : null,
    ultimoDiaTaxaFixa: r.juros === 'TRANSICAO_TAXA_LEGAL' && r.ultimoDiaTaxaFixa ? r.ultimoDiaTaxaFixa : null,
  })),
})

/**
 * Se a regra é a SELIC terminando junto à virada da Lei 14.905/2024 (30/08/2024). Como as
 * regras valem por mês inteiro, agosto/2024 fica todo com um critério só (#32).
 */
export const selicNaViradaDaLei14905 = (regra: RegraEditavel) =>
  regra.correcao === 'SELIC' && (regra.fim === '2024-07' || regra.fim === '2024-08')

/** Mês seguinte a um mês AAAA-MM. */
export function mesSeguinte(mes: string): string {
  const [ano, m] = mes.split('-').map(Number)
  return m === 12 ? `${ano + 1}-01` : `${ano}-${String(m + 1).padStart(2, '0')}`
}

/** Mês anterior a um mês AAAA-MM. */
export function mesAnterior(mes: string): string {
  const [ano, m] = mes.split('-').map(Number)
  return m === 1 ? `${ano - 1}-12` : `${ano}-${String(m - 1).padStart(2, '0')}`
}

export function descreverPeriodo(regra: Regra): string {
  return regra.fim ? `${mes(regra.inicio)} a ${mes(regra.fim)}` : `desde ${mes(regra.inicio)}`
}
