export interface ParcelaRequest {
  descricao: string
  valor: string
  inicioCorrecao: string
  inicioJuros: string | null
}

export interface CalculoRequest {
  dataBase: string
  aplicarArt523: boolean
  parcelas: ParcelaRequest[]
}

export interface LinhaDemonstrativo {
  descricao: string
  valorOriginal: number
  inicioCorrecao: string
  inicioJuros: string | null
  fatorCorrecao: number
  valorCorrigido: number
  percentualJuros: number
  valorJuros: number
  subtotal: number
}

export interface Demonstrativo {
  dataBase: string
  regime: string
  linhas: LinhaDemonstrativo[]
  totalCorrigido: number
  totalJuros: number
  debitoAtualizado: number
  multaArt523: number
  honorariosArt523: number
  total: number
}

export async function calcular(request: CalculoRequest): Promise<Demonstrativo> {
  const resposta = await fetch('/api/calculos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => null)
    throw new Error(problema?.detail ?? `Erro ${resposta.status} ao calcular`)
  }
  return resposta.json()
}
