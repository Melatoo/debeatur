import { useId, useState, type ReactNode } from 'react'

interface Props {
  /** A frase curta, sempre à vista: diz para que serve o campo. */
  curta: ReactNode
  /** O detalhe, aberto pelo "Saiba mais". */
  children: ReactNode
  /** Id da frase curta, para o campo apontar com `aria-describedby`; o detalhe fica de fora. */
  id?: string
  className?: string
}

/**
 * Dica com uma frase curta e o resto atrás de "Saiba mais". O botão, e não um `<details>`, para a
 * frase e o botão ficarem na mesma linha; o detalhe aberto aparece logo abaixo.
 */
export function Dica({ curta, children, id, className }: Props) {
  const [aberta, setAberta] = useState(false)
  const idDetalhe = useId()
  return (
    <div className={className ? `dica ${className}` : 'dica'}>
      <span id={id}>{curta}</span>{' '}
      <button
        type="button"
        className="saiba-mais"
        aria-expanded={aberta}
        aria-controls={idDetalhe}
        onClick={() => setAberta(!aberta)}
      >
        {/* O rótulo não muda: o aria-expanded já diz se o detalhe está aberto. */}
        Saiba mais
      </button>
      <p id={idDetalhe} className="dica-detalhe" hidden={!aberta}>
        {children}
      </p>
    </div>
  )
}
