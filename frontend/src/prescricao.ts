import { contar, data, mes } from './formato'

/** O que o aviso precisa da pensão: as parcelas e o dia do vencimento. */
export interface PensaoPrescricao {
  primeiroMes: string
  ultimoMes: string
  diaVencimento: number
}

export interface ParcelasAntigas {
  /** As competências, em ordem, das parcelas vencidas mais de dois anos antes do marco. */
  meses: string[]
  /** A data que interrompe a prescrição: o ajuizamento ou, sem ele, a data-base. */
  marco: string
  porAjuizamento: boolean
}

const MES = /^\d{4}-\d{2}$/
const DATA = /^\d{4}-\d{2}-\d{2}$/
const doisDigitos = (n: number) => String(n).padStart(2, '0')

/** O vencimento da parcela do mês: no dia escolhido ou, em mês sem ele, no último dia. */
export function vencimento(competencia: string, dia: number): string {
  const [ano, m] = competencia.split('-').map(Number)
  const ultimoDia = new Date(Date.UTC(ano, m, 0)).getUTCDate()
  return `${competencia}-${doisDigitos(Math.min(dia, ultimoDia))}`
}

/** A mesma data dois anos antes; 29/02 vira 28/02. */
function doisAnosAntes(iso: string): string {
  const [ano, m, d] = iso.split('-').map(Number)
  const ultimoDia = new Date(Date.UTC(ano - 2, m, 0)).getUTCDate()
  return `${ano - 2}-${doisDigitos(m)}-${doisDigitos(Math.min(d, ultimoDia))}`
}

function mesSeguinte(competencia: string): string {
  const [ano, m] = competencia.split('-').map(Number)
  return m === 12 ? `${ano + 1}-01` : `${ano}-${doisDigitos(m + 1)}`
}

/**
 * As parcelas da pensão vencidas mais de dois anos antes do ajuizamento, que podem estar
 * prescritas (art. 206, §2º, do CC): o prazo corre de cada vencimento, e a citação o interrompe
 * desde a propositura (art. 240, §1º, do CPC). Sem o ajuizamento, a data-base faz as vezes dele.
 * Só avisa: se a prescrição corre ou não depende de fatos que o cálculo não tem (arts. 197 e 198).
 *
 * @returns null se não há parcela antiga, ou se os dados ainda não bastam para dizer
 */
export function parcelasAntigas(
  pensao: PensaoPrescricao,
  ajuizamento: string | null,
  dataBase: string,
): ParcelasAntigas | null {
  const { primeiroMes, ultimoMes, diaVencimento } = pensao
  const marco = ajuizamento && DATA.test(ajuizamento) ? ajuizamento : dataBase
  if (!MES.test(primeiroMes) || !MES.test(ultimoMes) || !DATA.test(marco) || !(diaVencimento >= 1 && diaVencimento <= 31)) {
    return null
  }
  // Vencida no próprio dia do limite, o prazo acaba no dia do marco: ainda não prescreveu.
  const limite = doisAnosAntes(marco)
  const meses: string[] = []
  for (let m = primeiroMes; m <= ultimoMes && vencimento(m, diaVencimento) < limite; m = mesSeguinte(m)) {
    meses.push(m)
  }
  return meses.length > 0 ? { meses, marco, porAjuizamento: marco === ajuizamento } : null
}

/** O aviso por extenso, o mesmo no formulário e no demonstrativo. */
export function textoDoAviso({ meses, marco, porAjuizamento }: ParcelasAntigas): { fato: string; detalhe: string } {
  const varias = meses.length > 1
  const quais = varias ? `de ${mes(meses[0])} a ${mes(meses.at(-1)!)}` : `de ${mes(meses[0])}`
  const referencia = porAjuizamento
    ? `do ajuizamento (${data(marco)})`
    : `da data-base (${data(marco)}), que faz as vezes do ajuizamento enquanto ele não é informado`
  return {
    fato: `${contar(meses.length, 'parcela', 'parcelas')} ${quais} ${varias ? 'podem estar prescritas' : 'pode estar prescrita'}.`,
    detalhe:
      `${varias ? 'Venceram' : 'Venceu'} mais de dois anos antes ${referencia} (art. 206, §2º, do CC), e o cálculo ` +
      `${varias ? 'as inclui' : 'a inclui'}. A prescrição não corre contra o absolutamente incapaz, como o menor de 16 ` +
      'anos (art. 198, I, do CC), nem entre ascendentes e descendentes durante o poder familiar (art. 197, II): ' +
      'confira antes de levar estes números aos autos.',
  }
}
