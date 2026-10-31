export interface ParcelaRequest {
  descricao: string
  valor: string
  inicioCorrecao: string
  inicioJuros: string | null
}

export type IndiceCorrecao = 'INPC' | 'IPCA' | 'IPCA_E' | 'SELIC'
export type TipoJuros = 'TAXA_FIXA' | 'TAXA_LEGAL' | 'SEM_JUROS'

/** Meses no formato AAAA-MM; `fim` nulo quando a regra vale até a data-base. */
export interface Regra {
  inicio: string
  fim: string | null
  correcao: IndiceCorrecao
  juros: TipoJuros
  percentualJurosMensal: number | null
  /** Só na resposta. */
  descricaoJuros?: string
}

export interface Regime {
  nome: string | null
  regras: Regra[]
}

export interface CalculoRequest {
  dataBase: string
  aplicarArt523: boolean
  parcelas: ParcelaRequest[]
  /** Sem regime, a API aplica o regime cível padrão do TJMG. */
  regime?: Regime
}

export interface LinhaDemonstrativo {
  descricao: string
  valorOriginal: number
  inicioCorrecao: string
  inicioJuros: string | null
  fatorCorrecao: number
  valorCorrigido: number
  percentualJuros: number
  valorJuros: number
  subtotal: number
}

export interface Demonstrativo {
  dataBase: string
  regime: Regime
  linhas: LinhaDemonstrativo[]
  totalCorrigido: number
  totalJuros: number
  debitoAtualizado: number
  multaArt523: number
  honorariosArt523: number
  total: number
}

async function lerResposta<T>(resposta: Response, acao: string): Promise<T> {
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => null)
    throw new Error(problema?.detail ?? `Erro ${resposta.status} ao ${acao}`)
  }
  return resposta.json()
}

export async function calcular(request: CalculoRequest): Promise<Demonstrativo> {
  const resposta = await fetch('/api/calculos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return lerResposta(resposta, 'calcular')
}

export async function buscarRegimePadrao(): Promise<Regime> {
  return lerResposta(await fetch('/api/regimes/padrao'), 'carregar o regime padrão')
}
