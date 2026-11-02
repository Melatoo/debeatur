import type { CalculoRequest } from './api'

/** Espera depois da última edição antes de recalcular, para não pedir um cálculo por tecla. */
export const ESPERA_RECALCULO_MS = 400

/**
 * Identidade do que foi calculado. O processo fica de fora: ele só identifica o PDF, e mudá-lo
 * não deixa o demonstrativo desatualizado nem pede outro cálculo.
 */
export function chaveDoPedido(pedido: CalculoRequest): string {
  const { processo: _processo, ...calculado } = pedido
  return JSON.stringify(calculado)
}

/**
 * Se todos os campos do formulário são válidos, sem disparar os eventos `invalid` nem as
 * mensagens do navegador, como fariam `checkValidity` e `reportValidity`: o recálculo roda
 * enquanto a pessoa digita, e um campo pela metade não deve abrir seções nem mostrar balões.
 */
export function formularioValido(formulario: HTMLFormElement | null): boolean {
  if (!formulario) return false
  return Array.from(formulario.elements).every(
    (campo) => !('validity' in campo) || (campo as HTMLInputElement).validity.valid,
  )
}
