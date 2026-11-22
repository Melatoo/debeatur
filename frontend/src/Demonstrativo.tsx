import { Fragment, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import {
  ErroDaApi,
  O_ARQUIVO,
  gerarArquivo,
  gerarArquivoSalvo,
  type CalculoRequest,
  type FormatoDoArquivo,
  type Demonstrativo as Resultado,
  type LinhaDemonstrativo,
  type ProcessoRequest,
  type Rito,
  type SubtotalRito,
} from './api'
import { lerColunasOcultas, salvarColunasOcultas, type ColunaOpcional } from './colunas'
import { contar, data, decimal, fator, mes, moeda, percentual } from './formato'
import { competenciasQuitadas, parcelasAntigas, textoDoAviso } from './prescricao'
import { chaveDoPedido } from './recalculo'
import { FatorMesAMes } from './FatorMesAMes'
import { LinhaDoTempo } from './LinhaDoTempo'
import { useAlturaFixa } from './alturaFixa'
import { descreverPensao } from './pensao'
import { armazenamentoDoNavegador } from './armazenamento'
import { INDICES, descreverPeriodo, regrasDoPeriodo } from './regime'

interface Props {
  /** O pedido que gerou o resultado: o demonstrativo descreve o que foi calculado, não o formulário atual. */
  pedido: CalculoRequest
  resultado: Resultado
  /**
   * O processo como está no formulário agora, e não no último cálculo: ele só identifica o PDF,
   * então quem o preenche depois de calcular não precisa calcular de novo.
   */
  processo?: ProcessoRequest
  /** Se o demonstrativo bate com o formulário, está sendo recalculado ou ficou para trás. */
  situacao: Situacao
  /** Por que ficou desatualizado, quando não dá para recalcular. */
  motivo: string | null
  /** O que a faixa de desatualizado oferece para sair dele: "Tentar de novo", "Usar a data-base…". */
  acao?: AcaoDaSituacao
  /**
   * Id do cálculo salvo que está na tela, sem nenhuma alteração desde que foi aberto ou salvo. O PDF
   * sai dele, com o snapshot das séries, e não de um cálculo refeito com as séries de hoje.
   */
  idSalvo?: string
  /** Abre a janela de salvar; sem ele, o botão nem aparece. */
  aoSalvar?: () => void
  /** O servidor não acha mais o cálculo salvo (excluído em outra aba ou por outra pessoa). */
  aoSalvoSumir?: (id: string) => void
}

export type Situacao = 'atual' | 'atualizando' | 'desatualizado'

export interface AcaoDaSituacao {
  rotulo: string
  aoClicar: () => void
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

/** A regra da prisão quando o cálculo segue a planilha da DPMG, igual à do PDF. */
const PRISAO_PELA_DPMG = 'As três últimas parcelas do cálculo, como a planilha da DPMG.'

/** O que o aviso diz de cada arquivo, com o gênero certo: "o PDF … gerado", "a planilha … gerada". */
const TEXTOS_DO_ARQUIVO: Record<FormatoDoArquivo, { gerando: string; baixado: string; mudou: string }> = {
  pdf: { gerando: 'Gerando PDF…', baixado: 'PDF baixado', mudou: 'o PDF era gerado' },
  xlsx: { gerando: 'Gerando planilha…', baixado: 'Planilha baixada', mudou: 'a planilha era gerada' },
}

const porVencimento = (a: LinhaDemonstrativo, b: LinhaDemonstrativo) => a.inicioCorrecao.localeCompare(b.inicioCorrecao)

/** Totais de um grupo de linhas, sempre os somados no servidor, em `Dinheiro`. */
type Totais = Pick<SubtotalRito, 'totalCorrigido' | 'totalJuros' | 'debitoAtualizado'>

/** Uma coluna da memória: a mesma definição monta a tabela na tela e a copiada. */
interface Coluna {
  /** Nulo nas colunas que não se escondem: a Parcela identifica a linha, e o Atualizado é o resultado dela. */
  id: ColunaOpcional | null
  titulo: string
  celula: (l: LinhaDemonstrativo) => ReactNode
  texto: (l: LinhaDemonstrativo) => string
  /** Total do rodapé nesta coluna, se houver. */
  total?: keyof Totais
}

const nomeDaParcela = (l: LinhaDemonstrativo) => {
  if (!l.pensao) return l.descricao
  const quitada = l.pensao.valorPago > 0 && l.valorOriginal === 0
  return `${mes(l.pensao.mes)}${l.pensao.decimoTerceiro ? ' e 13º' : ''}${quitada ? ' (quitada)' : ''}`
}

/** Anotação dos juros no vencimento, igual na tela e no texto copiado. */
const notaDosJuros = (l: LinhaDemonstrativo) =>
  l.inicioJuros == null ? 'sem juros' : l.inicioJuros !== l.inicioCorrecao ? `juros desde ${data(l.inicioJuros)}` : null

export function Demonstrativo({ pedido, resultado, processo, situacao, motivo, acao, idSalvo, aoSalvar, aoSalvoSumir }: Props) {
  const titulo = useRef<HTMLHeadingElement>(null)
  const faixa = useRef<HTMLParagraphElement>(null)
  useAlturaFixa(faixa, '--altura-situacao')
  /**
   * Aviso de PDF ou cópia, preso ao pedido de que fala: o recálculo automático troca os números sem
   * remontar, e "PDF baixado" não pode ficar ao lado de números que não estão nele.
   */
  const [aviso, setAviso] = useState<{ de: CalculoRequest | string; texto: string } | null>(null)
  const avisar = (texto: string) => setAviso({ de: pedido, texto })
  /**
   * Um aviso que não fala dos números, preso à chave do pedido: ele continua depois do recálculo
   * do mesmo pedido (que traz outro objeto) e some quando a pessoa muda o formulário.
   */
  const avisarSobreOPedido = (texto: string) => setAviso({ de: chaveDoPedido(pedido), texto })
  const avisoNaTela =
    aviso && (aviso.de === pedido || (typeof aviso.de === 'string' && aviso.de === chaveDoPedido(pedido)))
      ? aviso.texto
      : null
  /** O pedido na tela agora, para um PDF que fica pronto depois de um recálculo não baixar o antigo. */
  const pedidoNaTela = useRef(pedido)
  useEffect(() => {
    pedidoNaTela.current = pedido
  }, [pedido])
  /** O arquivo sendo gerado; um de cada vez. */
  const [gerando, setGerando] = useState<FormatoDoArquivo | null>(null)
  /** A linha cujo fator está aberto na janela da série mês a mês. */
  const [fatorAberto, setFatorAberto] = useState<{ de: Resultado; linha: LinhaDemonstrativo } | null>(null)
  // O recálculo automático troca o resultado sem remontar: a janela fecha, em vez de misturar os dois.
  const fatorDe = fatorAberto?.de === resultado ? fatorAberto.linha : null
  const { linhas, ritos } = resultado
  const pensao = pedido.pensao
  const antigas =
    pensao && parcelasAntigas(pensao, pensao.ajuizamento, pedido.dataBase, competenciasQuitadas(resultado.linhas))
  const avisoPrescricao = antigas && textoDoAviso(antigas)
  const comSalarioMinimo = linhas.some((l) => l.pensao?.salarioMinimo != null)
  // o valor abatido pelo servidor, o mesmo da coluna "Pago", e não o digitado no pedido
  const pagos = linhas
    .flatMap((l) => (l.pensao && l.pensao.valorPago > 0 ? [l.pensao] : []))
    .sort((a, b) => a.mes.localeCompare(b.mes))
  const comPagamento = pagos.length > 0
  const comAcrescimos = resultado.multaArt523 > 0 || resultado.honorariosArt523 > 0
  const honorariosSobreAMulta = pedido.baseHonorarios === 'DEBITO_COM_MULTA'
  const rotuloHonorarios = honorariosSobreAMulta
    ? 'Honorários de 10%, com a multa na base (art. 523, §1º)'
    : 'Honorários de 10% (art. 523, §1º)'
  // Respostas de antes dos abatimentos não trazem estes campos.
  const abatimentos = resultado.abatimentos ?? []
  const totalAbatido = resultado.totalAbatido ?? 0
  const baseDoArt523 =
    totalAbatido > 0 ? `o saldo, já abatido, ${ritos ? 'da penhora' : 'do débito'}` : ritos ? 'a penhora' : 'o débito'
  const abatidoPenhora = ritos?.abatidoPenhora ?? 0
  const abatidoPrisao = ritos?.abatidoPrisao ?? 0
  const totalPrisao = ritos ? (ritos.totalPrisao ?? ritos.prisao.debitoAtualizado) : 0
  const nomeDoAbatimento = (a: (typeof abatimentos)[number]) =>
    `Abatimento de ${data(a.data)}${a.origem ? ` (${a.origem})` : ''}`

  useEffect(() => {
    // Leva quem calculou até o resultado, que na tela estreita fica abaixo do formulário. Só ao montar:
    // o clique em Calcular troca a `key` e remonta; o recálculo automático mantém a tela onde a pessoa
    // está editando. Em duas colunas, o título costuma já estar à vista, ao lado do formulário.
    const elemento = titulo.current
    if (!elemento) return
    const { top } = elemento.getBoundingClientRect()
    if (top < 0 || top > window.innerHeight * 0.5) {
      const reduzir = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      elemento.scrollIntoView({ behavior: reduzir ? 'auto' : 'smooth', block: 'start' })
    }
    elemento.focus({ preventScroll: true })
  }, [])

  // Um demonstrativo que não bate com o formulário não sai da tela: nem PDF, nem cópia, nem impressão.
  const travado = situacao !== 'atual'

  /** Preferência do navegador, e não do cálculo: sobrevive ao clique em Calcular, que remonta o demonstrativo. */
  const [ocultas, setOcultas] = useState<ColunaOpcional[]>(() => lerColunasOcultas(armazenamentoDoNavegador()))
  function mostrarColuna(id: ColunaOpcional, mostrar: boolean) {
    const novas = mostrar ? ocultas.filter((c) => c !== id) : [...ocultas, id]
    setOcultas(novas)
    salvarColunasOcultas(armazenamentoDoNavegador(), novas)
  }

  const todas: Coluna[] = [
    { id: null, titulo: 'Parcela', celula: nomeDaParcela, texto: nomeDaParcela },
    {
      id: 'vencimento',
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
            id: 'salarioMinimo' as const,
            titulo: 'Salário mínimo',
            celula: (l: LinhaDemonstrativo) => (l.pensao?.salarioMinimo != null ? decimal(l.pensao.salarioMinimo) : '—'),
            texto: (l: LinhaDemonstrativo) => (l.pensao?.salarioMinimo != null ? decimal(l.pensao.salarioMinimo) : ''),
          },
        ]
      : []),
    {
      id: 'devido',
      titulo: 'Devido',
      celula: (l) => decimal(l.pensao ? l.pensao.valorDevido : l.valorOriginal),
      texto: (l) => decimal(l.pensao ? l.pensao.valorDevido : l.valorOriginal),
    },
    ...(comPagamento
      ? [
          {
            id: 'pago' as const,
            titulo: 'Pago',
            celula: (l: LinhaDemonstrativo) => (l.pensao?.valorPago ? decimal(l.pensao.valorPago) : '—'),
            texto: (l: LinhaDemonstrativo) => (l.pensao?.valorPago ? decimal(l.pensao.valorPago) : ''),
          },
        ]
      : []),
    {
      id: 'fator',
      titulo: 'Fator',
      // Na tela, o fator abre a série mês a mês; impresso, é só o número.
      celula: (l) => (
        <button type="button" className="ver-fator" aria-haspopup="dialog" onClick={() => setFatorAberto({ de: resultado, linha: l })}>
          {fator(l.fatorCorrecao)}
          <span className="sr-only">, ver a série mês a mês de {nomeDaParcela(l)}</span>
        </button>
      ),
      texto: (l) => fator(l.fatorCorrecao),
    },
    {
      id: 'corrigido',
      titulo: 'Saldo corrigido',
      celula: (l) => decimal(l.valorCorrigido),
      texto: (l) => decimal(l.valorCorrigido),
      total: 'totalCorrigido',
    },
    {
      id: 'percentualJuros',
      titulo: 'Juros (%)',
      celula: (l) => percentual(l.percentualJuros),
      texto: (l) => percentual(l.percentualJuros),
    },
    {
      id: 'juros',
      titulo: 'Juros',
      celula: (l) => decimal(l.valorJuros),
      texto: (l) => decimal(l.valorJuros),
      total: 'totalJuros',
    },
    {
      id: null,
      titulo: 'Atualizado',
      celula: (l) => decimal(l.subtotal),
      texto: (l) => decimal(l.subtotal),
      total: 'debitoAtualizado',
    },
  ]
  const visivel = (c: Coluna) => c.id === null || !ocultas.includes(c.id)
  /** As colunas na tela, que são também as da cópia e da impressão; o PDF sai sempre com todas. */
  const colunas = todas.filter(visivel)
  // O Atualizado nunca se esconde, então sempre há uma coluna com total, e o rótulo do subtotal cabe antes dela.
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
      ...abatimentos.map((a) => [nomeDoAbatimento(a), decimal(-a.valor)]),
      ...(totalAbatido > 0 ? [['Saldo', decimal(resultado.debitoAtualizado - totalAbatido)]] : []),
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
      avisar('Tabela copiada. Cole no Word ou numa planilha.')
    } catch {
      avisar('O navegador não deixou copiar. Use Imprimir ou selecione a tabela com o mouse.')
    }
  }

  /**
   * Refaz o cálculo do pedido no servidor, que monta o PDF com os mesmos critérios e índices da
   * tela, com as séries do cache (salvo revisão de alguma série no SGS entre um e outro). O PDF de
   * um cálculo salvo e intacto vem do snapshot, como os números que ele mostra. A planilha sai
   * do mesmo jeito, com os mesmos dados.
   */
  async function baixar(formato: FormatoDoArquivo) {
    const textos = TEXTOS_DO_ARQUIVO[formato]
    setGerando(formato)
    avisar(textos.gerando)
    try {
      const { conteudo, nome } = await (idSalvo
        ? gerarArquivoSalvo(idSalvo, formato)
        : gerarArquivo({ ...pedido, processo }, formato))
      if (pedidoNaTela.current !== pedido) {
        setAviso({ de: pedidoNaTela.current, texto: `O demonstrativo mudou enquanto ${textos.mudou}. Baixe de novo.` })
        return
      }
      const url = URL.createObjectURL(conteudo)
      const link = document.createElement('a')
      link.href = url
      link.download = nome
      link.click()
      // o navegador já copiou o arquivo ao iniciar o download; liberar na hora cancela no Firefox
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
      avisar(`${textos.baixado}: ${nome}`)
    } catch (e) {
      if (idSalvo && e instanceof ErroDaApi && e.status === 404) {
        // Excluído em outra aba ou por outra pessoa: o snapshot não existe mais, e a tela deixa de
        // tratá-lo como salvo, como na exclusão feita aqui. O recálculo que vem a seguir troca o
        // objeto do pedido, e o aviso precisa continuar na tela.
        aoSalvoSumir?.(idSalvo)
        avisarSobreOPedido(
          `Este cálculo salvo foi excluído em outra janela. Os números são refeitos com os índices de hoje; baixe ${O_ARQUIVO[formato]} de novo.`,
        )
        return
      }
      avisar(`Não foi possível gerar ${O_ARQUIVO[formato]}. ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setGerando(null)
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
          <table style={{ '--colunas': colunas.length } as CSSProperties}>
            {/* O título já está no h3 acima; na tela, o caption só diz a unidade que as células não repetem. */}
            <caption>
              <span className="sr-only">{rotulo}. </span>Valores em R$
            </caption>
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
                  <td key={c.titulo}>{c.total && decimal(totais[c.total])}</td>
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
      <td>{decimal(valor)}</td>
    </tr>
  )

  const regras = regrasDoPeriodo(resultado.regime.regras, linhas, resultado.dataBase)
  const avulsas = pedido.parcelas.length

  return (
    <section
      className="demonstrativo"
      data-situacao={situacao}
      aria-labelledby="titulo-demonstrativo"
    >
      {/* Sem aria-busy na seção: dentro de uma região ocupada, o leitor de tela cala esta faixa. */}
      <p ref={faixa} className="situacao" role="status">
        {situacao === 'atualizando' && 'Atualizando o demonstrativo…'}
        {situacao === 'desatualizado' && (
          <>
            <strong>Demonstrativo desatualizado.</strong> {motivo}
            {acao && (
              <>
                {' '}
                <button type="button" className="secundario" onClick={acao.aoClicar}>
                  {acao.rotulo}
                </button>
              </>
            )}
          </>
        )}
      </p>
      <div className="cabecalho-resultado">
        <h2 id="titulo-demonstrativo" ref={titulo} tabIndex={-1}>
          Demonstrativo atualizado até {data(resultado.dataBase)}
        </h2>
        <div className="acoes-resultado">
          <button
            type="button"
            className={gerando === 'pdf' ? 'esperando' : undefined}
            onClick={() => baixar('pdf')}
            disabled={gerando !== null || travado}
          >
            {gerando === 'pdf' ? TEXTOS_DO_ARQUIVO.pdf.gerando : 'Baixar PDF'}
          </button>
          <button
            type="button"
            className={gerando === 'xlsx' ? 'secundario esperando' : 'secundario'}
            onClick={() => baixar('xlsx')}
            disabled={gerando !== null || travado}
          >
            {gerando === 'xlsx' ? TEXTOS_DO_ARQUIVO.xlsx.gerando : 'Baixar Excel'}
          </button>
          {aoSalvar && (
            // Salvo e intacto, salvar de novo só faria uma cópia, com os índices de hoje no lugar dos dele.
            <button type="button" className="secundario" onClick={aoSalvar} disabled={travado || idSalvo !== undefined}>
              {idSalvo ? 'Salvo' : 'Salvar'}
            </button>
          )}
          <button type="button" className="secundario" onClick={copiar} disabled={travado}>
            Copiar tabela
          </button>
          <button type="button" className="secundario" onClick={() => window.print()} disabled={travado}>
            Imprimir
          </button>
        </div>
        <p className="status" role="status">
          {avisoNaTela}
        </p>
      </div>

      {/*
        O total à vista sem rolar, como no quadro-resumo da primeira página do PDF. Fora do cabeçalho,
        que não se apaga: desatualizado, o total se apaga com os outros números.
      */}
      <p className="total-topo">
        <span className="rotulo">Total da execução</span> <strong>{moeda(resultado.total)}</strong>
        {ritos && (
          <span className="composicao">
            <span className="rito-prisao">Prisão {moeda(totalPrisao)}</span>
            <span className="rito-penhora">
              Penhora {moeda(ritos.totalPenhora)}
              {comAcrescimos && ' com multa e honorários'}
              {abatidoPenhora > 0 && (comAcrescimos ? ', já abatida' : ' já abatida')}
            </span>
          </span>
        )}
      </p>

      <dl className="parametros">
        {pensao && (
          <>
            <div>
              <dt>Pensão</dt>
              <dd>
                {descreverPensao(pensao)}, com vencimento no dia {pensao.diaVencimento}
              </dd>
            </div>
            <div>
              <dt>13º salário</dt>
              <dd>{pensao.decimoTerceiro ? 'Incluído: a parcela de dezembro vale o dobro' : 'Não incluído'}</dd>
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
              <dt>Abatimentos</dt>
              <dd>
                {abatimentos.length === 0
                  ? 'Nenhum'
                  : `Pelo valor nominal, sem correção, ${ritos ? 'do débito da penhora' : 'do débito atualizado'}: ${abatimentos
                      .map((a) => `${data(a.data)}${a.origem ? ` (${a.origem})` : ''}: ${moeda(a.valor)}`)
                      .join('; ')}`}
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
              ? honorariosSobreAMulta
                ? `Multa de 10% sobre ${baseDoArt523} e honorários de 10% sobre esse valor com a multa, como o CADEJ`
                : `Multa de 10% e honorários de 10% sobre ${baseDoArt523}`
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

      {/* Só na tela: no papel que vai aos autos, o aviso trabalharia contra quem o junta. */}
      {avisoPrescricao && (
        <p className="aviso aviso-prescricao">
          <strong>{avisoPrescricao.fato}</strong> {avisoPrescricao.detalhe}
        </p>
      )}

      <LinhaDoTempo linhas={linhas} ajuizamento={ritos?.ajuizamento ?? null} />

      {ritos ? (
        <div className="resumo">
          <div className="total-rito prisao">
            <h3>Rito da prisão</h3>
            <p className="valor">{moeda(totalPrisao)}</p>
            <dl>
              <dt>{contar(ritos.prisao.parcelas, 'parcela', 'parcelas')}, corrigidas</dt>
              <dd>{moeda(ritos.prisao.totalCorrigido)}</dd>
              <dt>Juros</dt>
              <dd>{moeda(ritos.prisao.totalJuros)}</dd>
              {abatidoPrisao > 0 && (
                <>
                  <dt>Abatimentos que passaram da penhora</dt>
                  <dd>{moeda(-abatidoPrisao)}</dd>
                </>
              )}
            </dl>
          </div>
          <div className="total-rito penhora">
            <h3>Rito da penhora</h3>
            <p className="valor">{moeda(ritos.totalPenhora)}</p>
            <dl>
              <dt>{contar(ritos.penhora.parcelas, 'parcela', 'parcelas')}, atualizadas</dt>
              <dd>{moeda(ritos.penhora.debitoAtualizado)}</dd>
              {abatidoPenhora > 0 && (
                <>
                  <dt>Abatimentos</dt>
                  <dd>{moeda(-abatidoPenhora)}</dd>
                </>
              )}
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
              {totalAbatido > 0 && (
                <>
                  <dt>Abatimentos</dt>
                  <dd>{moeda(-totalAbatido)}</dd>
                </>
              )}
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
              {totalAbatido > 0 && (
                <>
                  <dt>Abatimentos</dt>
                  <dd>{moeda(-totalAbatido)}</dd>
                  <dt>Saldo</dt>
                  <dd>{moeda(resultado.debitoAtualizado - totalAbatido)}</dd>
                </>
              )}
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

      <div className="cabecalho-memoria">
        <h3 className="titulo-memoria">Memória de cálculo</h3>
        <details className="seletor-colunas">
          <summary>
            Colunas ({colunas.length} de {todas.length})
          </summary>
          <fieldset>
            <legend className="sr-only">Colunas exibidas na memória de cálculo</legend>
            <div className="opcoes-colunas">
              {todas.map((c) => (
                <label key={c.titulo} className="checkbox">
                  <input
                    type="checkbox"
                    checked={visivel(c)}
                    disabled={c.id === null}
                    onChange={(e) => c.id && mostrarColuna(c.id, e.target.checked)}
                  />
                  {c.titulo}
                </label>
              ))}
            </div>
            <p className="dica">
              Vale também para a cópia e a impressão. O PDF sai sempre com todas as colunas. Parcela e Atualizado não
              se escondem.
            </p>
          </fieldset>
        </details>
      </div>
      <p className="explica-colunas">
        O devido é o valor da pensão no mês{pensao?.decimoTerceiro ? ', em dobro em dezembro com o 13º' : ''}; o pago é
        abatido antes da correção. O fator acumula a
        correção do vencimento até a data-base, e os juros incidem sobre o saldo corrigido.
      </p>

      {ritos ? (
        <>
          {tabela(
            'tabela-prisao',
            RITOS.PRISAO.titulo,
            pedido.pensao?.regraRitos === 'ULTIMOS_MESES_DO_CALCULO' ? PRISAO_PELA_DPMG : RITOS.PRISAO.regra,
            linhas.filter((l) => l.rito === 'PRISAO'),
            ritos.prisao,
            abatidoPrisao > 0 && (
              <>
                {linhaDeTotal('Abatimentos que passaram do débito da penhora', -abatidoPrisao)}
                {linhaDeTotal('Total da prisão', totalPrisao, 'total')}
              </>
            ),
          )}
          {tabela(
            'tabela-penhora',
            RITOS.PENHORA.titulo,
            RITOS.PENHORA.regra,
            linhas.filter((l) => l.rito === 'PENHORA'),
            ritos.penhora,
            <>
              {abatimentos.map((a, i) => (
                <Fragment key={i}>{linhaDeTotal(nomeDoAbatimento(a), -a.valor)}</Fragment>
              ))}
              {abatidoPrisao > 0 && linhaDeTotal('Parte dos abatimentos que sai da prisão', abatidoPrisao)}
              {abatidoPenhora > 0 && linhaDeTotal('Saldo', ritos.penhora.debitoAtualizado - abatidoPenhora, 'total')}
              {comAcrescimos && (
                <>
                  {linhaDeTotal('Multa de 10% (art. 523, §1º)', resultado.multaArt523)}
                  {linhaDeTotal(rotuloHonorarios, resultado.honorariosArt523)}
                  {linhaDeTotal('Total da penhora', ritos.totalPenhora, 'total')}
                </>
              )}
            </>,
          )}
        </>
      ) : (
        tabela(
          'tabela-parcelas',
          'Parcelas',
          null,
          linhas,
          resultado,
          <>
            {abatimentos.map((a, i) => (
              <Fragment key={i}>{linhaDeTotal(nomeDoAbatimento(a), -a.valor)}</Fragment>
            ))}
            {totalAbatido > 0 && linhaDeTotal('Saldo', resultado.debitoAtualizado - totalAbatido, 'total')}
            {comAcrescimos && (
              <>
                {linhaDeTotal('Multa de 10% (art. 523, §1º)', resultado.multaArt523)}
                {linhaDeTotal(rotuloHonorarios, resultado.honorariosArt523)}
              </>
            )}
          </>,
        )
      )}

      <p className="total-final">
        <span>Total da execução</span>
        <strong>{moeda(resultado.total)}</strong>
      </p>

      <FatorMesAMes
        linha={fatorDe}
        nome={fatorDe ? nomeDaParcela(fatorDe) : ''}
        resultado={resultado}
        aoFechar={() => setFatorAberto(null)}
      />
    </section>
  )
}
