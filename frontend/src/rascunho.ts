import type { ParcelaRequest, ProcessoRequest } from './api'
import type { PensaoEditavel } from './pensao'
import type { RegraEditavel } from './regime'

/**
 * O formulário inteiro, como fica guardado no navegador entre uma visita e outra. O demonstrativo
 * não entra: ele sai do servidor, e recalcular custa um clique.
 */
export interface Rascunho {
  processo: ProcessoRequest
  dataBase: string
  aplicarArt523: boolean
  comPensao: boolean
  separarRitos: boolean
  pensao: PensaoEditavel
  parcelas: ParcelaRequest[]
  outrasAbertas: boolean
  regimeProprio: boolean
  nomeRegime: string
  regras: RegraEditavel[]
}

/** Rascunho lido do navegador, com o momento em que foi salvo. */
export interface RascunhoSalvo {
  rascunho: Rascunho
  salvoEm: Date
}

/** Muda quando o formato do rascunho muda; um rascunho de outra versão é descartado, e não adaptado. */
const VERSAO = 1
export const CHAVE_RASCUNHO = 'debeatur:rascunho'

type Armazenamento = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

/**
 * `localStorage` some em janela anônima com o armazenamento bloqueado, e o próprio acesso à
 * propriedade pode lançar exceção: sem ele, o formulário funciona e só não guarda o rascunho.
 */
export function armazenamentoDoNavegador(): Armazenamento | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

const ehObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

/**
 * Confere a forma do rascunho, campo a campo do primeiro nível: um rascunho corrompido ou editado
 * à mão não pode derrubar a tela na carga. Os campos de texto são validados pelo próprio formulário.
 */
function ehRascunho(v: unknown): v is Rascunho {
  if (!ehObjeto(v)) return false
  return (
    ehObjeto(v.processo) &&
    typeof v.dataBase === 'string' &&
    typeof v.aplicarArt523 === 'boolean' &&
    typeof v.comPensao === 'boolean' &&
    typeof v.separarRitos === 'boolean' &&
    ehObjeto(v.pensao) &&
    Array.isArray(v.pensao.pagamentos) &&
    Array.isArray(v.parcelas) &&
    typeof v.outrasAbertas === 'boolean' &&
    typeof v.regimeProprio === 'boolean' &&
    typeof v.nomeRegime === 'string' &&
    Array.isArray(v.regras)
  )
}

export function lerRascunho(armazenamento: Armazenamento | null): RascunhoSalvo | null {
  try {
    const texto = armazenamento?.getItem(CHAVE_RASCUNHO)
    if (!texto) return null
    const salvo: unknown = JSON.parse(texto)
    if (!ehObjeto(salvo) || salvo.versao !== VERSAO || !ehRascunho(salvo.rascunho)) return null
    const salvoEm = new Date(typeof salvo.salvoEm === 'string' ? salvo.salvoEm : NaN)
    return { rascunho: salvo.rascunho, salvoEm: Number.isNaN(salvoEm.getTime()) ? new Date() : salvoEm }
  } catch {
    return null
  }
}

/**
 * Se o rascunho é o formulário em branco. A data-base fica de fora: a padrão muda a cada mês, e
 * mexer só nela não faz um caso que valha guardar.
 */
export function ehRascunhoVazio(rascunho: Rascunho, vazio: Rascunho): boolean {
  return JSON.stringify({ ...rascunho, dataBase: '' }) === JSON.stringify({ ...vazio, dataBase: '' })
}

/**
 * Guarda o rascunho, ou o apaga se for o formulário em branco: assim, abrir a página sem mexer em
 * nada não deixa para trás um rascunho que depois se anunciaria como restaurado.
 */
export function salvarRascunho(
  armazenamento: Armazenamento | null,
  rascunho: Rascunho,
  vazio: Rascunho,
  agora = new Date(),
): void {
  try {
    if (ehRascunhoVazio(rascunho, vazio)) armazenamento?.removeItem(CHAVE_RASCUNHO)
    else armazenamento?.setItem(CHAVE_RASCUNHO, JSON.stringify({ versao: VERSAO, salvoEm: agora.toISOString(), rascunho }))
  } catch {
    // Cota cheia ou armazenamento bloqueado: o rascunho deixa de ser guardado, e o formulário segue.
  }
}

export function apagarRascunho(armazenamento: Armazenamento | null): void {
  try {
    armazenamento?.removeItem(CHAVE_RASCUNHO)
  } catch {
    // idem
  }
}
