import type { CalculoRequest, Regime, ResumoCalculo } from './api'
import { data } from './formato'
import { fimDoMesAnterior, parcelaVazia } from './caso'
import { paraCampo } from './numero'
import { pensaoVazia, type PensaoEditavel } from './pensao'
import { iguais, type Rascunho } from './rascunho'
import { paraEditavel } from './regime'

/** O nome que o servidor dá a um regime informado sem nome (`RegimeDto.NOME_PADRAO`). */
const NOME_SEM_NOME = 'Critérios fixados no título'

/** Decimal da API, que no pedido salvo chega como número JSON, como o formulário o mostra. */
const campo = (valor: string | number) => paraCampo(String(valor))

/**
 * Valor em reais com ao menos duas casas, como foi digitado: o JSON perde o zero do fim, e
 * "350,5" no campo pareceria outro valor. Feito no texto, sem passar por ponto flutuante.
 */
const reais = (valor: string | number) => {
  const [inteiro, fracao = ''] = String(valor).split('.')
  return `${inteiro},${fracao.padEnd(2, '0')}`
}

function paraPensaoEditavel(p: NonNullable<CalculoRequest['pensao']>): PensaoEditavel {
  const base = p.base ?? 'SALARIO_MINIMO'
  const vazia = pensaoVazia()
  return {
    base,
    percentual: p.percentual == null ? '' : campo(p.percentual),
    rendimentos: p.rendimentos == null ? '' : reais(p.rendimentos),
    valoresFixos: p.valoresFixos?.length
      ? p.valoresFixos.map((v) => ({ aPartirDe: v.aPartirDe, valor: reais(v.valor) }))
      : vazia.valoresFixos,
    decimoTerceiro: p.decimoTerceiro ?? false,
    diaVencimento: String(p.diaVencimento),
    primeiroMes: p.primeiroMes,
    ultimoMes: p.ultimoMes,
    inicioJuros: p.inicioJuros,
    citacao: p.citacao ?? '',
    pagamentos: (p.pagamentos ?? []).map((pg) => ({ mes: pg.mes, valor: reais(pg.valor) })),
    abatimentos: (p.abatimentos ?? []).map((a) => ({ data: a.data, valor: reais(a.valor), origem: a.origem ?? '' })),
    ajuizamento: p.ajuizamento ?? '',
  }
}

/**
 * O regime de um cálculo salvo comparado com o padrão de hoje. O servidor salva o regime resolvido,
 * então o padrão volta por extenso, e só o nome diz que ele foi o escolhido:
 * - `padrao`: as regras ainda são as do padrão de hoje;
 * - `padrao-anterior`: foi o padrão, mas o padrão mudou desde o salvamento;
 * - `desconhecido`: não deu para buscar o padrão de hoje e comparar;
 * - `proprio`: critérios fixados no título.
 *
 * @param padrao o regime de `GET /api/regimes/padrao`, ou nulo se não deu para buscá-lo
 */
export type RegimeSalvo = 'padrao' | 'padrao-anterior' | 'desconhecido' | 'proprio'

export function regimeSalvo(pedido: CalculoRequest, padrao: Regime | null): RegimeSalvo {
  if (!pedido.regime) return 'padrao'
  if (padrao === null) return 'desconhecido'
  if (pedido.regime.nome !== padrao.nome) return 'proprio'
  return iguais(paraEditavel(pedido.regime.regras), paraEditavel(padrao.regras)) ? 'padrao' : 'padrao-anterior'
}

/**
 * O pedido de um cálculo salvo como formulário, para abrir, duplicar ou atualizar.
 *
 * Se o regime salvo ainda for o padrão de hoje, o formulário volta a "Regime padrão do TJMG", como
 * foi preenchido. Se o padrão mudou desde então, ou não se sabe qual é (`padrao` nulo), as regras
 * salvas entram como critérios do título: reabrir não pode mudar o regime de um cálculo que já foi
 * para os autos. Ver `regimeSalvo`. O Tema 1368 segue a mesma regra: só volta à opção dele se o
 * regime salvo ainda for o de hoje, e o cálculo leva o regime salvo, e não o de hoje.
 *
 * @param padrao o regime de `GET /api/regimes/padrao`, ou nulo se não deu para buscá-lo
 * @param tema o regime de `GET /api/regimes/tema-1368`, ou nulo se não deu para buscá-lo
 */
export function paraRascunho(pedido: CalculoRequest, padrao: Regime | null, tema: Regime | null = null): Rascunho {
  // Os campos do processo podem voltar nulos do servidor.
  const processo = pedido.processo
  const comPensao = pedido.pensao != null
  const parcelas = (pedido.parcelas ?? []).map((p) => ({
    descricao: p.descricao,
    valor: reais(p.valor),
    inicioCorrecao: p.inicioCorrecao,
    inicioJuros: p.inicioJuros ?? '',
  }))
  const regras = pedido.regime ? paraEditavel(pedido.regime.regras) : []
  const ehPadrao = regimeSalvo(pedido, padrao) === 'padrao'
  const ehTema = ehOTema1368(pedido.regime, tema)
  const nome = pedido.regime?.nome ?? ''
  return {
    processo: {
      numero: processo?.numero ?? '',
      juizo: processo?.juizo ?? '',
      exequente: processo?.exequente ?? '',
      executado: processo?.executado ?? '',
    },
    dataBase: pedido.dataBase,
    aplicarArt523: pedido.aplicarArt523,
    comPensao,
    // Sem pensão, a opção não aparece; marcada, ela volta como o formulário em branco a deixa.
    separarRitos: pedido.pensao ? pedido.pensao.ajuizamento != null : true,
    pensao: pedido.pensao ? paraPensaoEditavel(pedido.pensao) : pensaoVazia(),
    // Sem pensão, as parcelas avulsas são o cálculo, e o formulário pede ao menos uma.
    parcelas: !comPensao && parcelas.length === 0 ? [parcelaVazia()] : parcelas,
    outrasAbertas: comPensao && parcelas.length > 0,
    regimeProprio: !ehPadrao && !ehTema,
    nomeRegime: ehPadrao || ehTema || nome === NOME_SEM_NOME ? '' : nome,
    regras: ehPadrao || ehTema ? [] : regras,
    tema1368: ehTema,
    regimeTema1368: ehTema ? (pedido.regime ?? null) : null,
  }
}

/** Se o regime salvo é o do Tema 1368 de hoje: mesmo nome e mesmas regras. */
function ehOTema1368(regime: Regime | undefined, tema: Regime | null): boolean {
  if (!regime || !tema || regime.nome !== tema.nome) return false
  return iguais(paraEditavel(regime.regras), paraEditavel(tema.regras))
}

/** O título que o servidor dá a um cálculo salvo sem título; o campo o mostra como exemplo. */
export function tituloPadrao(pedido: Pick<CalculoRequest, 'dataBase' | 'processo'>): string {
  const numero = pedido.processo?.numero?.trim()
  return numero ? `Processo ${numero}` : `Cálculo com data-base em ${data(pedido.dataBase)}`
}

/** Sem acento e em minúsculas: "Execução" acha "execucao". */
const semAcento = (texto: string) => texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

const soDigitos = (texto: string) => texto.replace(/\D/g, '')

/**
 * Os cálculos salvos que batem com a busca, na mesma ordem. Cada palavra precisa aparecer no
 * título, no regime ou no número do processo, sem diferença de acento nem de maiúsculas.
 *
 * Uma palavra só de números e pontuação procura no título e no processo, e acha o processo também
 * pelos dígitos: "5001234562024" acha "5001234-56.2024.8.13.0024", com ou sem a máscara do CNJ. Ela
 * não procura no regime, cujo nome tem datas e leis ("07/2024", "14.905"): "2024" acharia todo
 * cálculo no regime padrão, e não os processos de 2024.
 */
export function filtrarCalculos(lista: readonly ResumoCalculo[], busca: string): ResumoCalculo[] {
  const palavras = semAcento(busca).split(/\s+/).filter(Boolean)
  if (palavras.length === 0) return [...lista]
  return lista.filter((r) => {
    const texto = semAcento(`${r.titulo} ${r.regime} ${r.processo ?? ''}`)
    const textoSemRegime = semAcento(`${r.titulo} ${r.processo ?? ''}`)
    const digitos = soDigitos(r.processo ?? '')
    return palavras.every((p) => {
      if (!/^[\d.\-/]+$/.test(p)) return texto.includes(p)
      const numero = soDigitos(p)
      return textoSemRegime.includes(p) || (numero !== '' && digitos.includes(numero))
    })
  })
}

/**
 * A data-base de um cálculo salvo trazido para hoje: o fim do mês anterior, a mesma do formulário
 * em branco. Nula se o cálculo já vai até lá, e não há o que atualizar.
 */
export function novaDataBase(dataBase: string, hoje = new Date()): string | null {
  const nova = fimDoMesAnterior(hoje)
  return dataBase < nova ? nova : null
}

/** O caso trazido para hoje e, se a pensão foi estendida, de que mês a que mês foi a última parcela. */
export interface Atualizacao {
  rascunho: Rascunho
  ultimaParcela: { de: string; para: string } | null
}

/**
 * O caso de um cálculo salvo trazido para hoje: a data-base avança como em `novaDataBase` e, se a
 * pensão ainda corria na data-base antiga (a última parcela é do mês dela), a última parcela vai
 * ao mês da nova, com as prestações que venceram no meio. Uma pensão que terminou antes, ou cuja
 * última parcela já ia além (um fim fixado no título, como a maioridade), fica como estava. No
 * caso ambíguo (a pensão terminou justamente no mês da data-base antiga), ela é estendida, e quem
 * abre é avisado do mês de antes para poder voltá-lo.
 */
export function atualizarRascunho(rascunho: Rascunho, hoje = new Date()): Atualizacao {
  const dataBase = novaDataBase(rascunho.dataBase, hoje)
  if (!dataBase) return { rascunho, ultimaParcela: null }
  const de = rascunho.pensao.ultimoMes
  const para = dataBase.slice(0, 7)
  const emCurso = rascunho.comPensao && de === rascunho.dataBase.slice(0, 7) && de < para
  if (!emCurso) return { rascunho: { ...rascunho, dataBase }, ultimaParcela: null }
  return {
    rascunho: { ...rascunho, dataBase, pensao: { ...rascunho.pensao, ultimoMes: para } },
    ultimaParcela: { de, para },
  }
}
