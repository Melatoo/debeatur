import type { ParcelaRequest, ProcessoRequest } from './api'
import { apagar, gravar, ler, type Armazenamento } from './armazenamento'
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

/** Rascunho lido do navegador, com o momento em que foi salvo e a aba que o salvou. */
export interface RascunhoSalvo {
  rascunho: Rascunho
  salvoEm: Date
  aba: string
}

/** Muda quando o formato do rascunho muda; um rascunho de outra versão é descartado, e não adaptado. */
const VERSAO = 1
export const CHAVE_RASCUNHO = 'debeatur:rascunho'
/** Pausa na digitação antes de gravar: o formulário inteiro vai para o disco, e não a cada tecla. */
export const ESPERA_RASCUNHO_MS = 400

const ehObjeto = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v)

const comTextos = (v: unknown, campos: readonly string[]): v is Record<string, unknown> =>
  ehObjeto(v) && campos.every((c) => typeof v[c] === 'string')

const listaDe = (v: unknown, item: (x: unknown) => boolean) => Array.isArray(v) && v.every(item)

const ehProcesso = (v: unknown) => comTextos(v, ['numero', 'juizo', 'exequente', 'executado'])

const ehParcela = (v: unknown) =>
  comTextos(v, ['descricao', 'valor', 'inicioCorrecao']) && (v.inicioJuros === null || typeof v.inicioJuros === 'string')

const ehPensao = (v: unknown) =>
  comTextos(v, ['percentual', 'diaVencimento', 'primeiroMes', 'ultimoMes', 'inicioJuros', 'citacao', 'ajuizamento']) &&
  listaDe(v.pagamentos, (p) => comTextos(p, ['mes', 'valor']))

const ehRegra = (v: unknown) => comTextos(v, ['inicio', 'fim', 'correcao', 'juros', 'percentual', 'ultimoDiaTaxaFixa'])

/**
 * Confere a forma do rascunho inteiro, até os itens das listas: um rascunho corrompido, editado à
 * mão ou salvo por uma versão anterior do formulário não pode derrubar a tela na carga. O conteúdo
 * dos campos de texto é validado pelo próprio formulário.
 */
function ehRascunho(v: unknown): v is Rascunho {
  if (!ehObjeto(v)) return false
  return (
    ehProcesso(v.processo) &&
    typeof v.dataBase === 'string' &&
    typeof v.aplicarArt523 === 'boolean' &&
    typeof v.comPensao === 'boolean' &&
    typeof v.separarRitos === 'boolean' &&
    ehPensao(v.pensao) &&
    listaDe(v.parcelas, ehParcela) &&
    typeof v.outrasAbertas === 'boolean' &&
    typeof v.regimeProprio === 'boolean' &&
    typeof v.nomeRegime === 'string' &&
    listaDe(v.regras, ehRegra)
  )
}

/** O conteúdo da chave, se for um rascunho que esta versão do formulário sabe abrir. */
function interpretar(texto: string): RascunhoSalvo | null {
  try {
    const salvo: unknown = JSON.parse(texto)
    if (!ehObjeto(salvo) || salvo.versao !== VERSAO || !ehRascunho(salvo.rascunho)) return null
    const salvoEm = new Date(typeof salvo.salvoEm === 'string' ? salvo.salvoEm : NaN)
    return {
      rascunho: salvo.rascunho,
      salvoEm: Number.isNaN(salvoEm.getTime()) ? new Date() : salvoEm,
      aba: typeof salvo.aba === 'string' ? salvo.aba : '',
    }
  } catch {
    return null
  }
}

/** O rascunho guardado. Um que não dá para abrir é apagado, para não ser lido de novo a cada carga. */
export function lerRascunho(armazenamento: Armazenamento | null): RascunhoSalvo | null {
  const texto = ler(armazenamento, CHAVE_RASCUNHO)
  if (!texto) return null
  const salvo = interpretar(texto)
  if (!salvo) apagar(armazenamento, CHAVE_RASCUNHO)
  return salvo
}

/** Igualdade de valores, sem depender da ordem das chaves. */
function iguais(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (Array.isArray(a) || Array.isArray(b)) {
    return Array.isArray(a) && Array.isArray(b) && a.length === b.length && a.every((x, i) => iguais(x, b[i]))
  }
  if (!ehObjeto(a) || !ehObjeto(b)) return false
  const chaves = Object.keys(a)
  return chaves.length === Object.keys(b).length && chaves.every((k) => Object.hasOwn(b, k) && iguais(a[k], b[k]))
}

/**
 * Se o rascunho é o formulário em branco. A data-base fica de fora: a padrão muda a cada mês, e
 * mexer só nela não faz um caso que valha guardar.
 */
export function ehRascunhoVazio(rascunho: Rascunho, vazio: Rascunho): boolean {
  return iguais({ ...rascunho, dataBase: '' }, { ...vazio, dataBase: '' })
}

/** @param aba quem salvou: só ela apaga o rascunho, para o "Limpar" de uma aba não levar o caso de outra */
export function salvarRascunho(armazenamento: Armazenamento | null, rascunho: Rascunho, aba: string, agora = new Date()): void {
  gravar(armazenamento, CHAVE_RASCUNHO, JSON.stringify({ versao: VERSAO, salvoEm: agora.toISOString(), aba, rascunho }))
}

/**
 * Apaga o rascunho, se foi esta aba que o salvou por último. Se outra aba gravou o caso dela
 * depois, ele fica: é o que a próxima visita deve restaurar.
 */
export function apagarRascunho(armazenamento: Armazenamento | null, aba: string): void {
  const texto = ler(armazenamento, CHAVE_RASCUNHO)
  if (!texto) return
  const salvo = interpretar(texto)
  if (!salvo || salvo.aba === aba) apagar(armazenamento, CHAVE_RASCUNHO)
}
