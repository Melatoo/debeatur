import { data, mes } from './formato'
import { anoMes, mesAnterior } from './regime'

/** O 422 da API quando falta o valor de uma série no mês (`/problemas/serie-indisponivel`). */
export interface SerieIndisponivel {
  /** O índice (`IPCA`, `TAXA_LEGAL`…) ou `SALARIO_MINIMO`. */
  serie: string
  /** Como a série se escreve no demonstrativo: "IPCA (IBGE)". */
  descricao: string
  /** AAAA-MM */
  mes: string
}

export const TIPO_SERIE_INDISPONIVEL = '/problemas/serie-indisponivel'

/** O problem+json como `SerieIndisponivel`, ou nulo se for outro erro ou vier incompleto. */
export function lerSerieIndisponivel(problema: unknown): SerieIndisponivel | null {
  if (typeof problema !== 'object' || problema === null) return null
  const { type, serie, descricao, mes } = problema as Record<string, unknown>
  if (type !== TIPO_SERIE_INDISPONIVEL) return null
  if (typeof serie !== 'string' || typeof descricao !== 'string' || typeof mes !== 'string') return null
  if (!/^\d{4}-\d{2}$/.test(mes)) return null
  return { serie, descricao, mes }
}

export interface AvisoDeSerie {
  /** O fato, para ir em negrito. */
  fato: string
  /** O porquê e o que fazer. */
  detalhe: string
  /** Se o mês só não saiu ainda: é aviso, e não erro. */
  recente: boolean
  /** A maior data-base que não depende do mês que falta; nula quando recuar não resolve. */
  dataBaseSugerida: string | null
}

/** Último dia (AAAA-MM-DD) de um mês AAAA-MM. */
function ultimoDia(mesIso: string): string {
  const [ano, m] = mesIso.split('-').map(Number)
  return `${mesIso}-${String(new Date(ano, m, 0).getDate()).padStart(2, '0')}`
}

/**
 * O que dizer quando falta o valor de uma série. O cálculo usa os meses até o anterior ao da
 * data-base, então, se falta o mês M, qualquer data-base dentro de M não depende dele: a sugestão
 * é o último dia de M. Um mês recente (o anterior ao de hoje, ou depois) só não foi divulgado
 * ainda; um mês antigo é lacuna da série, e recuar a data-base não resolve.
 *
 * @param dataBase a data-base do pedido que falhou (AAAA-MM-DD)
 */
export function avisoDeSerie(s: SerieIndisponivel, dataBase: string, hoje = new Date()): AvisoDeSerie {
  const mesDeHoje = anoMes(hoje.getFullYear(), hoje.getMonth() + 1)
  const recente = s.mes >= mesAnterior(mesDeHoje)
  const salarioMinimo = s.serie === 'SALARIO_MINIMO'
  if (!recente) {
    return {
      fato: `${s.descricao} sem valor para ${mes(s.mes)}.`,
      detalhe: salarioMinimo
        ? 'O mês não está na série do Banco Central. Confira o período da pensão.'
        : 'O mês não está na série do Banco Central. Confira o período e o índice das regras de correção.',
      recente,
      dataBaseSugerida: null,
    }
  }
  if (salarioMinimo) {
    return {
      fato: `Salário mínimo de ${mes(s.mes)} ainda não publicado.`,
      detalhe: 'Confira a última parcela da pensão.',
      recente,
      dataBaseSugerida: null,
    }
  }
  const sugestao = ultimoDia(s.mes)
  const recuar = sugestao < dataBase
  return {
    fato: `${s.descricao} de ${mes(s.mes)} ainda não divulgado.`,
    detalhe: recuar
      ? `Os índices de um mês saem por volta do dia 10 do mês seguinte. Com a data-base até ${data(sugestao)}, o cálculo não depende dele.`
      : 'Os índices de um mês saem por volta do dia 10 do mês seguinte.',
    recente,
    dataBaseSugerida: recuar ? sugestao : null,
  }
}
