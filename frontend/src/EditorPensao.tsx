import { useEffect, useId, useRef, useState } from 'react'
import type { InicioJuros, PagamentoRequest } from './api'
import { CampoDecimal } from './CampoDecimal'
import { contar, mes } from './formato'
import { lerPagamentosColados } from './pagamentosColados'
import { erroDoDiaVencimento, type PensaoEditavel } from './pensao'

interface Props {
  pensao: PensaoEditavel
  onPensao: (pensao: PensaoEditavel) => void
}

export function EditorPensao({ pensao, onPensao }: Props) {
  const alterar = (campos: Partial<PensaoEditavel>) => onPensao({ ...pensao, ...campos })
  // Campo de texto, como os outros números do formulário; a faixa de 1 a 31 vem daqui, e não de min e max.
  const campoDia = useRef<HTMLInputElement>(null)
  useEffect(() => {
    campoDia.current?.setCustomValidity(erroDoDiaVencimento(pensao.diaVencimento))
  }, [pensao.diaVencimento])
  const alterarPagamento = (indice: number, campos: Partial<PagamentoRequest>) =>
    alterar({ pagamentos: pensao.pagamentos.map((p, i) => (i === indice ? { ...p, ...campos } : p)) })

  return (
    <div className="editor-pensao">
      <div className="campos quatro">
        <label>
          % do salário mínimo
          <CampoDecimal
            casas={2}
            milhar={false}
            required
            value={pensao.percentual}
            onValor={(v) => alterar({ percentual: v })}
          />
        </label>
        <label>
          Dia do vencimento
          <input
            ref={campoDia}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            maxLength={2}
            required
            aria-describedby="dica-vencimento"
            value={pensao.diaVencimento}
            onChange={(e) => alterar({ diaVencimento: e.target.value })}
          />
        </label>
        <label>
          Primeira parcela
          <input
            type="month"
            required
            value={pensao.primeiroMes}
            onChange={(e) => alterar({ primeiroMes: e.target.value })}
          />
        </label>
        <label>
          Última parcela
          <input
            type="month"
            required
            min={pensao.primeiroMes || undefined}
            value={pensao.ultimoMes}
            onChange={(e) => alterar({ ultimoMes: e.target.value })}
          />
        </label>
      </div>
      <p className="dica" id="dica-vencimento">
        Cada parcela vale o percentual do salário mínimo vigente no mês do vencimento. Em mês sem o dia escolhido (31
        em fevereiro, por exemplo), a parcela vence no último dia.
      </p>

      <div className="campos quatro">
        <label className="duplo">
          Juros desde
          <select value={pensao.inicioJuros} onChange={(e) => alterar({ inicioJuros: e.target.value as InicioJuros })}>
            <option value="VENCIMENTO">o vencimento de cada parcela</option>
            <option value="CITACAO">a citação</option>
          </select>
        </label>
        {pensao.inicioJuros === 'CITACAO' && (
          <label>
            Data da citação
            <input type="date" required value={pensao.citacao} onChange={(e) => alterar({ citacao: e.target.value })} />
          </label>
        )}
      </div>
      {pensao.inicioJuros === 'CITACAO' && (
        <p className="dica">
          As parcelas vencidas antes da citação contam juros a partir dela; as seguintes, do próprio vencimento.
        </p>
      )}

      <h3>Pagamentos parciais</h3>
      {pensao.pagamentos.length === 0 && <p className="dica">Nenhum pagamento informado.</p>}
      {pensao.pagamentos.map((p, i) => (
        <fieldset key={i} className="pagamento">
          <legend className="sr-only">Pagamento {i + 1}</legend>
          <label>
            Mês da parcela
            <input
              type="month"
              required
              min={pensao.primeiroMes || undefined}
              max={pensao.ultimoMes || undefined}
              value={p.mes}
              onChange={(e) => alterarPagamento(i, { mes: e.target.value })}
            />
          </label>
          <label>
            Valor pago (R$)
            <CampoDecimal casas={2} required value={p.valor} onValor={(v) => alterarPagamento(i, { valor: v })} />
          </label>
          <button
            type="button"
            className="secundario"
            aria-label={`Remover o pagamento ${p.mes ? `de ${mes(p.mes)}` : i + 1}`}
            onClick={() => alterar({ pagamentos: pensao.pagamentos.filter((_, j) => j !== i) })}
          >
            Remover
          </button>
        </fieldset>
      ))}
      <button
        type="button"
        className="secundario"
        onClick={() => alterar({ pagamentos: [...pensao.pagamentos, { mes: '', valor: '' }] })}
      >
        Adicionar pagamento
      </button>
      <p className="dica">
        O valor pago é abatido da parcela do mesmo mês antes da correção. Um pagamento maior que a parcela não é
        compensado com as outras.
      </p>
      <ColarPagamentos pensao={pensao} onPagamentos={(pagamentos) => alterar({ pagamentos })} />
    </div>
  )
}

interface PropsColar {
  pensao: PensaoEditavel
  onPagamentos: (pagamentos: PagamentoRequest[]) => void
}

/** Pagamentos colados de uma planilha, com o mês e o valor de cada um numa linha. */
function ColarPagamentos({ pensao, onPagamentos }: PropsColar) {
  const [texto, setTexto] = useState('')
  const [erros, setErros] = useState<string[]>([])
  const [incluidos, setIncluidos] = useState(0)
  const id = useId()
  const painel = useRef<HTMLDetailsElement>(null)
  const caixa = useRef<HTMLTextAreaElement>(null)

  // Texto colado e não incluído não vai para o cálculo: o formulário fica inválido até incluí-lo ou
  // apagá-lo, senão o débito sairia sem abater pagamentos que estão na tela.
  useEffect(() => {
    caixa.current?.setCustomValidity(
      texto.trim() ? 'Inclua os pagamentos colados, com "Incluir pagamentos", ou apague o texto antes de calcular.' : '',
    )
  }, [texto])

  function incluir() {
    // Uma linha em branco do "Adicionar pagamento" dá lugar aos colados, em vez de travar o envio.
    const preenchidos = pensao.pagamentos.filter((p) => p.mes || p.valor.trim())
    const colagem = lerPagamentosColados(texto, {
      primeiroMes: pensao.primeiroMes,
      ultimoMes: pensao.ultimoMes,
      jaInformados: preenchidos.map((p) => p.mes),
    })
    if (!colagem.ok) {
      setErros(colagem.erros)
      setIncluidos(0)
      return
    }
    onPagamentos([...preenchidos, ...colagem.pagamentos])
    setTexto('')
    setErros([])
    setIncluidos(colagem.pagamentos.length)
  }

  return (
    <details className="colar-pagamentos" ref={painel}>
      <summary>Colar de uma planilha</summary>
      <label>
        Mês e valor pago, um pagamento por linha
        <textarea
          ref={caixa}
          rows={5}
          spellCheck={false}
          autoComplete="off"
          placeholder={'04/2024\t350,00\n05/2024\t200,00'}
          aria-describedby={`${id}-dica`}
          aria-invalid={erros.length > 0 || undefined}
          aria-errormessage={erros.length > 0 ? `${id}-erros` : undefined}
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value)
            // Os erros eram do texto anterior: com outro texto, as linhas podem nem ser as mesmas.
            setErros([])
            setIncluidos(0)
          }}
          // Com o painel fechado, o navegador não teria onde mostrar por que o envio parou.
          onInvalid={() => {
            if (painel.current) painel.current.open = true
          }}
        />
      </label>
      <p className="dica" id={`${id}-dica`}>
        Selecione na planilha as duas colunas, o mês (04/2024 ou abr/24) e o valor, e cole aqui. Um cabeçalho na
        primeira linha é ignorado. Se uma linha não for lida, nenhum pagamento entra.
      </p>
      {erros.length > 0 && (
        <ul className="erros-colagem" id={`${id}-erros`} role="alert">
          {erros.map((erro) => (
            <li key={erro}>{erro}</li>
          ))}
        </ul>
      )}
      <div className="acoes-colagem">
        <button type="button" className="secundario" disabled={!texto.trim()} onClick={incluir}>
          Incluir pagamentos
        </button>
        <p className="dica" role="status">
          {incluidos > 0 && `${contar(incluidos, 'pagamento incluído', 'pagamentos incluídos')} acima.`}
        </p>
      </div>
    </details>
  )
}
