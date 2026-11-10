import { useEffect, type RefObject } from 'react'

/**
 * Publica a altura de um elemento fixo na rolagem (sticky) numa variável CSS do contêiner que
 * rola, para o `scroll-padding` dele reservar esse espaço e o campo que recebe o foco nunca ficar
 * por baixo (WCAG 2.4.11). A altura muda com o texto que quebra, por isso é medida, e não fixa.
 *
 * @param conteiner o que rola; sem ele, a página (`<html>`)
 */
export function useAlturaFixa(
  elemento: RefObject<HTMLElement | null>,
  variavel: string,
  conteiner?: RefObject<HTMLElement | null>,
) {
  useEffect(() => {
    const fixo = elemento.current
    const alvo = conteiner ? conteiner.current : document.documentElement
    if (!fixo || !alvo || typeof ResizeObserver === 'undefined') return
    const observador = new ResizeObserver(() => alvo.style.setProperty(variavel, `${fixo.offsetHeight}px`))
    observador.observe(fixo)
    return () => {
      observador.disconnect()
      alvo.style.removeProperty(variavel)
    }
  }, [elemento, variavel, conteiner])
}
