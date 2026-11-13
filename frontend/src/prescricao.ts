import { contar, data, mes } from './formato'

/** O que o aviso precisa da pensão: as parcelas e o dia do vencimento. */
export interface PensaoPrescricao {
  primeiroMes: string
  ultimoMes: string
  diaVencimento: number
}

export interface ParcelasAntigas {
  /** As competências, em ordem, das parcelas com saldo vencidas mais de dois anos antes do marco. */
  meses: string[]
  /** A data que interrompe a prescrição: o ajuizamento ou, sem ele, a data-base. */
  marco: string
  porAjuizamento: boolean
}

const MES = /^\d{4}-\d{2}$/
const DATA = /^\d{4}-\d{2}-\d{2}$/
const doisDigitos = (n: number) => String(n).padStart(2, '0')

/** O vencimento da parcela do mês, como no motor: no dia escolhido ou, em mês sem ele, no último dia. */
export function vencimento(competencia: string, dia: number): string {
  const [ano, m] = competencia.split('-').map(Number)
  const ultimoDia = new Date(Date.UTC(ano, m, 0)).getUTCDate()
  return `${competencia}-${doisDigitos(Math.min(dia, ultimoDia))}`
}

/**
 * O fim do prazo de dois anos contado de `inicio` (art. 132, §3º, do CC): no mesmo dia do ano, ou,
 * sem ele (29/02 em ano não bissexto), no dia seguinte, 01/03.
 */
export function fimDoPrazo(inicio: string): string {
  const [ano, m, d] = inicio.split('-').map(Number)
  const ultimoDia = new Date(Date.UTC(ano + 2, m, 0)).getUTCDate()
  return d > ultimoDia ? `${ano + 2}-${doisDigitos(m + 1)}-01` : `${ano + 2}-${doisDigitos(m)}-${doisDigitos(d)}`
}

function mesSeguinte(competencia: string): string {
  const [ano, m] = competencia.split('-').map(Number)
  return m === 12 ? `${ano + 1}-01` : `${ano}-${doisDigitos(m + 1)}`
}

/**
 * As parcelas da pensão vencidas mais de dois anos antes do ajuizamento, que podem estar
 * prescritas (art. 206, §2º, do CC): o prazo corre de cada vencimento, e na execução a citação o
 * interrompe desde a propositura (arts. 802, parágrafo único, e 240, §2º, do CPC). Sem o
 * ajuizamento, a data-base faz as vezes dele. Só avisa: se a prescrição corre depende de fatos que
 * o cálculo não tem (arts. 197 e 198 do CC), e o prazo que acaba em dia sem expediente se prorroga.
 *
 * @param quitadas competências já pagas por inteiro: sem saldo, não há o que prescrever
 * @returns null se não há parcela antiga com saldo, ou se os dados ainda não bastam para dizer
 */
export function parcelasAntigas(
  pensao: PensaoPrescricao,
  ajuizamento: string | null,
  dataBase: string,
  quitadas: ReadonlySet<string> = new Set(),
): ParcelasAntigas | null {
  const { primeiroMes, ultimoMes, diaVencimento } = pensao
  const marco = ajuizamento && DATA.test(ajuizamento) ? ajuizamento : dataBase
  if (
    !MES.test(primeiroMes) ||
    !MES.test(ultimoMes) ||
    !DATA.test(marco) ||
    !Number.isInteger(diaVencimento) ||
    diaVencimento < 1 ||
    diaVencimento > 31
  ) {
    return null
  }
  const meses: string[] = []
  // As competências vêm em ordem, e o vencimento cresce com elas: a primeira que não prescreveu encerra.
  // O prazo que acaba no próprio dia do ajuizamento ainda não acabou.
  for (let m = primeiroMes; m <= ultimoMes && fimDoPrazo(vencimento(m, diaVencimento)) < marco; m = mesSeguinte(m)) {
    if (!quitadas.has(m)) meses.push(m)
  }
  return meses.length > 0 ? { meses, marco, porAjuizamento: marco === ajuizamento } : null
}

/** As competências da pensão pagas por inteiro num demonstrativo: ficam nele, mas com saldo zero. */
export const competenciasQuitadas = (linhas: { valorOriginal: number; pensao: { mes: string; valorPago: number } | null }[]) =>
  new Set(linhas.flatMap((l) => (l.pensao && l.pensao.valorPago > 0 && l.valorOriginal === 0 ? [l.pensao.mes] : [])))

/** As competências em faixas contínuas: "01/2021 a 03/2021 e 05/2021". */
function faixas(meses: string[]): string {
  const grupos: string[][] = []
  for (const m of meses) {
    const ultimo = grupos.at(-1)
    if (ultimo && mesSeguinte(ultimo.at(-1)!) === m) ultimo.push(m)
    else grupos.push([m])
  }
  const textos = grupos.map((g) => (g.length === 1 ? mes(g[0]) : `${mes(g[0])} a ${mes(g.at(-1)!)}`))
  return textos.length === 1 ? textos[0] : `${textos.slice(0, -1).join(', ')} e ${textos.at(-1)}`
}

/** O aviso por extenso, o mesmo no formulário e no demonstrativo. */
export function textoDoAviso({ meses, marco, porAjuizamento }: ParcelasAntigas): { fato: string; detalhe: string } {
  const varias = meses.length > 1
  const parcelas = `${contar(meses.length, 'parcela', 'parcelas')} (${faixas(meses)})`
  const referencia = porAjuizamento ? `do ajuizamento (${data(marco)})` : `da data-base (${data(marco)})`
  return {
    fato: porAjuizamento
      ? `${parcelas} ${varias ? 'podem estar prescritas' : 'pode estar prescrita'}.`
      : `Se a execução ainda não foi proposta, ${parcelas} ${varias ? 'podem estar prescritas' : 'pode estar prescrita'}.`,
    detalhe:
      `${varias ? 'Venceram' : 'Venceu'} mais de dois anos antes ${referencia} (art. 206, §2º, do CC), e o cálculo ` +
      `${varias ? 'as inclui' : 'a inclui'}. A prescrição não corre contra o menor de 16 anos (arts. 198, I, e 3º do ` +
      'CC) nem entre ascendentes e descendentes durante o poder familiar (art. 197, II): confira antes de levar estes ' +
      'números aos autos.',
  }
}
