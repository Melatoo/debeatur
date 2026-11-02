import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { CalculoRequest, Demonstrativo as Resultado, LinhaDemonstrativo, Regra, Rito, SubtotalRito } from './api'
import { contar, data, decimal, fator, mes, moeda, percentual } from './formato'
import { LinhaDoTempo } from './LinhaDoTempo'
import { INDICES, descreverPeriodo, mesAnterior } from './regime'

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

/** Totais de um grupo de linhas, sempre os somados no servidor, em `Dinheiro`. */
type Totais = Pick<SubtotalRito, 'totalCorrigido' | 'totalJuros' | 'debitoAtualizado'>

/** Uma coluna da memória: a mesma definição monta a tabela na tela e a copiada. */
interface Coluna {
  titulo: string
  celula: (l: LinhaDemonstrativo) => ReactNode
  texto: (l: LinhaDemonstrativo) => string
  /** Total do rodapé nesta coluna, se houver. */
  total?: keyof Totais
}

const nomeDaParcela = (l: LinhaDemonstrativo) => {
  if (!l.pensao) return l.descricao
  const quitada = l.pensao.valorPago > 0 && l.valorOriginal === 0
  return `${mes(l.pensao.mes)}${quitada ? ' (quitada)' : ''}`
}

/**
 * Regras do regime que alcançam o período do cálculo: do primeiro termo inicial ao mês anterior
 * à data-base, que é o último mês que o motor corrige e sobre o qual conta juros. Se tudo vence
 * no mês da data-base, mostra a regra vigente nesse mês, em vez de uma lista vazia.
 */
function regrasDoPeriodo(regras: Regra[], linhas: LinhaDemonstrativo[], dataBase: string): Regra[] {
  const inicios = linhas.flatMap((l) => [l.inicioCorrecao, l.inicioJuros ?? l.inicioCorrecao]).map((d) => d.slice(0, 7))
  const primeiro = inicios.sort()[0]
  if (!primeiro) return regras
  const anteriorABase = mesAnterior(dataBase.slice(0, 7))
  const ultimo = primeiro > anteriorABase ? primeiro : anteriorABase
  return regras.filter((r) => r.inicio <= ultimo && (r.fim == null || r.fim >= primeiro))
}

/** Anotação dos juros no vencimento, igual na tela e no texto copiado. */
const notaDosJuros = (l: LinhaDemonstrativo) =>
  l.inicioJuros == null ? 'sem juros' : l.inicioJuros !== l.inicioCorrecao ? `juros desde ${data(l.inicioJuros)}` : null

export function Demonstrativo({ pedido, resultado }: Props) {
  const titulo = useRef<HTMLHeadingElement>(null)
  const [aviso, setAviso] = useState('')
  const { linhas, ritos } = resultado
  const pensao = pedido.pensao
  const comSalarioMinimo = linhas.some((l) => l.pensao)
  // o valor abatido pelo servidor, o mesmo da coluna "Pago", e não o digitado no pedido
  const pagos = linhas
    .flatMap((l) => (l.pensao && l.pensao.valorPago > 0 ? [l.pensao] : []))
    .sort((a, b) => a.mes.localeCompare(b.mes))
  const comPagamento = pagos.length > 0
  const comAcrescimos = resultado.multaArt523 > 0 || resultado.honorariosArt523 > 0

  useEffect(() => {
    // Leva quem calculou até o resultado, que fica abaixo do formulário.
    const reduzir = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    titulo.current?.scrollIntoView({ behavior: reduzir ? 'auto' : 'smooth', block: 'start' })
    titulo.current?.focus({ preventScroll: true })
  }, [resultado])

  const colunas: Coluna[] = [
    { titulo: 'Parcela', celula: nomeDaParcela, texto: nomeDaParcela },
    {
      titulo: 'Vencimento',
      celula: (l) => {
        const nota = notaDosJuros(l)
        return (
          <>
            {data(l.inicioCorrecao)}
            {nota && <span className="nota">{nota}</span>}
          </>
        )
      },
      texto: (l) => {
        const nota = notaDosJuros(l)
        return nota ? `${data(l.inicioCorrecao)} (${nota})` : data(l.inicioCorrecao)
      },
    },
    ...(comSalarioMinimo
      ? [
          {
            titulo: 'Salário mínimo',
            celula: (l: LinhaDemonstrativo) => (l.pensao ? moeda(l.pensao.salarioMinimo) : '—'),
            texto: (l: LinhaDemonstrativo) => (l.pensao ? decimal(l.pensao.salarioMinimo) : ''),
          },
        ]
      : []),
    {
      titulo: 'Devido',
      celula: (l) => moeda(l.pensao ? l.pensao.valorDevido : l.valorOriginal),
      texto: (l) => decimal(l.pensao ? l.pensao.valorDevido : l.valorOriginal),
    },
    ...(comPagamento
      ? [
          {
            titulo: 'Pago',
            celula: (l: LinhaDemonstrativo) => (l.pensao?.valorPago ? moeda(l.pensao.valorPago) : '—'),
            texto: (l: LinhaDemonstrativo) => (l.pensao?.valorPago ? decimal(l.pensao.valorPago) : ''),
          },
        ]
      : []),
    { titulo: 'Fator', celula: (l) => fator(l.fatorCorrecao), texto: (l) => fator(l.fatorCorrecao) },
    {
      titulo: 'Saldo corrigido',
      celula: (l) => moeda(l.valorCorrigido),
      texto: (l) => decimal(l.valorCorrigido),
      total: 'totalCorrigido',
    },
    { titulo: 'Juros (%)', celula: (l) => percentual(l.percentualJuros), texto: (l) => percentual(l.percentualJuros) },
    { titulo: 'Juros', celula: (l) => moeda(l.valorJuros), texto: (l) => decimal(l.valorJuros), total: 'totalJuros' },
    {
      titulo: 'Atualizado',
      celula: (l) => moeda(l.subtotal),
      texto: (l) => decimal(l.subtotal),
      total: 'debitoAtualizado',
    },
  ]
  const primeiraComTotal = colunas.findIndex((c) => c.total)

  async function copiar() {
    const cabecalho = [...(ritos ? ['Rito'] : []), ...colunas.map((c) => c.titulo)]
    const ordenadas = [...linhas].sort(porVencimento)
    const corpo = ordenadas.map((l) => [
      ...(ritos ? [l.rito ? RITOS[l.rito].titulo : ''] : []),
      ...colunas.map((c) => c.texto(l)),
    ])
    const totais = [
      ...(ritos
        ? [
            ['Rito da prisão', decimal(ritos.prisao.debitoAtualizado)],
            ['Rito da penhora', decimal(ritos.penhora.debitoAtualizado)],
          ]
        : []),
      ['Débito atualizado', decimal(resultado.debitoAtualizado)],
      ...(comAcrescimos
        ? [
            ['Multa (art. 523, §1º)', decimal(resultado.multaArt523)],
            ['Honorários (art. 523, §1º)', decimal(resultado.honorariosArt523)],
            ...(ritos ? [['Total da penhora', decimal(ritos.totalPenhora)]] : []),
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

  function tabela(
    id: string,
    rotulo: ReactNode,
    descricao: string | null,
    das: LinhaDemonstrativo[],
    totais: Totais,
    rodape: ReactNode,
  ) {
    const ordenadas = [...das].sort(porVencimento)
    return (
      <section className="memoria" aria-labelledby={id}>
        <h3 id={id}>{rotulo}</h3>
        {descricao && <p className="regra-rito">{descricao}</p>}
        <div className="rolagem" tabIndex={0} role="region" aria-labelledby={id}>
          <table>
            <thead>
              <tr>
                {colunas.map((c) => (
                  <th key={c.titulo} scope="col">
                    {c.titulo}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ordenadas.map((l, i) => (
                <tr key={i}>
                  {colunas.map((c, j) =>
                    j === 0 ? (
                      <th key={c.titulo} scope="row">
                        {c.celula(l)}
                      </th>
                    ) : (
                      <td key={c.titulo}>{c.celula(l)}</td>
                    ),
                  )}
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="subtotal">
                <th scope="row" colSpan={primeiraComTotal}>
                  Subtotal, {contar(ordenadas.length, 'parcela', 'parcelas')}
                </th>
                {colunas.slice(primeiraComTotal).map((c) => (
                  <td key={c.titulo}>{c.total && moeda(totais[c.total])}</td>
                ))}
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
                {pagos.length === 0
                  ? 'Nenhum'
                  : pagos.map((p) => `${mes(p.mes)}: ${moeda(p.valorPago)}`).join('; ')}
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
            {comAcrescimos
              ? `Multa de 10% e honorários de 10% sobre ${ritos ? 'a penhora' : 'o débito'}`
              : pedido.aplicarArt523
                ? `Multa e honorários de 10%, sem débito ${ritos ? 'na penhora' : 'a acrescer'}`
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
            <p className="valor">{moeda(ritos.totalPenhora)}</p>
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
              {comAcrescimos && (
                <>
                  <dt>Multa de 10%</dt>
                  <dd>{moeda(resultado.multaArt523)}</dd>
                  <dt>Honorários de 10%</dt>
                  <dd>{moeda(resultado.honorariosArt523)}</dd>
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
              {comAcrescimos && (
                <>
                  <dt>Multa de 10%</dt>
                  <dd>{moeda(resultado.multaArt523)}</dd>
                  <dt>Honorários de 10%</dt>
                  <dd>{moeda(resultado.honorariosArt523)}</dd>
                </>
              )}
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
            ritos.prisao,
            null,
          )}
          {tabela(
            'tabela-penhora',
            RITOS.PENHORA.titulo,
            RITOS.PENHORA.regra,
            linhas.filter((l) => l.rito === 'PENHORA'),
            ritos.penhora,
            comAcrescimos && (
              <>
                {linhaDeTotal('Multa de 10% (art. 523, §1º)', resultado.multaArt523)}
                {linhaDeTotal('Honorários de 10% (art. 523, §1º)', resultado.honorariosArt523)}
                {linhaDeTotal('Total da penhora', ritos.totalPenhora, 'total')}
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
          resultado,
          comAcrescimos && (
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
