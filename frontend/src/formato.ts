const moedaBr = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const numeroBr = (casas: number) =>
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas })
const duasCasas = numeroBr(2)
const seteCasas = numeroBr(7)
/**
 * Quatro casas, e até seis quando a taxa as tem: o BCB publica a taxa legal com seis, e com menos
 * quem confere os juros a partir do percentual pode errar o centavo. O PDF usa a mesma regra.
 */
const taxa = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 4, maximumFractionDigits: 6 })

export const moeda = (valor: number) => moedaBr.format(valor)
/** Valor com duas casas e sem o símbolo, para colar em planilha. */
export const decimal = (valor: number) => duasCasas.format(valor)
export const percentual = (valor: number) => `${taxa.format(valor)}%`
export const fator = (valor: number) => seteCasas.format(valor)

/** Data AAAA-MM-DD em dd/mm/aaaa. */
export const data = (iso: string | null) => (iso ? iso.split('-').reverse().join('/') : '—')

const dataHoraBr = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/** Data e hora locais em dd/mm/aaaa, hh:mm. */
export const dataHora = (d: Date) => dataHoraBr.format(d)

/** Mês AAAA-MM em mm/aaaa. */
export const mes = (iso: string) => iso.split('-').reverse().join('/')

/** Mês AAAA-MM em mm/aa, para eixos estreitos. */
export const mesCurto = (iso: string) => {
  const [ano, m] = iso.split('-')
  return `${m}/${ano.slice(2)}`
}

export const contar = (n: number, singular: string, plural: string) => `${n} ${n === 1 ? singular : plural}`

const variacaoBr = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 6 })

/** Variação mensal de um índice, em %, com as casas que a série publicou (até seis). */
export const variacao = (valor: number) => `${variacaoBr.format(valor)}%`
