import type { CSSProperties } from 'react'
import type { Demonstrativo, LinhaDemonstrativo, SerieUsada } from './api'
import { Janela } from './Janela'
import { contar, dataHora, fator, mes, variacao } from './formato'
import { mesesDoFator, seriesDosMeses } from './serieDoFator'

interface Props {
  /** A linha cujo fator se confere; nula com a janela fechada. */
  linha: LinhaDemonstrativo | null
  /** O nome da parcela como está na memória: "04/2024" ou a descrição da avulsa. */
  nome: string
  resultado: Demonstrativo
  aoFechar: () => void
}

const deQuando = (s: SerieUsada) =>
  !s.capturadaEm
    ? ''
    : s.snapshot
      ? `, valores guardados com o cálculo salvo em ${dataHora(new Date(s.capturadaEm))}`
      : `, consultada em ${dataHora(new Date(s.capturadaEm))}`

/**
 * A série por trás do fator de uma parcela, mês a mês, com a fonte e a data dos valores. Só mostra
 * o que o servidor leu: o fator acumulado é o dele, sem refazer a multiplicação aqui.
 */
export function FatorMesAMes({ linha, nome, resultado, aoFechar }: Props) {
  const meses = linha ? mesesDoFator(linha.inicioCorrecao, resultado.dataBase, resultado.regime.regras, resultado.series) : []
  const fontes = seriesDosMeses(meses)
  return (
    <Janela id="titulo-fator" titulo={`Fator de correção: ${nome}`} aberta={linha !== null} aoFechar={aoFechar}>
      {linha && (
        <div className="fator-mes-a-mes">
          {meses.length === 0 ? (
            <p className="dica">A parcela vence no mês da data-base: não há mês a corrigir, e o fator é 1.</p>
          ) : (
            <>
              <p className="dica">
                {contar(meses.length, 'mês', 'meses')}, de {mes(meses[0].mes)} a {mes(meses[meses.length - 1].mes)}. O
                fator é o produto de (1 + variação) de cada mês, sem arredondamento.
              </p>
              <div className="rolagem" tabIndex={0} role="region" aria-label="Variação de cada mês">
                <table style={{ '--colunas': 3 } as CSSProperties}>
                  <caption className="sr-only">Variação mensal dos índices que formam o fator</caption>
                  <thead>
                    <tr>
                      <th scope="col">Mês</th>
                      <th scope="col">Índice</th>
                      <th scope="col">Variação</th>
                    </tr>
                  </thead>
                  <tbody>
                    {meses.map((m) => (
                      <tr key={m.mes}>
                        <th scope="row">{mes(m.mes)}</th>
                        <td>{m.serie?.descricao ?? '—'}</td>
                        <td>{m.variacao === null ? '—' : variacao(m.variacao)}</td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="total">
                      <th scope="row" colSpan={2}>
                        Fator acumulado
                      </th>
                      <td>{fator(linha.fatorCorrecao)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          )}
          {fontes.length > 0 && (
            <ul className="fontes">
              {fontes.map((s) => (
                <li key={s.serie}>
                  {s.descricao}: {s.fonte}
                  {deQuando(s)}.
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Janela>
  )
}
