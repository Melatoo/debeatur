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

/** Identificação do processo no cabeçalho do PDF; nenhum campo entra no cálculo, e todos são opcionais. */
export interface ProcessoRequest {
  numero: string
  juizo: string
  exequente: string
  executado: string
}

export interface CalculoRequest {
  dataBase: string
  aplicarArt523: boolean
  /** Parcelas avulsas; opcionais quando há pensão. */
  parcelas: ParcelaRequest[]
  pensao?: PensaoRequest
  /** Sem regime, a API aplica o regime cível padrão do TJMG. */
  regime?: Regime
  processo?: ProcessoRequest
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

/** @param sinal cancela o pedido quando o formulário muda antes da resposta */
export async function calcular(request: CalculoRequest, sinal?: AbortSignal): Promise<Demonstrativo> {
  const resposta = await fetch('/api/calculos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: sinal,
  })
  return lerResposta(resposta, 'calcular')
}

/**
 * Nome do arquivo no Content-Disposition, preferindo o `filename*` (RFC 5987), que vem em UTF-8.
 * Sem cabeçalho legível, fica o padrão.
 */
export function nomeDoAnexo(disposicao: string | null, padrao: string): string {
  if (!disposicao) return padrao
  const estendido = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposicao)
  if (estendido) {
    try {
      return decodeURIComponent(estendido[1].trim())
    } catch {
      // segue para o filename simples
    }
  }
  const simples = /filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)/i.exec(disposicao)
  return simples?.[1] ?? simples?.[2]?.trim() ?? padrao
}

export interface Anexo {
  conteudo: Blob
  nome: string
}

/** O mesmo cálculo de `calcular`, como demonstrativo em PDF para juntar aos autos. */
export async function gerarPdf(request: CalculoRequest): Promise<Anexo> {
  const resposta = await fetch('/api/calculos/pdf', {
    method: 'POST',
    // sem o problem+json, o erro do cálculo viria como 406, sem a mensagem
    headers: { 'Content-Type': 'application/json', Accept: 'application/pdf, application/problem+json' },
    body: JSON.stringify(request),
  })
  if (!resposta.ok) await lerResposta(resposta, 'gerar o PDF')
  const nome = nomeDoAnexo(resposta.headers.get('Content-Disposition'), `demonstrativo-${request.dataBase}.pdf`)
  return { conteudo: await resposta.blob(), nome }
}

/** Linha da lista de cálculos salvos. */
export interface ResumoCalculo {
  id: string
  titulo: string
  dataBase: string
  /** Nome do regime usado. */
  regime: string
  total: number
  /** Instante ISO 8601. */
  criadoEm: string
}

/**
 * Cálculo salvo, como volta do servidor. O `calculo` é o pedido como foi salvo, com o regime
 * resolvido (o padrão vem por extenso), e os decimais dele chegam como números JSON, e não como
 * texto; `paraRascunho` os devolve ao formulário. O `demonstrativo` é refeito com o snapshot das
 * séries: sai igual ao do dia em que foi salvo.
 */
export interface CalculoSalvo {
  id: string
  titulo: string
  criadoEm: string
  calculo: CalculoRequest
  demonstrativo: Demonstrativo
}

/** Calcula com as séries de hoje e salva o pedido com o snapshot delas. Sem título, o servidor dá um. */
export async function salvarCalculo(titulo: string, calculo: CalculoRequest): Promise<CalculoSalvo> {
  const resposta = await fetch('/api/calculos/salvos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ titulo: titulo.trim() || null, calculo }),
  })
  return lerResposta(resposta, 'salvar o cálculo')
}

/** Os cálculos salvos, do mais recente para o mais antigo. */
export async function listarCalculos(): Promise<ResumoCalculo[]> {
  return lerResposta(await fetch('/api/calculos/salvos'), 'carregar os cálculos salvos')
}

export async function reabrirCalculo(id: string, sinal?: AbortSignal): Promise<CalculoSalvo> {
  return lerResposta(await fetch(`/api/calculos/salvos/${encodeURIComponent(id)}`, { signal: sinal }), 'abrir o cálculo')
}

/** O PDF do cálculo salvo, com o snapshot das séries: os mesmos números da tela ao abri-lo. */
export async function gerarPdfSalvo(id: string): Promise<Anexo> {
  const resposta = await fetch(`/api/calculos/salvos/${encodeURIComponent(id)}/pdf`, {
    headers: { Accept: 'application/pdf, application/problem+json' },
  })
  if (!resposta.ok) await lerResposta(resposta, 'gerar o PDF')
  const nome = nomeDoAnexo(resposta.headers.get('Content-Disposition'), `demonstrativo-${id}.pdf`)
  return { conteudo: await resposta.blob(), nome }
}

export async function buscarRegimePadrao(): Promise<Regime> {
  return lerResposta(await fetch('/api/regimes/padrao'), 'carregar o regime padrão')
}
