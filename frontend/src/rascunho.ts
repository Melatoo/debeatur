import type { ParcelaRequest, ProcessoRequest, Regime } from './api'
import { apagar, gravar, ler, type Armazenamento } from './armazenamento'
import { pensaoVazia, type PensaoEditavel } from './pensao'
import type { RegraEditavel } from './regime'

/**
 * O formulário inteiro, como fica guardado no navegador entre uma visita e outra. O demonstrativo
 * não entra: ele sai do servidor, e recalcular custa um clique.
 */
export interface Rascunho {
  processo: ProcessoRequest
  dataBase: string
  aplicarArt523: boolean
  /** Honorários do art. 523 sobre o débito com a multa, como o CADEJ; desmarcado, sobre o débito, como a DPMG. */
  honorariosSobreAMulta: boolean
  comPensao: boolean
  separarRitos: boolean
  /** Prisão pela lei (as três até o ajuizamento e as do curso); desmarcado, pela DPMG (as três últimas do cálculo). */
  ritosPelaLei: boolean
  pensao: PensaoEditavel
  parcelas: ParcelaRequest[]
  outrasAbertas: boolean
  regimeProprio: boolean
  nomeRegime: string
  regras: RegraEditavel[]
  /**
   * Título omisso ou "juros legais": vale o regime do Tema 1368, e não o padrão nem as `regras`,
   * que ficam guardadas para quem voltar aos critérios do título.
   */
  tema1368: boolean
  /** O regime do Tema 1368 como o servidor o montou; nulo até a busca terminar. */
  regimeTema1368: Regime | null
}

/** Rascunho lido do navegador, com o momento em que foi salvo e a aba que o salvou. */
export interface RascunhoSalvo {
  rascunho: Rascunho
  salvoEm: Date
  aba: string
}

/**
 * Muda quando o formato do rascunho muda. Um rascunho da versão 1 (só salário mínimo) é adaptado,
 * com os campos novos da pensão no padrão; um de outra versão é descartado.
 */
const VERSAO = 2
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
  comTextos(v, ['base', 'percentual', 'rendimentos', 'diaVencimento', 'primeiroMes', 'ultimoMes', 'inicioJuros', 'citacao', 'ajuizamento']) &&
  typeof v.decimoTerceiro === 'boolean' &&
  typeof v.pagoAMaiorComoCredito === 'boolean' &&
  listaDe(v.valoresFixos, (f) => comTextos(f, ['aPartirDe', 'valor'])) &&
  listaDe(v.mudancasRendimentos, (m) => comTextos(m, ['aPartirDe', 'valor'])) &&
  listaDe(v.mudancasBase, (m) => comTextos(m, ['aPartirDe', 'base', 'percentual', 'rendimentos', 'valor'])) &&
  listaDe(v.pagamentos, (p) => comTextos(p, ['mes', 'valor'])) &&
  listaDe(v.abatimentos, (a) => comTextos(a, ['data', 'valor', 'origem']))

const ehRegra = (v: unknown) => comTextos(v, ['inicio', 'fim', 'correcao', 'juros', 'percentual', 'ultimoDiaTaxaFixa'])

/** O regime como a API o devolve: só a forma, que o servidor confere de novo no cálculo. */
const ehRegime = (v: unknown) =>
  ehObjeto(v) &&
  (v.nome === null || typeof v.nome === 'string') &&
  listaDe(v.regras, (r) => comTextos(r, ['inicio', 'correcao', 'juros']))

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
    typeof v.honorariosSobreAMulta === 'boolean' &&
    typeof v.comPensao === 'boolean' &&
    typeof v.separarRitos === 'boolean' &&
    typeof v.ritosPelaLei === 'boolean' &&
    ehPensao(v.pensao) &&
    listaDe(v.parcelas, ehParcela) &&
    typeof v.outrasAbertas === 'boolean' &&
    typeof v.regimeProprio === 'boolean' &&
    typeof v.nomeRegime === 'string' &&
    listaDe(v.regras, ehRegra) &&
    typeof v.tema1368 === 'boolean' &&
    (v.regimeTema1368 === null || ehRegime(v.regimeTema1368))
  )
}

/** Rascunho de antes do Tema 1368: a opção entra desmarcada, e o regime fica como estava. */
function semTema1368(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || 'tema1368' in rascunho) return rascunho
  return { ...rascunho, tema1368: false, regimeTema1368: null }
}

/** Rascunho de antes da base dos honorários: vale o padrão, sobre o débito. */
function semBaseHonorarios(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || 'honorariosSobreAMulta' in rascunho) return rascunho
  return { ...rascunho, honorariosSobreAMulta: false }
}

/** Rascunho de antes da regra dos ritos: vale o padrão do formulário, a regra da DPMG. */
function semRegraRitos(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || 'ritosPelaLei' in rascunho) return rascunho
  return { ...rascunho, ritosPelaLei: false }
}

/** Rascunho de antes da opção do pagamento a maior: vale o padrão do formulário, o crédito da DPMG. */
function semExcedentePagamento(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || !ehObjeto(rascunho.pensao) || 'pagoAMaiorComoCredito' in rascunho.pensao) return rascunho
  return { ...rascunho, pensao: { ...rascunho.pensao, pagoAMaiorComoCredito: true } }
}

/** Rascunho de antes das mudanças dos rendimentos: os rendimentos valem em todos os meses. */
function semMudancasRendimentos(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || !ehObjeto(rascunho.pensao) || 'mudancasRendimentos' in rascunho.pensao) return rascunho
  return { ...rascunho, pensao: { ...rascunho.pensao, mudancasRendimentos: [] } }
}

/** Rascunho de antes das mudanças da base: a base vale a pensão inteira. */
function semMudancasBase(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || !ehObjeto(rascunho.pensao) || 'mudancasBase' in rascunho.pensao) return rascunho
  return { ...rascunho, pensao: { ...rascunho.pensao, mudancasBase: [] } }
}

/**
 * A versão 1 só tinha a pensão em salário mínimo: base, rendimentos, valores fixos, 13º e
 * abatimentos entram com o valor do formulário em branco. O resto é conferido por {@link ehRascunho}.
 */
function daVersao1(rascunho: unknown): unknown {
  if (!ehObjeto(rascunho) || !ehObjeto(rascunho.pensao)) return rascunho
  const { base, rendimentos, valoresFixos, decimoTerceiro, abatimentos } = pensaoVazia()
  return { ...rascunho, pensao: { base, rendimentos, valoresFixos, decimoTerceiro, abatimentos, ...rascunho.pensao } }
}

/** O conteúdo da chave, se for um rascunho que esta versão do formulário sabe abrir. */
function interpretar(texto: string): RascunhoSalvo | null {
  try {
    const salvo: unknown = JSON.parse(texto)
    if (!ehObjeto(salvo) || (salvo.versao !== VERSAO && salvo.versao !== 1)) return null
    const rascunho = semMudancasBase(
      semExcedentePagamento(
        semMudancasRendimentos(
          semRegraRitos(semBaseHonorarios(semTema1368(salvo.versao === 1 ? daVersao1(salvo.rascunho) : salvo.rascunho))),
        ),
      ),
    )
    if (!ehRascunho(rascunho)) return null
    const salvoEm = new Date(typeof salvo.salvoEm === 'string' ? salvo.salvoEm : NaN)
    return {
      rascunho,
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
export function iguais(a: unknown, b: unknown): boolean {
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
