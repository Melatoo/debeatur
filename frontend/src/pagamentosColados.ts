import type { PagamentoRequest } from './api'
import { mes as mesBr } from './formato'
import { casasDecimais, lerDecimal, paraCampo, pontoAmbiguo } from './numero'

const NOMES_POR_EXTENSO = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]

/** Nome do mês, abreviado ou por extenso, e o seu número; "marco" entra porque muita planilha tira o cedilha. */
const NOMES = new Map<string, number>(
  NOMES_POR_EXTENSO.flatMap((nome, i): [string, number][] => [
    [nome.slice(0, 3), i + 1],
    [nome, i + 1],
  ]),
)
NOMES.set('marco', 3)

const doisDigitos = (n: number) => String(n).padStart(2, '0')

/** Ano e mês válidos em AAAA-MM, ou null. */
function anoMes(ano: number, mes: number): string | null {
  if (mes < 1 || mes > 12 || ano < 1900 || ano > 2999) return null
  return `${ano}-${doisDigitos(mes)}`
}

/**
 * Lê o mês de uma célula de planilha e devolve AAAA-MM, ou null. Aceita "04/2024", "4/2024", uma
 * data inteira ("10/04/2024", "2024-04-10"), "2024-04" e o mês por nome ("abr/24", "abril de 2024"),
 * que é como o Excel mostra o formato "mmm/aa". O ano numérico com dois dígitos ("04/24") não entra:
 * "10/04" também pode ser dia e mês, e um mês trocado abate o pagamento da parcela errada.
 */
export function lerMes(texto: string): string | null {
  const t = texto.trim().toLowerCase()
  let m = /^(\d{1,2})\/(\d{4})$/.exec(t)
  if (m) return anoMes(Number(m[2]), Number(m[1]))
  m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(t)
  if (m) return Number(m[1]) <= 31 ? anoMes(Number(m[3]), Number(m[2])) : null
  m = /^(\d{4})-(\d{2})(-(\d{2}))?$/.exec(t)
  if (m) return !m[4] || Number(m[4]) <= 31 ? anoMes(Number(m[1]), Number(m[2])) : null
  m = /^([a-zç]+)\.?(?:\/|-|\s+de\s+|\s+)(\d{2}|\d{4})$/.exec(t)
  if (m) {
    const numero = NOMES.get(m[1])
    if (numero === undefined) return null
    // Com o nome do mês, "abr/24" não se confunde com dia e mês: o ano curto é deste século.
    const ano = m[2].length === 2 ? 2000 + Number(m[2]) : Number(m[2])
    return anoMes(ano, numero)
  }
  return null
}

export type Colagem =
  | { ok: true; pagamentos: PagamentoRequest[] }
  | { ok: false; erros: string[] }

interface Limites {
  /** Período da pensão em AAAA-MM; vazio quando ainda não foi preenchido. */
  primeiroMes: string
  ultimoMes: string
  /** Meses que já têm pagamento no formulário. */
  jaInformados: string[]
}

/**
 * Lê pagamentos colados de uma planilha: uma linha por pagamento, com o mês e o valor separados por
 * tabulação (o que o Excel e o Google Planilhas põem na área de transferência) ou ponto e vírgula.
 * Uma primeira linha sem mês nem valor é o cabeçalho e fica de fora; linhas em branco também.
 *
 * Ou entra tudo, ou nada: com uma linha que não se lê, a colagem devolve os erros de cada linha e
 * nenhum pagamento, para não ficar metade da planilha no formulário sem que se perceba.
 */
export function lerPagamentosColados(texto: string, limites: Limites): Colagem {
  const linhas = texto.replace(/\r\n?/g, '\n').split('\n')
  const erros: string[] = []
  const pagamentos: PagamentoRequest[] = []
  const vistos = new Map<string, number>(limites.jaInformados.filter(Boolean).map((m) => [m, 0]))

  linhas.forEach((linha, i) => {
    if (!linha.trim()) return
    const numero = i + 1
    const celulas = linha.split(/\t|;/).map((c) => c.trim())
    // Colunas vazias nas pontas aparecem quando a seleção pega uma coluna a mais.
    while (celulas.length > 0 && celulas[celulas.length - 1] === '') celulas.pop()
    while (celulas.length > 0 && celulas[0] === '') celulas.shift()
    const [textoMes = '', textoValor = ''] = celulas
    const mes = lerMes(textoMes)
    const valor = lerDecimal(textoValor)

    if (pagamentos.length === 0 && erros.length === 0 && mes === null && valor === null) return
    if (celulas.length !== 2) {
      erros.push(`Linha ${numero}: esperava duas colunas, o mês e o valor; vieram ${celulas.length}.`)
      return
    }
    if (mes === null) {
      erros.push(`Linha ${numero}: "${textoMes}" não é um mês (ex.: 04/2024 ou abr/2024).`)
      return
    }
    if (valor === null) {
      erros.push(
        pontoAmbiguo(textoValor)
          ? `Linha ${numero}: em "${textoValor}" o ponto é ambíguo; escreva com os centavos (ex.: 1.500,00).`
          : `Linha ${numero}: "${textoValor}" não é um valor em reais (ex.: 1.234,56).`,
      )
      return
    }
    if (casasDecimais(valor) > 2) {
      erros.push(`Linha ${numero}: "${textoValor}" tem mais de duas casas decimais.`)
      return
    }
    if (Number(valor) <= 0) {
      erros.push(`Linha ${numero}: o valor pago deve ser maior que zero.`)
      return
    }
    if ((limites.primeiroMes && mes < limites.primeiroMes) || (limites.ultimoMes && mes > limites.ultimoMes)) {
      erros.push(`Linha ${numero}: ${mesBr(mes)} está fora do período da pensão.`)
      return
    }
    const anterior = vistos.get(mes)
    if (anterior !== undefined) {
      erros.push(
        anterior === 0
          ? `Linha ${numero}: ${mesBr(mes)} já tem pagamento no formulário.`
          : `Linha ${numero}: ${mesBr(mes)} repete a linha ${anterior}; some os valores do mês numa linha só.`,
      )
      return
    }
    vistos.set(mes, numero)
    pagamentos.push({ mes, valor: paraCampo(valor) })
  })

  if (erros.length > 0) return { ok: false, erros }
  if (pagamentos.length === 0) return { ok: false, erros: ['Nenhum pagamento encontrado no texto colado.'] }
  return { ok: true, pagamentos }
}
