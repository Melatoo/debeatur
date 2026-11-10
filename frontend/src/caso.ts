import type { CalculoRequest, ParcelaRequest, ProcessoRequest } from './api'
import { exigirDecimal } from './numero'
import { paraPensao, pensaoVazia } from './pensao'
import type { Rascunho } from './rascunho'
import { paraRegime } from './regime'

/**
 * Último dia do mês anterior. O cálculo usa os índices até o mês anterior ao da data-base, e o
 * índice do mês que acabou de fechar só sai lá pelo dia 10; antes disso, com a data de hoje, o
 * cálculo falharia. Depois, o padrão fica para trás e a dica do campo pede para avançá-lo.
 */
export const fimDoMesAnterior = (hoje = new Date()) =>
  new Date(hoje.getFullYear(), hoje.getMonth(), 0).toLocaleDateString('sv-SE')

export const parcelaVazia = (): ParcelaRequest => ({ descricao: '', valor: '', inicioCorrecao: '', inicioJuros: '' })
export const estaVazia = (p: ParcelaRequest) => !p.descricao && !p.valor && !p.inicioCorrecao && !p.inicioJuros

export const processoVazio = (): ProcessoRequest => ({ numero: '', juizo: '', exequente: '', executado: '' })

/** Os campos sem espaços nas pontas; sem nenhum preenchido, o processo nem vai no pedido. */
export function paraProcesso(processo: ProcessoRequest): ProcessoRequest | undefined {
  const limpo: ProcessoRequest = {
    numero: processo.numero.trim(),
    juizo: processo.juizo.trim(),
    exequente: processo.exequente.trim(),
    executado: processo.executado.trim(),
  }
  return Object.values(limpo).some(Boolean) ? limpo : undefined
}

/** O formulário em branco, como ele abre e como fica depois do "Limpar". */
export const rascunhoVazio = (): Rascunho => ({
  processo: processoVazio(),
  dataBase: fimDoMesAnterior(),
  aplicarArt523: true,
  comPensao: true,
  separarRitos: true,
  pensao: pensaoVazia(),
  parcelas: [],
  outrasAbertas: false,
  regimeProprio: false,
  nomeRegime: '',
  regras: [],
})

/** O que do formulário entra no cálculo: o processo e as seções abertas ou fechadas ficam de fora. */
export type DadosDoCalculo = Pick<
  Rascunho,
  'dataBase' | 'aplicarArt523' | 'parcelas' | 'comPensao' | 'pensao' | 'separarRitos' | 'regimeProprio' | 'nomeRegime' | 'regras'
>

/**
 * O formulário como pedido de cálculo, sem o processo, que só identifica o PDF.
 *
 * @throws Error se algum valor nem pode ser lido, com a mensagem do campo
 */
export function paraPedido(r: DadosDoCalculo): CalculoRequest {
  return {
    dataBase: r.dataBase,
    aplicarArt523: r.aplicarArt523,
    parcelas: r.parcelas.map((p) => ({ ...p, valor: exigirDecimal(p.valor, 'Valor da parcela'), inicioJuros: p.inicioJuros || null })),
    pensao: r.comPensao ? paraPensao(r.pensao, r.separarRitos) : undefined,
    regime: r.regimeProprio ? paraRegime(r.nomeRegime, r.regras) : undefined,
  }
}
