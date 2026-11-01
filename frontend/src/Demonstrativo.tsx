import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { CalculoRequest, Demonstrativo as Resultado, LinhaDemonstrativo, Regra, Rito } from './api'
import { contar, data, decimal, fator, mes, moeda, percentual } from './formato'
import { LinhaDoTempo } from './LinhaDoTempo'
import { INDICES, descreverPeriodo } from './regime'

interface Props {
  /** O pedido que gerou o resultado: o demonstrativo descreve o que foi calculado, não o formulário atual. */
  pedido: CalculoRequest
  resultado: Resultado
}

const RITOS: Record<Rito, { titulo: string; regra: string }> = {
  PRISAO: {
    titulo: 'Rito da prisão',
    regra: 'Art. 528, §3º, do CPC: as três parcelas vencidas até o ajuizamento e as que vencem depois dele.',
  },
  PENHORA: {
    titulo: 'Rito da penhora',
    regra: 'Art. 528, §8º, do CPC: as parcelas mais antigas, com multa e honorários do art. 523, §1º.',
  },
}

const porVencimento = (a: LinhaDemonstrativo, b: LinhaDemonstrativo) => a.inicioCorrecao.localeCompare(b.inicioCorrecao)
const soma = (linhas: LinhaDemonstrativo[], campo: 'valorCorrigido' | 'valorJuros' | 'subtotal') =>
  linhas.reduce((total, l) => total + l[campo], 0)

const nomeDaParcela = (l: LinhaDemonstrativo) => {
  if (!l.pensao) return l.descricao
  const quitada = l.pensao.valorPago > 0 && l.valorOriginal === 0
  return `${mes(l.pensao.mes)}${quitada ? ' (quitada)' : ''}`
}

/** Regras do regime que alcançam o período do cálculo: do primeiro termo inicial ao mês anterior à data-base. */
function regrasDoPeriodo(regras: Regra[], linhas: LinhaDemonstrativo[], dataBase: string): Regra[] {
  const inicios = linhas.flatMap((l) => [l.inicioCorrecao, l.inicioJuros ?? l.inicioCorrecao]).map((d) => d.slice(0, 7))
  const primeiro = inicios.sort()[0]
  const ultimo = dataBase.slice(0, 7)
  if (!primeiro) return regras
  return regras.filter((r) => r.inicio <= ultimo && (r.fim == null || r.fim >= primeiro))
}

export function Demonstrativo({ pedido, resultado }: Props) {
  const titulo = useRef<HTMLHeadingElement>(null)
  const [aviso, setAviso] = useState('')
  const { linhas, ritos } = resultado
  const pensao = pedido.pensao
  const comSalarioMinimo = linhas.some((l) => l.pensao)
  const comPagamento = linhas.some((l) => (l.pensao?.valorPago ?? 0) > 0)
  const acrescimos = resultado.multaArt523 + resultado.honorariosArt523

  useEffect(() => {
    // Leva quem calculou até o resultado, que fica abaixo do formulário.
    const reduzir = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    titulo.current?.scrollIntoView({ behavior: reduzir ? 'auto' : 'smooth', block: 'start' })
    titulo.current?.focus({ preventScroll: true })
  }, [resultado])

  const colunas = [
    'Parcela',
    'Vencimento',
    ...(comSalarioMinimo ? ['Salário mínimo'] : []),
    'Devido',
    ...(comPagamento ? ['Pago'] : []),
    'Fator',
    'Saldo corrigido',
    'Juros (%)',
    'Juros',
    'Atualizado',
  ]

  async function copiar() {
    const cabecalho = [...(ritos ? ['Rito'] : []), ...colunas]
    const ordenadas = [...linhas].sort(porVencimento)
    const corpo = ordenadas.map((l) => [
      ...(ritos && l.rito ? [RITOS[l.rito].titulo] : ritos ? [''] : []),
      nomeDaParcela(l),
      data(l.inicioCorrecao),
      ...(comSalarioMinimo ? [l.pensao ? decimal(l.pensao.salarioMinimo) : ''] : []),
      decimal(l.pensao ? l.pensao.valorDevido : l.valorOriginal),
      ...(comPagamento ? [l.pensao?.valorPago ? decimal(l.pensao.valorPago) : ''] : []),
      fator(l.fatorCorrecao),
      decimal(l.valorCorrigido),
      percentual(l.percentualJuros),
      decimal(l.valorJuros),
      decimal(l.subtotal),
    ])
    const totais = [
      ...(ritos
        ? [
            ['Rito da prisão', decimal(ritos.prisao.debitoAtualizado)],
            ['Rito da penhora', decimal(ritos.penhora.debitoAtualizado)],
          ]
        : []),
      ['Débito atualizado', decimal(resultado.debitoAtualizado)],
      ...(acrescimos > 0
        ? [
            ['Multa (art. 523, §1º)', decimal(resultado.multaArt523)],
            ['Honorários (art. 523, §1º)', decimal(resultado.honorariosArt523)],
          ]
        : []),
      ['Total da execução', decimal(resultado.total)],
    ]
    const texto = [cabecalho, ...corpo, [], ...totais].map((linha) => linha.join('\t')).join('\n')
    try {
      await navigator.clipboard.writeText(texto)
      setAviso('Tabela copiada. Cole no Word ou numa planilha.')
    } catch {
      setAviso('O navegador não deixou copiar. Use Imprimir ou selecione a tabela com o mouse.')
    }
  }

  function tabela(id: string, rotulo: ReactNode, descricao: string | null, das: LinhaDemonstrativo[], rodape: ReactNode) {
    const ordenadas = [...das].sort(porVencimento)
    const antesDosTotais = colunas.length - 4
    return (
      <section className="memoria" aria-labelledby={id}>
        <h3 id={id}>{rotulo}</h3>
        {descricao && <p className="regra-rito">{descricao}</p>}
        <div className="rolagem" tabIndex={0} role="region" aria-labelledby={id}>
          <table>
            <thead>
              <tr>
                {colunas.map((c) => (
                  <th key={c} scope="col">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ordenadas.map((l, i) => (
                <tr key={i}>
                  <th scope="row">{nomeDaParcela(l)}</th>
                  <td>
                    {data(l.inicioCorrecao)}
                    {l.inicioJuros == null ? (
                      <span className="nota">sem juros</span>
                    ) : (
                      l.inicioJuros !== l.inicioCorrecao && <span className="nota">juros desde {data(l.inicioJuros)}</span>
                    )}
                  </td>
                  {comSalarioMinimo && <td>{l.pensao ? moeda(l.pensao.salarioMinimo) : '—'}</td>}
                  <td>{moeda(l.pensao ? l.pensao.valorDevido : l.valorOriginal)}</td>
                  {comPagamento && <td>{l.pensao?.valorPago ? moeda(l.pensao.valorPago) : '—'}</td>}
                  <td>{fator(l.fatorCorrecao)}</td>
                  <td>{moeda(l.valorCorrigido)}</td>
                  <td>{percentual(l.percentualJuros)}</td>
                  <td>{moeda(l.valorJuros)}</td>
                  <td>{moeda(l.subtotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="subtotal">
                <th scope="row" colSpan={antesDosTotais}>
                  Subtotal, {contar(ordenadas.length, 'parcela', 'parcelas')}
                </th>
                <td>{moeda(soma(ordenadas, 'valorCorrigido'))}</td>
                <td />
                <td>{moeda(soma(ordenadas, 'valorJuros'))}</td>
                <td>{moeda(soma(ordenadas, 'subtotal'))}</td>
              </tr>
              {rodape}
            </tfoot>
          </table>
        </div>
      </section>
    )
  }

  const linhaDeTotal = (rotulo: string, valor: number, classe?: string) => (
    <tr className={classe}>
      <th scope="row" colSpan={colunas.length - 1}>
        {rotulo}
      </th>
      <td>{moeda(valor)}</td>
    </tr>
  )

  const regras = regrasDoPeriodo(resultado.regime.regras, linhas, resultado.dataBase)
  const avulsas = pedido.parcelas.length

  return (
    <section className="demonstrativo" aria-labelledby="titulo-demonstrativo">
      <div className="cabecalho-resultado">
        <h2 id="titulo-demonstrativo" ref={titulo} tabIndex={-1}>
          Demonstrativo atualizado até {data(resultado.dataBase)}
        </h2>
        <div className="acoes-resultado">
          <button type="button" className="secundario" onClick={copiar}>
            Copiar tabela
          </button>
          <button type="button" className="secundario" onClick={() => window.print()}>
            Imprimir
          </button>
        </div>
        <p className="status" role="status">
          {aviso}
        </p>
      </div>

      <dl className="parametros">
        {pensao && (
          <>
            <div>
              <dt>Pensão</dt>
              <dd>
                {pensao.percentual.replace('.', ',')}% do salário mínimo vigente em cada mês, com vencimento no dia{' '}
                {pensao.diaVencimento}
              </dd>
            </div>
            <div>
              <dt>Parcelas</dt>
              <dd>
                {mes(pensao.primeiroMes)} a {mes(pensao.ultimoMes)}
                {avulsas > 0 && `, mais ${contar(avulsas, 'parcela avulsa', 'parcelas avulsas')}`}
              </dd>
            </div>
            <div>
              <dt>Juros</dt>
              <dd>
                {pensao.inicioJuros === 'CITACAO'
                  ? `Desde a citação (${data(pensao.citacao)}) para as parcelas vencidas antes dela; as seguintes, desde o vencimento`
                  : 'Desde o vencimento de cada parcela'}
              </dd>
            </div>
            <div>
              <dt>Pagamentos parciais</dt>
              <dd>
                {pensao.pagamentos.length === 0
                  ? 'Nenhum'
                  : pensao.pagamentos.map((p) => `${mes(p.mes)}: ${moeda(Number(p.valor))}`).join('; ')}
              </dd>
            </div>
            <div>
              <dt>Ajuizamento</dt>
              <dd>{ritos ? data(ritos.ajuizamento) : 'Ritos não separados'}</dd>
            </div>
          </>
        )}
        <div>
          <dt>Art. 523, §1º</dt>
          <dd>
            {acrescimos > 0
              ? `Multa de 10% e honorários de 10% sobre ${ritos ? 'a penhora' : 'o débito'}`
              : pedido.aplicarArt523
                ? 'Multa e honorários de 10%, sem débito na penhora'
                : 'Não aplicados'}
          </dd>
        </div>
        <div className="largo">
          <dt>Correção e juros</dt>
          <dd>
            {resultado.regime.nome}
            <ul className="regras">
              {regras.map((r, i) => (
                <li key={i}>
                  {descreverPeriodo(r)}: {INDICES[r.correcao]} + {r.descricaoJuros}
                </li>
              ))}
            </ul>
          </dd>
        </div>
      </dl>

      <LinhaDoTempo linhas={linhas} ajuizamento={ritos?.ajuizamento ?? null} />

      {ritos ? (
        <div className="resumo">
          <div className="total-rito prisao">
            <h3>Rito da prisão</h3>
            <p className="valor">{moeda(ritos.prisao.debitoAtualizado)}</p>
            <dl>
              <dt>{contar(ritos.prisao.parcelas, 'parcela', 'parcelas')}, corrigidas</dt>
              <dd>{moeda(ritos.prisao.totalCorrigido)}</dd>
              <dt>Juros</dt>
              <dd>{moeda(ritos.prisao.totalJuros)}</dd>
            </dl>
          </div>
          <div className="total-rito penhora">
            <h3>Rito da penhora</h3>
            <p className="valor">{moeda(ritos.penhora.debitoAtualizado + acrescimos)}</p>
            <dl>
              <dt>{contar(ritos.penhora.parcelas, 'parcela', 'parcelas')}, atualizadas</dt>
              <dd>{moeda(ritos.penhora.debitoAtualizado)}</dd>
              <dt>Multa de 10%</dt>
              <dd>{moeda(resultado.multaArt523)}</dd>
              <dt>Honorários de 10%</dt>
              <dd>{moeda(resultado.honorariosArt523)}</dd>
            </dl>
          </div>
          <div className="total-rito geral">
            <h3>Total da execução</h3>
            <p className="valor">{moeda(resultado.total)}</p>
            <dl>
              <dt>Corrigido</dt>
              <dd>{moeda(resultado.totalCorrigido)}</dd>
              <dt>Juros</dt>
              <dd>{moeda(resultado.totalJuros)}</dd>
              {acrescimos > 0 && (
                <>
                  <dt>Multa e honorários</dt>
                  <dd>{moeda(acrescimos)}</dd>
                </>
              )}
            </dl>
          </div>
        </div>
      ) : (
        <div className="resumo um">
          <div className="total-rito geral">
            <h3>Total da execução</h3>
            <p className="valor">{moeda(resultado.total)}</p>
            <dl>
              <dt>Débito atualizado</dt>
              <dd>{moeda(resultado.debitoAtualizado)}</dd>
              <dt>Multa de 10%</dt>
              <dd>{moeda(resultado.multaArt523)}</dd>
              <dt>Honorários de 10%</dt>
              <dd>{moeda(resultado.honorariosArt523)}</dd>
            </dl>
          </div>
        </div>
      )}

      <h3 className="titulo-memoria">Memória de cálculo</h3>
      <p className="explica-colunas">
        O devido é o percentual do salário mínimo do mês; o pago é abatido antes da correção. O fator acumula a
        correção do vencimento até a data-base, e os juros incidem sobre o saldo corrigido.
      </p>

      {ritos ? (
        <>
          {tabela(
            'tabela-prisao',
            RITOS.PRISAO.titulo,
            RITOS.PRISAO.regra,
            linhas.filter((l) => l.rito === 'PRISAO'),
            null,
          )}
          {tabela(
            'tabela-penhora',
            RITOS.PENHORA.titulo,
            RITOS.PENHORA.regra,
            linhas.filter((l) => l.rito === 'PENHORA'),
            acrescimos > 0 && (
              <>
                {linhaDeTotal('Multa de 10% (art. 523, §1º)', resultado.multaArt523)}
                {linhaDeTotal('Honorários de 10% (art. 523, §1º)', resultado.honorariosArt523)}
                {linhaDeTotal('Total da penhora', ritos.penhora.debitoAtualizado + acrescimos, 'total')}
              </>
            ),
          )}
        </>
      ) : (
        tabela(
          'tabela-parcelas',
          'Parcelas',
          null,
          linhas,
          acrescimos > 0 && (
            <>
              {linhaDeTotal('Multa de 10% (art. 523, §1º)', resultado.multaArt523)}
              {linhaDeTotal('Honorários de 10% (art. 523, §1º)', resultado.honorariosArt523)}
            </>
          ),
        )
      )}

      <p className="total-final">
        <span>Total da execução</span>
        <strong>{moeda(resultado.total)}</strong>
      </p>
    </section>
  )
}
