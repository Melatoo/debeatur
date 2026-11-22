import type { CalculoRequest, ParcelaRequest, ProcessoRequest, Regime } from './api'
import { mes } from './formato'
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
  honorariosSobreAMulta: false,
  comPensao: true,
  separarRitos: true,
  pensao: pensaoVazia(),
  parcelas: [],
  outrasAbertas: false,
  regimeProprio: false,
  nomeRegime: '',
  regras: [],
  tema1368: false,
  regimeTema1368: null,
})

/** O que do formulário entra no cálculo: o processo e as seções abertas ou fechadas ficam de fora. */
export type DadosDoCalculo = Pick<
  Rascunho,
  | 'dataBase'
  | 'aplicarArt523'
  | 'honorariosSobreAMulta'
  | 'parcelas'
  | 'comPensao'
  | 'pensao'
  | 'separarRitos'
  | 'regimeProprio'
  | 'nomeRegime'
  | 'regras'
  | 'tema1368'
  | 'regimeTema1368'
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
    // O padrão (sobre o débito) não vai no pedido, que fica igual ao de antes da opção.
    ...(r.aplicarArt523 && r.honorariosSobreAMulta ? { baseHonorarios: 'DEBITO_COM_MULTA' as const } : {}),
    parcelas: r.parcelas.map((p) => ({ ...p, valor: exigirDecimal(p.valor, 'Valor da parcela'), inicioJuros: p.inicioJuros || null })),
    pensao: r.comPensao ? paraPensao(r.pensao, r.separarRitos) : undefined,
    regime: regimeDoPedido(r),
  }
}

/** O primeiro mês (AAAA-MM) com termo inicial no caso: da pensão ou das parcelas avulsas. */
function primeiroMesDoCaso(r: DadosDoCalculo): string | undefined {
  const meses = [
    ...(r.comPensao && r.pensao.primeiroMes ? [r.pensao.primeiroMes] : []),
    ...r.parcelas.flatMap((p) => [p.inicioCorrecao, p.inicioJuros ?? '']),
  ]
    .filter(Boolean)
    .map((d) => d.slice(0, 7))
  return meses.sort()[0]
}

function regimeDoPedido(r: DadosDoCalculo): Regime | undefined {
  if (r.tema1368) {
    // Sem o regime, o pedido iria ao padrão do TJMG: melhor não calcular que calcular com outro critério.
    if (!r.regimeTema1368) throw new Error('O regime do Tema 1368 ainda não foi carregado.')
    const inicio = r.regimeTema1368.regras[0]?.inicio
    const primeiro = primeiroMesDoCaso(r)
    if (inicio && primeiro && primeiro < inicio) {
      throw new Error(
        `O Tema 1368 vale desde ${mes(inicio)}, com o CC/2002, e o cálculo começa em ${mes(primeiro)}. ` +
          'Informe os critérios do título em “Outros critérios fixados no título”.',
      )
    }
    return r.regimeTema1368
  }
  return r.regimeProprio ? paraRegime(r.nomeRegime, r.regras) : undefined
}
