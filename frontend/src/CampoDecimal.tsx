import { useEffect, useRef, type InputHTMLAttributes } from 'react'
import { casasDecimais, lerDecimal, pontoAmbiguo } from './numero'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'value' | 'onChange'> & {
  value: string
  onValor: (texto: string) => void
  /** Máximo de casas decimais aceitas. */
  casas: number
  /** Se o ponto pode separar milhares (valores em reais) ou é só decimal (percentuais). */
  milhar?: boolean
}

/**
 * Campo de número com vírgula decimal. O `type="number"` recusa a vírgula conforme o navegador,
 * então o campo é de texto e a validação, feita aqui, chega ao navegador como erro do formulário.
 */
export function CampoDecimal({ value, onValor, casas, milhar = true, ...resto }: Props) {
  const ref = useRef<HTMLInputElement>(null)

  useEffect(() => {
    ref.current?.setCustomValidity(mensagemDeErro(value, casas, milhar))
  }, [value, casas, milhar])

  return (
    <input
      {...resto}
      ref={ref}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      value={value}
      onChange={(e) => onValor(e.target.value)}
    />
  )
}

function mensagemDeErro(texto: string, casas: number, milhar: boolean): string {
  // Vazio fica a cargo do `required`; um campo opcional vazio é válido.
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
