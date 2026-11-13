import { cloneElement, useContext, useEffect, useId, useRef, useState, type ReactElement, type ReactNode, type Ref } from 'react'
import { contar } from './formato'
import { TentouCalcular, erroDoControle, type ControleValidavel, type ErroDoCampo, type ErroDoResumo } from './validacao'

interface PropsDoControle {
  id?: string
  value?: string
  min?: string
  max?: string
  required?: boolean
  disabled?: boolean
  'aria-describedby'?: string
  'aria-invalid'?: boolean
  'data-se-vazio'?: string
}

interface Props {
  rotulo: ReactNode
  /** O controle: `input`, `select`, `textarea` ou `CampoDecimal`, que repassa as props ao `input`. */
  children: ReactElement<PropsDoControle>
  /** A mensagem do obrigatório vazio, quando a genérica ("Preencha este campo.") não diz o bastante. */
  seVazio?: string
  /**
   * O erro de uma regra que o HTML não tem, como o dia de 1 a 31 num campo de texto, ou '' se vale.
   * O `Campo` o aplica com `setCustomValidity` antes de ler a validade: feito no efeito de quem o
   * renderiza, que roda depois do dele, a mensagem sairia com o valor anterior.
   */
  erroProprio?: string
  className?: string
}

/**
 * Um campo do formulário com o rótulo e a `MensagemErro`. A validade vem do próprio controle
 * (`required`, `min`, `max`, `setCustomValidity`), a mesma que barra o envio, para a mensagem
 * escrita e o bloqueio nunca discordarem.
 */
export function Campo({ rotulo, children, seVazio, erroProprio, className }: Props) {
  const id = useId()
  const rotuloRef = useRef<HTMLLabelElement>(null)
  const tentou = useContext(TentouCalcular)
  // Conta as saídas, e não só se houve uma: a data digitada pela metade não dispara o onChange, e
  // cada saída precisa reler a validade.
  const [saidas, setSaidas] = useState(0)
  const [erro, setErro] = useState<ErroDoCampo | null>(null)
  const { value, min, max, required, disabled } = children.props

  // A validade é do DOM: o controle, que é filho, faz o setCustomValidity no efeito dele, que roda
  // antes deste. Relida a cada mudança do que a define.
  useEffect(() => {
    const controle = rotuloRef.current?.control as ControleValidavel | null | undefined
    if (controle && erroProprio !== undefined) controle.setCustomValidity(erroProprio)
    const novo = controle ? erroDoControle(controle) : null
    setErro((atual) => (atual?.mensagem === novo?.mensagem && atual?.vazio === novo?.vazio ? atual : novo))
  }, [value, min, max, required, disabled, seVazio, erroProprio, saidas, tentou])

  const mostrar = erro !== null && (tentou || (saidas > 0 && !erro.vazio))
  const idErro = `${id}-erro`
  const descricao = [mostrar ? idErro : undefined, children.props['aria-describedby']].filter(Boolean).join(' ')

  return (
    <label ref={rotuloRef} className={className} onBlur={() => setSaidas((n) => n + 1)}>
      <span className="rotulo">{rotulo}</span>
      {mostrar && <MensagemErro id={idErro}>{erro.mensagem}</MensagemErro>}
      {cloneElement(children, {
        id: children.props.id ?? `${id}-campo`,
        'aria-invalid': mostrar || undefined,
        'aria-describedby': descricao || undefined,
        'data-se-vazio': seVazio,
      })}
    </label>
  )
}

/**
 * O que corrigir num campo, entre o rótulo e o controle: abaixo dele, a mensagem desalinharia o
 * controle dos vizinhos da mesma linha, que se alinham pela base. Fica fora da árvore de
 * acessibilidade porque, dentro do `<label>`, entraria no nome do campo; chega ao leitor de tela
 * pelo `aria-describedby`, que lê o texto mesmo assim.
 */
export function MensagemErro({ id, children }: { id: string; children: ReactNode }) {
  return (
    <span id={id} className="mensagem-erro" aria-hidden="true">
      {children}
    </span>
  )
}

interface PropsResumo {
  erros: ErroDoResumo[]
  ref: Ref<HTMLElement>
}

/**
 * Os erros de um Calcular barrado com dois campos inválidos ou mais. Recebe o foco, para o leitor
 * de tela anunciar quantos são, e cada item leva ao campo.
 */
export function ResumoErros({ erros, ref }: PropsResumo) {
  const id = useId()
  return (
    <section ref={ref} className="resumo-erros" tabIndex={-1} aria-labelledby={id}>
      <h2 id={id}>Corrija {contar(erros.length, 'campo', 'campos')} para calcular</h2>
      <ul>
        {erros.map(({ controle, rotulo, mensagem }, i) => (
          <li key={i}>
            <a
              href={`#${controle.id}`}
              onClick={(e) => {
                // O link só rolaria até o campo; o foco faz o leitor de tela ler o campo e o erro.
                e.preventDefault()
                controle.focus()
              }}
            >
              {rotulo}
            </a>
            : {mensagem}
          </li>
        ))}
      </ul>
    </section>
  )
}
