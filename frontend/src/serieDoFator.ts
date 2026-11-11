import type { Regra, SerieUsada } from './api'
import { mesSeguinte } from './regime'

/** Um mês da correção de uma parcela: o índice que a regra do mês manda usar e a variação lida. */
export interface MesDoFator {
  /** AAAA-MM */
  mes: string
  /** Nulo se nenhuma regra cobre o mês, o que o servidor não deixaria calcular. */
  serie: SerieUsada | null
  /** Em pontos percentuais; nula se a série não traz o mês. */
  variacao: number | null
}

/**
 * Os meses que entram no fator de uma parcela, do mês do início da correção até o anterior ao da
 * data-base, como o `MotorCalculo` os percorre, com o índice da regra vigente em cada um. Só
 * descreve: o fator continua o do servidor, sem refazer a conta aqui.
 *
 * @param inicioCorrecao AAAA-MM-DD
 * @param dataBase AAAA-MM-DD
 */
export function mesesDoFator(inicioCorrecao: string, dataBase: string, regras: Regra[], series: SerieUsada[]): MesDoFator[] {
  const meses: MesDoFator[] = []
  const mesBase = dataBase.slice(0, 7)
  for (let mes = inicioCorrecao.slice(0, 7); mes < mesBase; mes = mesSeguinte(mes)) {
    const regra = regras.find((r) => r.inicio <= mes && (r.fim == null || r.fim >= mes))
    const serie = (regra && series.find((s) => s.serie === regra.correcao)) ?? null
    const variacao = serie?.valores.find((v) => v.mes === mes)?.valor ?? null
    meses.push({ mes, serie, variacao })
  }
  return meses
}

/** As séries que aparecem nos meses, na ordem em que aparecem, sem repetir. */
export function seriesDosMeses(meses: MesDoFator[]): SerieUsada[] {
  const vistas: SerieUsada[] = []
  for (const { serie } of meses) {
    if (serie && !vistas.includes(serie)) vistas.push(serie)
  }
  return vistas
}
