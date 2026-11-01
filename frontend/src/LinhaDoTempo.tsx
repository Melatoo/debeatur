import type { CSSProperties } from 'react'
import type { LinhaDemonstrativo } from './api'
import { contar, data, mes, mesCurto, moeda } from './formato'

interface Props {
  /** Só as parcelas da pensão entram no gráfico, uma barra por mês. */
  linhas: LinhaDemonstrativo[]
  ajuizamento: string | null
}

/**
 * Parcelas da pensão no tempo: a altura é o valor atualizado, a cor o rito e a parte hachurada o
 * que foi pago no mês. A linha tracejada marca o ajuizamento, que define a separação dos ritos.
 */
export function LinhaDoTempo({ linhas, ajuizamento }: Props) {
  const meses = linhas.filter((l) => l.pensao)
  if (meses.length < 2) return null

  const maior = Math.max(...meses.map((l) => l.subtotal + (l.pensao?.valorPago ?? 0)))
  const n = meses.length
  const intervaloRotulo = Math.ceil(n / 12)
  // Fronteira antes da primeira parcela que vence depois do ajuizamento.
  const antesDoAjuizamento = ajuizamento ? meses.filter((l) => l.inicioCorrecao <= ajuizamento).length : 0
  const marcaAjuizamento = ajuizamento && antesDoAjuizamento > 0 && antesDoAjuizamento < n
  const comPagamento = meses.some((l) => (l.pensao?.valorPago ?? 0) > 0)

  const prisao = meses.filter((l) => l.rito === 'PRISAO').length
  const penhora = meses.filter((l) => l.rito === 'PENHORA').length
  const resumo = ajuizamento
    ? `${contar(n, 'parcela', 'parcelas')} da pensão: ${prisao} no rito da prisão e ${penhora} no da penhora; ajuizamento em ${data(ajuizamento)}.`
    : `${contar(n, 'parcela', 'parcelas')} da pensão, de ${mes(meses[0].pensao!.mes)} a ${mes(meses[n - 1].pensao!.mes)}.`

  return (
    <figure
      className="linha-do-tempo"
      style={{ '--n': n, '--k': antesDoAjuizamento, '--g': n > 24 ? '3px' : '6px' } as CSSProperties}
    >
      <div className="grafico" role="img" aria-label={resumo}>
        {meses.map((l) => {
          const pago = l.pensao?.valorPago ?? 0
          const classe = l.rito ? l.rito.toLowerCase() : 'sem-rito'
          return (
            <div
              key={l.pensao!.mes}
              className="coluna"
              title={`${mes(l.pensao!.mes)}: ${moeda(l.subtotal)} atualizado${pago ? `, ${moeda(pago)} pago` : ''}`}
            >
              {pago > 0 && <span className="pago" style={{ height: `${(pago / maior) * 100}%` }} />}
              <span className={`barra ${classe}`} style={{ height: `${(l.subtotal / maior) * 100}%` }} />
            </div>
          )
        })}
        {marcaAjuizamento && (
          <div className={antesDoAjuizamento / n > 0.5 ? 'marco a-esquerda' : 'marco'}>
            <span>Ajuizamento {data(ajuizamento)}</span>
          </div>
        )}
      </div>
      <div className="eixo" aria-hidden="true">
        {meses.map((l, i) => (
          // Em tela estreita, só metade dos rótulos aparece (os de classe "menor" somem).
          <span key={l.pensao!.mes} className={i % (intervaloRotulo * 2) === 0 ? undefined : 'menor'}>
            {i % intervaloRotulo === 0 ? mesCurto(l.pensao!.mes) : ''}
          </span>
        ))}
      </div>
      <figcaption className="legenda">
        {ajuizamento ? (
          <>
            <span>
              <i className="amostra prisao" />
              Rito da prisão
            </span>
            <span>
              <i className="amostra penhora" />
              Rito da penhora
            </span>
          </>
        ) : (
          <span>
            <i className="amostra sem-rito" />
            Valor atualizado
          </span>
        )}
        {comPagamento && (
          <span>
            <i className="amostra pago" />
            Pago no mês (valor nominal)
          </span>
        )}
      </figcaption>
    </figure>
  )
}
