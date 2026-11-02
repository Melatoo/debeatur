export interface ParcelaRequest {
  descricao: string
  valor: string
  inicioCorrecao: string
  inicioJuros: string | null
}

export type IndiceCorrecao = 'INPC' | 'IPCA' | 'IPCA_E' | 'SELIC'
export type TipoJuros = 'TAXA_FIXA' | 'TAXA_LEGAL' | 'TRANSICAO_TAXA_LEGAL' | 'SEM_JUROS'

/** Meses no formato AAAA-MM; `fim` nulo quando a regra vale até a data-base. */
export interface Regra {
  inicio: string
  fim: string | null
  correcao: IndiceCorrecao
  juros: TipoJuros
  percentualJurosMensal: number | null
  /** Só em TRANSICAO_TAXA_LEGAL: último dia (AAAA-MM-DD) da taxa fixa no mês da regra. */
  ultimoDiaTaxaFixa: string | null
  /** Só na resposta. */
  descricaoJuros?: string
}

export interface Regime {
  nome: string | null
  regras: Regra[]
}

export type InicioJuros = 'VENCIMENTO' | 'CITACAO'

/** Mês (AAAA-MM) e valor pago, abatido da parcela que vence no mesmo mês. */
export interface PagamentoRequest {
  mes: string
  valor: string
}

/** Pensão em percentual do salário mínimo: uma parcela por mês, de `primeiroMes` a `ultimoMes`. */
export interface PensaoRequest {
  percentual: string
  diaVencimento: number
  primeiroMes: string
  ultimoMes: string
  inicioJuros: InicioJuros
  /** Obrigatória com juros desde a citação. */
  citacao: string | null
  pagamentos: PagamentoRequest[]
  /** Se informado, separa as linhas entre o rito da prisão e o da penhora; o art. 523 incide só na penhora. */
  ajuizamento: string | null
}

export interface CalculoRequest {
  dataBase: string
  aplicarArt523: boolean
  /** Parcelas avulsas; opcionais quando há pensão. */
  parcelas: ParcelaRequest[]
  pensao?: PensaoRequest
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
  /** Nulo se o cálculo não separa os ritos. */
  rito: Rito | null
  /** De onde veio a parcela da pensão; nulo nas parcelas avulsas. */
  pensao: MesPensao | null
}

/** O devido é o percentual do salário mínimo vigente no mês; o valor original da linha, o devido menos o pago. */
export interface MesPensao {
  /** AAAA-MM */
  mes: string
  salarioMinimo: number
  valorDevido: number
  valorPago: number
}

export type Rito = 'PRISAO' | 'PENHORA'

export interface SubtotalRito {
  parcelas: number
  totalCorrigido: number
  totalJuros: number
  debitoAtualizado: number
}

export interface Ritos {
  ajuizamento: string
  prisao: SubtotalRito
  penhora: SubtotalRito
  /** Débito da penhora mais multa e honorários do art. 523, somados no servidor. */
  totalPenhora: number
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
  ritos: Ritos | null
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
