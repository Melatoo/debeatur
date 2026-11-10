/** Tira os espaços das pontas e o "R$" do começo; um espaço no meio do número continua lá e o invalida. */
const limpar = (texto: string) => texto.trim().replace(/^R\$\s*/, '')

/**
 * Um só ponto seguido de três dígitos e sem vírgula: "1.500" tanto pode ser mil e quinhentos, à
 * brasileira, quanto 1,5 colado de uma planilha em inglês. Num valor que vai para os autos, não se
 * adivinha.
 */
const AMBIGUO = /^[1-9]\d{0,2}\.\d{3}$/

/**
 * Lê um decimal digitado à brasileira e devolve o texto que a API espera ("1234.56"), ou null se
 * não for um número. Aceita vírgula decimal e ponto de milhar ("1.234,56"), como se copia de uma
 * decisão, e também o ponto decimal ("1234.56"), desde que não se confunda com o de milhar.
 *
 * @param milhar se o ponto pode separar milhares; nos percentuais não pode, e "1.5" é 1,5
 */
export function lerDecimal(texto: string, milhar = true): string | null {
  const limpo = limpar(texto)
  if (/^\d+(,\d+)?$/.test(limpo)) return limpo.replace(',', '.')
  if (milhar) {
    if (AMBIGUO.test(limpo)) return null
    // Ponto seguido de grupos de três dígitos é de milhar quando há vírgula ou mais de um ponto:
    // "1.500,00" e "1.500.000". O primeiro grupo não começa com zero, senão "0.500" viraria quinhentos.
    if (/^[1-9]\d{0,2}(\.\d{3})+(,\d+)?$/.test(limpo)) return limpo.replace(/\./g, '').replace(',', '.')
  }
  if (/^\d+\.\d+$/.test(limpo)) return limpo
  return null
}

/** Se o texto é um valor em reais recusado só pela ambiguidade do ponto, como "1.500". */
export const pontoAmbiguo = (texto: string) => AMBIGUO.test(limpar(texto))

/**
 * Converte o texto do formulário para o formato da API. O formulário já barra o valor inválido;
 * se ainda assim chegar um, o pedido não sai, em vez de levar o texto cru à API.
 *
 * @param campo nome do campo, para a mensagem de erro
 */
export function exigirDecimal(texto: string, campo: string, milhar = true): string {
  const lido = lerDecimal(texto, milhar)
  if (lido === null) throw new Error(`${campo} inválido: ${texto}`)
  return lido
}

/** Casas decimais de um texto já lido por {@link lerDecimal}. */
export const casasDecimais = (lido: string) => lido.split('.')[1]?.length ?? 0

/**
 * Por que um decimal digitado não vale, ou '' se vale. É a regra do campo de valor do formulário e
 * da colagem de pagamentos, para os dois aceitarem e explicarem o mesmo texto do mesmo jeito.
 * Vazio vale: fica a cargo do `required`, e um campo opcional vazio é válido.
 *
 * @param casas máximo de casas decimais aceitas
 * @param milhar se o ponto pode separar milhares (valores em reais) ou é só decimal (percentuais)
 */
export function erroDeDecimal(texto: string, casas: number, milhar = true): string {
  if (!texto.trim()) return ''
  const lido = lerDecimal(texto, milhar)
  if (lido === null) {
    if (milhar && pontoAmbiguo(texto)) {
      return 'O ponto é ambíguo: para milhar, escreva sem o ponto ou com os centavos (ex.: 1500 ou 1.500,00); para decimais, use vírgula (ex.: 1,5).'
    }
    return milhar
      ? 'Informe só números, com vírgula antes dos centavos (ex.: 1.234,56).'
      : 'Informe só números, com vírgula decimal (ex.: 0,5).'
  }
  if (casasDecimais(lido) > casas) return `Use no máximo ${casas} casas decimais.`
  if (Number(lido) <= 0) return 'Informe um valor maior que zero.'
  return ''
}

/** Decimal no formato da API ("1234.56") como o formulário o mostra ("1234,56"). */
export const paraCampo = (valor: number | string) => String(valor).replace('.', ',')
