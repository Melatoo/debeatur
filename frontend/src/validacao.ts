import { createContext } from 'react'
import { data, mes } from './formato'

/**
 * Se a pessoa já tentou calcular este caso. Antes disso, só o valor mal escrito aparece, ao sair do
 * campo; depois, todo campo inválido mostra a mensagem, até o obrigatório vazio.
 */
export const TentouCalcular = createContext(false)

/** O que a mensagem precisa de um campo do formulário: o tipo, os limites e a validade nativa. */
export interface CampoValidavel {
  type: string
  min?: string
  max?: string
  validity: Pick<
    ValidityState,
    'valid' | 'valueMissing' | 'badInput' | 'customError' | 'rangeUnderflow' | 'rangeOverflow'
  >
  validationMessage: string
}

export interface ErroDoCampo {
  mensagem: string
  /** Se o erro é só a falta do valor: não aparece ao sair do campo, só depois de Calcular. */
  vazio: boolean
}

/** O limite de um campo de data ou de mês, como a tela o escreve. */
const limite = (campo: CampoValidavel, valor: string) => (campo.type === 'month' ? mes(valor) : data(valor))

/**
 * Por que o campo não vale, em português e com o que fazer, ou null se vale. O balão do navegador
 * muda com o idioma dele e diz só "Preencha este campo."; aqui a mensagem é a mesma em todo lugar.
 * O erro do `setCustomValidity` já vem escrito assim (`erroDeDecimal`, `erroDoDiaVencimento`).
 *
 * @param seVazio a mensagem do campo obrigatório vazio, quando a genérica não diz o bastante
 */
export function erroDoCampo(campo: CampoValidavel, seVazio?: string): ErroDoCampo | null {
  const { validity } = campo
  if (validity.valid) return null
  if (validity.customError) return { mensagem: campo.validationMessage, vazio: false }
  // Na data digitada pela metade, o valor fica vazio e o navegador acusa as duas coisas: vale o badInput.
  if (validity.badInput) {
    return {
      mensagem:
        campo.type === 'month'
          ? 'Mês incompleto. Informe o mês e o ano (ex.: 03/2025).'
          : 'Data incompleta ou inexistente. Informe dia, mês e ano (ex.: 05/03/2025).',
      vazio: false,
    }
  }
  if (validity.valueMissing) {
    const mensagem =
      seVazio ??
      (campo.type === 'date'
        ? 'Informe a data (ex.: 05/03/2025).'
        : campo.type === 'month'
          ? 'Informe o mês e o ano (ex.: 03/2025).'
          : 'Preencha este campo.')
    return { mensagem, vazio: true }
  }
  if (validity.rangeUnderflow && campo.min) {
    return { mensagem: `Informe ${limite(campo, campo.min)} ou depois.`, vazio: false }
  }
  if (validity.rangeOverflow && campo.max) {
    return { mensagem: `Informe ${limite(campo, campo.max)} ou antes.`, vazio: false }
  }
  return { mensagem: campo.validationMessage || 'Confira este campo.', vazio: false }
}

export type ControleValidavel = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement

/** O erro de um controle do formulário, com a mensagem de vazio que o `Campo` gravou nele. */
export const erroDoControle = (controle: ControleValidavel) =>
  controle.willValidate ? erroDoCampo(controle, controle.dataset.seVazio) : null

export interface ErroDoResumo {
  controle: ControleValidavel
  rotulo: string
  mensagem: string
}

/** O nome do campo no resumo, com o grupo quando há vários iguais: "Pagamento 2, Valor pago (R$)". */
function rotuloNoResumo(controle: ControleValidavel): string {
  const rotulo = controle.labels?.[0]
  const texto = (rotulo?.querySelector('.rotulo') ?? rotulo)?.textContent?.trim() || controle.name
  const grupo = controle.closest('fieldset')?.querySelector(':scope > legend')?.textContent?.trim()
  return grupo ? `${grupo}, ${texto}` : texto
}

/** Os campos inválidos do formulário, na ordem da tela, sem disparar os eventos `invalid`. */
export function errosDoFormulario(formulario: HTMLFormElement): ErroDoResumo[] {
  const erros: ErroDoResumo[] = []
  for (const elemento of Array.from(formulario.elements)) {
    // Fieldset e botão não têm o que validar: o willValidate deles é falso, e o erroDoControle os pula.
    if (!('willValidate' in elemento)) continue
    const controle = elemento as ControleValidavel
    const erro = erroDoControle(controle)
    if (erro) erros.push({ controle, rotulo: rotuloNoResumo(controle), mensagem: erro.mensagem })
  }
  return erros
}
