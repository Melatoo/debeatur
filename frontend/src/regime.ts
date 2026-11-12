import type { IndiceCorrecao, Regime, Regra, TipoJuros } from './api'
import { mes } from './formato'
import { exigirDecimal, paraCampo } from './numero'

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
    percentualJurosMensal: usaPercentual(r.juros) && r.percentual ? Number(exigirDecimal(r.percentual, 'Percentual de juros', false)) : null,
    ultimoDiaTaxaFixa: r.juros === 'TRANSICAO_TAXA_LEGAL' && r.ultimoDiaTaxaFixa ? r.ultimoDiaTaxaFixa : null,
  })),
})

/**
 * Quem fica com agosto/2024 quando a regra é a SELIC terminando junto à virada da Lei 14.905/2024
 * (30/08/2024), ou {@code null} se a regra não é essa.
 * <p>
 * As regras valem por mês inteiro, e o mês da virada não se divide (#32). Com a SELIC até 08/2024,
 * o cálculo segue o Manual de Cálculos da Justiça Federal (devedor privado: SELIC até ago./2024,
 * taxa legal desde set./2024) e o CADEJ do TJMG, que conta meses inteiros. Até 07/2024, agosto
 * inteiro passa para o critério da regra seguinte, o que nenhuma das duas fontes faz.
 */
export function agostoNaViradaDaSelic(regra: RegraEditavel): 'selic' | 'regra-seguinte' | null {
  if (regra.correcao !== 'SELIC') return null
  if (regra.fim === '2024-08') return 'selic'
  if (regra.fim === '2024-07') return 'regra-seguinte'
  return null
}

/** Ano e mês (1 a 12) no formato AAAA-MM. */
export const anoMes = (ano: number, mes: number) => `${ano}-${String(mes).padStart(2, '0')}`

/** Mês seguinte a um mês AAAA-MM. */
export function mesSeguinte(mes: string): string {
  const [ano, m] = mes.split('-').map(Number)
  return m === 12 ? anoMes(ano + 1, 1) : anoMes(ano, m + 1)
}

/** Mês anterior a um mês AAAA-MM. */
export function mesAnterior(mes: string): string {
  const [ano, m] = mes.split('-').map(Number)
  return m === 1 ? anoMes(ano - 1, 12) : anoMes(ano, m - 1)
}

export function descreverPeriodo(regra: Regra): string {
  return regra.fim ? `${mes(regra.inicio)} a ${mes(regra.fim)}` : `desde ${mes(regra.inicio)}`
}
