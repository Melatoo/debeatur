import { useEffect, useRef, type ReactNode, type RefObject } from 'react'

/**
 * Abre e fecha o `<dialog>` modal junto com o estado; o Esc e o Fechar passam pelo `onClose`. O
 * `showModal` leva o foco ao primeiro controle, o Fechar; o campo marcado com `data-foco-inicial`
 * o recebe em seguida. O `autoFocus` do React não serve: roda antes do `showModal`.
 */
function useModal(janela: RefObject<HTMLDialogElement | null>, aberta: boolean) {
  useEffect(() => {
    const d = janela.current
    if (!d) return
    if (aberta && !d.open) {
      d.showModal()
      d.querySelector<HTMLElement>('[data-foco-inicial]')?.focus()
    }
    if (!aberta && d.open) d.close()
  }, [janela, aberta])
}

interface Props {
  id: string
  titulo: string
  aberta: boolean
  aoFechar: () => void
  children: ReactNode
}

/** Janela modal com título e Fechar: Cálculos salvos, Salvar cálculo, a série de um fator. */
export function Janela({ id, titulo, aberta, aoFechar, children }: Props) {
  const janela = useRef<HTMLDialogElement>(null)
  useModal(janela, aberta)
  return (
    <dialog ref={janela} className="janela" aria-labelledby={id} onClose={aoFechar}>
      <div className="cabecalho-janela">
        <h2 id={id}>{titulo}</h2>
        <button type="button" className="secundario" onClick={aoFechar}>
          Fechar
        </button>
      </div>
      {aberta && children}
    </dialog>
  )
}
