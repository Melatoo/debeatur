/**
 * Lê um decimal digitado à brasileira e devolve o texto que a API espera ("1234.56"), ou null se
 * não for um número. Aceita vírgula decimal e ponto de milhar ("1.234,56"), como se copia de uma
 * decisão, e também o ponto decimal ("1234.56"), desde que não se confunda com o de milhar.
 *
 * @param milhar se o ponto pode separar milhares; nos percentuais não pode, e "1.5" é 1,5
 */
export function lerDecimal(texto: string, milhar = true): string | null {
  const limpo = texto.replace(/R\$|\s/g, '')
  if (/^\d+(,\d+)?$/.test(limpo)) return limpo.replace(',', '.')
  // Ponto seguido de grupos de três dígitos é de milhar: "1.500" são mil e quinhentos.
  if (milhar && /^\d{1,3}(\.\d{3})+(,\d+)?$/.test(limpo)) return limpo.replace(/\./g, '').replace(',', '.')
  if (/^\d+\.\d+$/.test(limpo)) return limpo
  return null
}

/** Casas decimais de um texto já lido por {@link lerDecimal}. */
export const casasDecimais = (lido: string) => lido.split('.')[1]?.length ?? 0

/** Decimal no formato da API ("1234.56") como o formulário o mostra ("1234,56"). */
export const paraCampo = (valor: number | string) => String(valor).replace('.', ',')
