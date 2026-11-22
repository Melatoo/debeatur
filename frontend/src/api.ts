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

/** Como o título fixou o valor mensal da pensão. */
export type TipoBase = 'SALARIO_MINIMO' | 'RENDIMENTOS' | 'VALOR_FIXO'

/** Valor mensal da pensão em valor fixo, do mês `aPartirDe` (AAAA-MM) até o próximo valor. */
export interface ValorFixoRequest {
  aPartirDe: string
  valor: string
}

/** Valor recebido em qualquer data (bloqueio, depósito), que sai do débito atualizado pelo valor nominal. */
export interface AbatimentoRequest {
  /** AAAA-MM-DD */
  data: string
  valor: string
  origem: string
}

/** Pensão alimentícia: uma parcela por mês, de `primeiroMes` a `ultimoMes`. */
export interface PensaoRequest {
  /** Sem base, a API entende salário mínimo, como nos cálculos salvos antes das outras. */
  base?: TipoBase
  /** Percentual do salário mínimo ou dos rendimentos; nulo no valor fixo. */
  percentual: string | null
  /** Rendimentos mensais, iguais em todos os meses; só na base RENDIMENTOS. */
  rendimentos?: string
  /** Só na base VALOR_FIXO; o primeiro valor cobre o início da pensão. */
  valoresFixos?: ValorFixoRequest[]
  /** Se a parcela de dezembro dobra com o 13º salário. */
  decimoTerceiro?: boolean
  diaVencimento: number
  primeiroMes: string
  ultimoMes: string
  inicioJuros: InicioJuros
  /** Obrigatória com juros desde a citação. */
  citacao: string | null
  pagamentos: PagamentoRequest[]
  /** Saem do débito atualizado (ou do da penhora, com os ritos) pelo valor nominal; multa e honorários incidem sobre o saldo. */
  abatimentos?: AbatimentoRequest[]
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

/** Sobre o que incidem os honorários do art. 523: o débito (DPMG e STJ) ou o débito com a multa (CADEJ). */
export type BaseHonorarios = 'DEBITO' | 'DEBITO_COM_MULTA'

export interface CalculoRequest {
  dataBase: string
  aplicarArt523: boolean
  /** Sem ela, os honorários incidem sobre o débito, como na planilha da DPMG. */
  baseHonorarios?: BaseHonorarios
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

/** O devido sai da base do mês (em dobro em dezembro, com o 13º); o valor original da linha é o devido menos o pago. */
export interface MesPensao {
  /** AAAA-MM */
  mes: string
  /** Nulo se a pensão não é em salário mínimo. */
  salarioMinimo: number | null
  valorDevido: number
  valorPago: number
  /** Se o devido inclui o 13º salário. Ausente nas respostas anteriores a ele. */
  decimoTerceiro?: boolean
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
  /** Parte dos abatimentos que saiu do débito da penhora. Ausente nas respostas anteriores aos abatimentos. */
  abatidoPenhora?: number
  /** Parte dos abatimentos que passou do débito da penhora e saiu do da prisão. */
  abatidoPrisao?: number
  /** Débito da prisão menos `abatidoPrisao`. */
  totalPrisao?: number
  /** Saldo da penhora mais multa e honorários do art. 523, somados no servidor. */
  totalPenhora: number
}

/** Um abatimento como o servidor o aplicou. */
export interface Abatimento {
  data: string
  valor: number
  origem: string
}

/** Um índice que o cálculo leu, com o valor de cada mês, de onde veio e de quando é. */
export interface SerieUsada {
  /** `IPCA`, `INPC`, `IPCA_E`, `SELIC` ou `TAXA_LEGAL`. */
  serie: string
  /** "INPC (IBGE)" */
  descricao: string
  /** "SGS/BCB, série 188" */
  fonte: string
  /** Instante ISO 8601: a última consulta ao SGS, ou o salvamento se `snapshot`; nulo se a fonte não sabe. */
  capturadaEm: string | null
  /** Se os valores são os guardados com um cálculo salvo. */
  snapshot: boolean
  /** Variação de cada mês (AAAA-MM), em pontos percentuais, em ordem. */
  valores: { mes: string; valor: number }[]
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
  /** Ausente nas respostas anteriores aos abatimentos. */
  abatimentos?: Abatimento[]
  /** Soma dos abatimentos; multa e honorários incidem sobre o débito menos ele. */
  totalAbatido?: number
  total: number
  ritos: Ritos | null
  /** Os índices que o cálculo leu, mês a mês; o salário mínimo vem nas linhas da pensão. */
  series: SerieUsada[]
}

/** Erro devolvido pela API, com o problem+json inteiro para quem quiser mais que a mensagem. */
export class ErroDaApi extends Error {
  readonly status: number
  readonly problema: unknown

  constructor(mensagem: string, status: number, problema: unknown) {
    super(mensagem)
    this.name = 'ErroDaApi'
    this.status = status
    this.problema = problema
  }
}

/** Quem está logado. */
export interface Sessao {
  email: string
}

const SESSAO_EXPIRADA = 'A sessão expirou. Entre de novo e repita o que estava fazendo.'

const aoExpirar = new Set<() => void>()

/**
 * Avisa quando a API responde 401 no meio do uso: a sessão expirou ou foi encerrada em outra aba.
 * @returns a função que cancela o aviso
 */
export function aoExpirarSessao(aviso: () => void): () => void {
  aoExpirar.add(aviso)
  return () => aoExpirar.delete(aviso)
}

/** O token CSRF que a API grava no cookie `XSRF-TOKEN` e confere no cabeçalho de todo POST, PATCH e DELETE. */
export function tokenCsrf(cookies: string = document.cookie): string | null {
  for (const par of cookies.split(';')) {
    const [nome, ...valor] = par.trim().split('=')
    if (nome === 'XSRF-TOKEN') return decodeURIComponent(valor.join('='))
  }
  return null
}

/**
 * Os cabeçalhos com o token CSRF. Sem o cookie (outra aba saiu, ou os cookies foram apagados), o
 * GET da sessão grava um novo antes: sem token, o POST levaria 403, e não o 401 que pede login.
 */
async function comCsrf(cabecalhos: Record<string, string>): Promise<Record<string, string>> {
  if (!tokenCsrf()) await fetch('/api/sessao').catch(() => undefined)
  const token = tokenCsrf()
  return token ? { ...cabecalhos, 'X-XSRF-TOKEN': token } : cabecalhos
}

async function lerResposta<T>(resposta: Response, acao: string): Promise<T> {
  if (resposta.status === 401) {
    aoExpirar.forEach((aviso) => aviso())
    throw new ErroDaApi(SESSAO_EXPIRADA, 401, null)
  }
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => null)
    throw new ErroDaApi(problema?.detail ?? `Erro ${resposta.status} ao ${acao}`, resposta.status, problema)
  }
  return resposta.json()
}

/** Quem está logado, ou `null`. Também entrega o cookie do token CSRF, que o login exige. */
export async function buscarSessao(): Promise<Sessao | null> {
  const resposta = await fetch('/api/sessao')
  if (resposta.status === 401) return null
  if (!resposta.ok) throw new ErroDaApi(`Erro ${resposta.status} ao verificar a sessão`, resposta.status, null)
  return resposta.json()
}

/**
 * @throws ErroDaApi com "E-mail ou senha incorretos." (401) se as credenciais não conferem, ou
 * "Muitas tentativas de entrar..." (429) depois de muitas falhas
 */
export async function entrar(email: string, senha: string): Promise<Sessao> {
  const resposta = await fetch('/api/sessao', {
    method: 'POST',
    headers: await comCsrf({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ email, senha }),
  })
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => null)
    throw new ErroDaApi(problema?.detail ?? `Erro ${resposta.status} ao entrar`, resposta.status, problema)
  }
  return resposta.json()
}

export async function sair(): Promise<void> {
  const resposta = await fetch('/api/sessao', { method: 'DELETE', headers: await comCsrf({}) })
  if (!resposta.ok && resposta.status !== 401) {
    throw new ErroDaApi(`Erro ${resposta.status} ao sair`, resposta.status, null)
  }
}

/** @param sinal cancela o pedido quando o formulário muda antes da resposta */
export async function calcular(request: CalculoRequest, sinal?: AbortSignal): Promise<Demonstrativo> {
  const resposta = await fetch('/api/calculos', {
    method: 'POST',
    headers: await comCsrf({ 'Content-Type': 'application/json' }),
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

/** O demonstrativo em PDF, para juntar aos autos, ou em planilha do Excel, com os mesmos dados. */
export type FormatoDoArquivo = 'pdf' | 'xlsx'

const TIPO_DO_ARQUIVO: Record<FormatoDoArquivo, string> = {
  pdf: 'application/pdf',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
}

/** O arquivo com o artigo, para as mensagens: "gerar o PDF", "gerar a planilha". */
export const O_ARQUIVO: Record<FormatoDoArquivo, string> = { pdf: 'o PDF', xlsx: 'a planilha' }

/** O mesmo cálculo de `calcular`, como demonstrativo em PDF ou em planilha. */
export async function gerarArquivo(request: CalculoRequest, formato: FormatoDoArquivo): Promise<Anexo> {
  const resposta = await fetch(`/api/calculos/${formato}`, {
    method: 'POST',
    // sem o problem+json, o erro do cálculo viria como 406, sem a mensagem
    headers: await comCsrf({
      'Content-Type': 'application/json',
      Accept: `${TIPO_DO_ARQUIVO[formato]}, application/problem+json`,
    }),
    body: JSON.stringify(request),
  })
  if (!resposta.ok) await lerResposta(resposta, `gerar ${O_ARQUIVO[formato]}`)
  const nome = nomeDoAnexo(resposta.headers.get('Content-Disposition'), `demonstrativo-${request.dataBase}.${formato}`)
  return { conteudo: await resposta.blob(), nome }
}

/** Linha da lista de cálculos salvos. */
export interface ResumoCalculo {
  id: string
  titulo: string
  /** O número do processo no pedido salvo, se houver. */
  processo: string | null
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
    headers: await comCsrf({ 'Content-Type': 'application/json' }),
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

/** Troca só o título; os números e os índices guardados não mudam. */
export async function renomearCalculo(id: string, titulo: string): Promise<ResumoCalculo> {
  const resposta = await fetch(`/api/calculos/salvos/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    headers: await comCsrf({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ titulo: titulo.trim() }),
  })
  return lerResposta(resposta, 'renomear o cálculo')
}

/** Exclui o cálculo e os índices guardados com ele, sem volta. */
export async function excluirCalculo(id: string): Promise<void> {
  const resposta = await fetch(`/api/calculos/salvos/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: await comCsrf({}),
  })
  if (resposta.status !== 204) await lerResposta(resposta, 'excluir o cálculo')
}

/** O PDF ou a planilha do cálculo salvo, com o snapshot das séries: os mesmos números da tela ao abri-lo. */
export async function gerarArquivoSalvo(id: string, formato: FormatoDoArquivo): Promise<Anexo> {
  const resposta = await fetch(`/api/calculos/salvos/${encodeURIComponent(id)}/${formato}`, {
    headers: { Accept: `${TIPO_DO_ARQUIVO[formato]}, application/problem+json` },
  })
  if (!resposta.ok) await lerResposta(resposta, `gerar ${O_ARQUIVO[formato]}`)
  const nome = nomeDoAnexo(resposta.headers.get('Content-Disposition'), `demonstrativo-${id}.${formato}`)
  return { conteudo: await resposta.blob(), nome }
}

export async function buscarRegimePadrao(): Promise<Regime> {
  return lerResposta(await fetch('/api/regimes/padrao'), 'carregar o regime padrão')
}

/** Regime do título omisso ou de "juros legais" pelo Tema 1368/STJ, montado no servidor. */
export async function buscarRegimeTema1368(): Promise<Regime> {
  return lerResposta(await fetch('/api/regimes/tema-1368'), 'carregar o regime do Tema 1368')
}
