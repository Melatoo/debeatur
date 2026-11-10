import type { PagamentoRequest } from './api'
import { mes as mesBr } from './formato'
import { erroDeDecimal, lerDecimal, paraCampo } from './numero'
import { anoMes } from './regime'

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

/** Ano e mês válidos em AAAA-MM, ou null. */
function anoMesValido(ano: number, mes: number): string | null {
  if (mes < 1 || mes > 12 || ano < 1900 || ano > 2999) return null
  return anoMes(ano, mes)
}

/** Dia de uma data inteira: só importa que exista, de 1 a 31, porque o pagamento vale pelo mês. */
const diaValido = (dia: string | undefined) => dia === undefined || (Number(dia) >= 1 && Number(dia) <= 31)

/**
 * Ano de dois dígitos depois do nome do mês: deste século, salvo se der um ano que ainda não chegou.
 * Pagamento é coisa passada, então "dez/99" é 1999, e não 2099.
 */
function anoCurto(aa: string): number {
  const ano = 2000 + Number(aa)
  return ano > new Date().getFullYear() ? ano - 100 : ano
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
  if (m) return anoMesValido(Number(m[2]), Number(m[1]))
  m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(t)
  if (m) return diaValido(m[1]) ? anoMesValido(Number(m[3]), Number(m[2])) : null
  m = /^(\d{4})-(\d{2})(?:-(\d{2}))?$/.exec(t)
  if (m) return diaValido(m[3]) ? anoMesValido(Number(m[1]), Number(m[2])) : null
  m = /^([a-zç]+)\.?(?:\/|-|\s+de\s+|\s+)(\d{2}|\d{4})$/.exec(t)
  if (m) {
    const numero = NOMES.get(m[1])
    if (numero === undefined) return null
    // Com o nome do mês, "abr/24" não se confunde com dia e mês.
    const ano = m[2].length === 2 ? anoCurto(m[2]) : Number(m[2])
    return anoMesValido(ano, numero)
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
 * A primeira linha fica de fora quando é um cabeçalho, com rótulos em vez de mês e valor; linhas em
 * branco também. Uma linha de dados que não se lê é erro, mesmo sendo a primeira.
 *
 * Ou entra tudo, ou nada: com uma linha que não se lê, a colagem devolve os erros de cada linha e
 * nenhum pagamento, para não ficar metade da planilha no formulário sem que se perceba.
 */
export function lerPagamentosColados(texto: string, limites: Limites): Colagem {
  const linhas = texto.replace(/\r\n?/g, '\n').split('\n')
  const erros: string[] = []
  const pagamentos: PagamentoRequest[] = []
  const vistos = new Map<string, number>(limites.jaInformados.filter(Boolean).map((m) => [m, 0]))
  const primeira = linhas.findIndex((linha) => linha.trim())

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

    // Cabeçalho só na primeira linha e só com rótulos ("Mês", "Valor pago"): uma linha de dados
    // ilegível, como "04/24 1.500", tem de dar erro, e não sumir como se fosse cabeçalho.
    const rotulos = celulas.length > 0 && celulas.every((c) => /\p{L}/u.test(c))
    if (i === primeira && rotulos && mes === null && valor === null) return
    if (celulas.length !== 2) {
      erros.push(`Linha ${numero}: esperava duas colunas, o mês e o valor; vieram ${celulas.length}.`)
      return
    }
    if (mes === null) {
      erros.push(`Linha ${numero}: "${textoMes}" não é um mês (ex.: 04/2024 ou abr/2024).`)
      return
    }
    // A mesma regra do campo "Valor pago", para a colagem não aceitar o que o campo recusa.
    const erroValor = erroDeDecimal(textoValor, 2)
    if (erroValor || valor === null) {
      erros.push(`Linha ${numero}, valor "${textoValor}": ${erroValor}`)
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
