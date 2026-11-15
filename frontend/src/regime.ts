import type { IndiceCorrecao, LinhaDemonstrativo, Regime, Regra, TipoJuros } from './api'
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
 * taxa legal desde set./2024). Até 07/2024, agosto inteiro passa para o critério da regra
 * seguinte, como no CADEJ do TJMG, que deixa agosto com a taxa legal. As fontes divergem
 * (divergência 4 do docs/gabarito.md), então o editor avisa nos dois casos.
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
  if (!regra.fim) return `desde ${mes(regra.inicio)}`
  return regra.fim === regra.inicio ? mes(regra.inicio) : `${mes(regra.inicio)} a ${mes(regra.fim)}`
}

/**
 * Regras do regime que alcançam o período do cálculo: do primeiro termo inicial ao mês anterior
 * à data-base, que é o último mês que o motor corrige e sobre o qual conta juros. Se tudo vence
 * no mês da data-base, mostra a regra vigente nesse mês, em vez de uma lista vazia.
 *
 * A vigência de cada regra sai recortada a esse período, como no PDF: num cálculo de 2024, a
 * regra que vale desde 07/1994 aparece desde o primeiro mês do cálculo. A sem fim continua "desde".
 */
export function regrasDoPeriodo(regras: Regra[], linhas: LinhaDemonstrativo[], dataBase: string): Regra[] {
  const inicios = linhas.flatMap((l) => [l.inicioCorrecao, l.inicioJuros ?? l.inicioCorrecao]).map((d) => d.slice(0, 7))
  const primeiro = inicios.sort()[0]
  if (!primeiro) return regras
  const anteriorABase = mesAnterior(dataBase.slice(0, 7))
  const ultimo = primeiro > anteriorABase ? primeiro : anteriorABase
  return regras
    .filter((r) => r.inicio <= ultimo && (r.fim == null || r.fim >= primeiro))
    .map((r) => ({
      ...r,
      inicio: r.inicio < primeiro ? primeiro : r.inicio,
      fim: r.fim != null && r.fim > ultimo ? ultimo : r.fim,
    }))
}
