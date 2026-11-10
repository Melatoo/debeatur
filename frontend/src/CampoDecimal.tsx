import { useEffect, useRef, type InputHTMLAttributes } from 'react'
import { erroDeDecimal } from './numero'

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
    ref.current?.setCustomValidity(erroDeDecimal(value, casas, milhar))
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
