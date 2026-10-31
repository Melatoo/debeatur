import type { IndiceCorrecao, Regime, Regra, TipoJuros } from './api'

export const INDICES: Record<IndiceCorrecao, string> = {
  INPC: 'INPC',
  IPCA: 'IPCA',
  IPCA_E: 'IPCA-E',
  SELIC: 'SELIC',
}

export const TIPOS_JUROS: Record<TipoJuros, string> = {
  TAXA_FIXA: 'Taxa fixa mensal',
  TAXA_LEGAL: 'Taxa legal (Lei 14.905/2024)',
  SEM_JUROS: 'Sem juros',
}

/** Regra como o formulário a edita: campos de texto, meses em AAAA-MM e fim vazio = até a data-base. */
export interface RegraEditavel {
  inicio: string
  fim: string
  correcao: IndiceCorrecao
  juros: TipoJuros
  percentual: string
}

export const regraVazia = (inicio = ''): RegraEditavel => ({
  inicio,
  fim: '',
  correcao: 'IPCA',
  juros: 'TAXA_LEGAL',
  percentual: '',
})

export const paraEditavel = (regras: Regra[]): RegraEditavel[] =>
  regras.map((r) => ({
    inicio: r.inicio,
    fim: r.fim ?? '',
    correcao: r.correcao,
    juros: r.juros,
    percentual: r.percentualJurosMensal == null ? '' : String(r.percentualJurosMensal),
  }))

export const paraRegime = (nome: string, regras: RegraEditavel[]): Regime => ({
  nome: nome.trim() || null,
  regras: regras.map((r) => ({
    inicio: r.inicio,
    fim: r.fim || null,
    correcao: r.correcao,
    juros: r.juros,
    percentualJurosMensal: r.juros === 'TAXA_FIXA' && r.percentual ? Number(r.percentual) : null,
  })),
})

/** Mês seguinte a um mês AAAA-MM. */
export function mesSeguinte(mes: string): string {
  const [ano, m] = mes.split('-').map(Number)
  return m === 12 ? `${ano + 1}-01` : `${ano}-${String(m + 1).padStart(2, '0')}`
}

export const mesBr = (mes: string) => mes.split('-').reverse().join('/')

export function descreverPeriodo(regra: Regra): string {
  return regra.fim ? `${mesBr(regra.inicio)} a ${mesBr(regra.fim)}` : `desde ${mesBr(regra.inicio)}`
}
