import { useContext, useEffect, useId, useRef, useState } from 'react'
import type { InicioJuros, PagamentoRequest } from './api'
import { Campo, MensagemErro } from './Campo'
import { TentouCalcular } from './validacao'
import { CampoDecimal } from './CampoDecimal'
import { contar, mes } from './formato'
import { lerPagamentosColados } from './pagamentosColados'
import { erroDoDiaVencimento, type PensaoEditavel } from './pensao'
import { Dica } from './Dica'

interface Props {
  pensao: PensaoEditavel
  onPensao: (pensao: PensaoEditavel) => void
}

export function EditorPensao({ pensao, onPensao }: Props) {
  const alterar = (campos: Partial<PensaoEditavel>) => onPensao({ ...pensao, ...campos })
  const alterarPagamento = (indice: number, campos: Partial<PagamentoRequest>) =>
    alterar({ pagamentos: pensao.pagamentos.map((p, i) => (i === indice ? { ...p, ...campos } : p)) })

  return (
    <div className="editor-pensao">
      <div className="campos quatro">
        <Campo rotulo="% do salário mínimo" seVazio="Informe o percentual da sentença (ex.: 30 ou 33,33).">
          <CampoDecimal
            casas={2}
            milhar={false}
            required
            value={pensao.percentual}
            onValor={(v) => alterar({ percentual: v })}
          />
        </Campo>
        {/* Campo de texto, como os outros números do formulário; a faixa de 1 a 31 vem do erroProprio, e não de min e max. */}
        <Campo
          rotulo="Dia do vencimento"
          seVazio="Informe o dia do vencimento (ex.: 10)."
          erroProprio={erroDoDiaVencimento(pensao.diaVencimento)}
        >
          <input
            type="text"
            inputMode="numeric"
            autoComplete="off"
            maxLength={2}
            required
            aria-describedby="dica-vencimento"
            value={pensao.diaVencimento}
            onChange={(e) => alterar({ diaVencimento: e.target.value })}
          />
        </Campo>
        <Campo rotulo="Primeira parcela">
          <input
            type="month"
            required
            value={pensao.primeiroMes}
            onChange={(e) => alterar({ primeiroMes: e.target.value })}
          />
        </Campo>
        <Campo rotulo="Última parcela">
          <input
            type="month"
            required
            min={pensao.primeiroMes || undefined}
            value={pensao.ultimoMes}
            onChange={(e) => alterar({ ultimoMes: e.target.value })}
          />
        </Campo>
      </div>
      <Dica id="dica-vencimento" curta="Cada parcela vale o percentual do salário mínimo do mês do vencimento.">
        Em mês sem o dia escolhido (31 em fevereiro, por exemplo), a parcela vence no último dia.
      </Dica>

      <div className="campos quatro">
        <label className="duplo">
          Juros desde
          <select value={pensao.inicioJuros} onChange={(e) => alterar({ inicioJuros: e.target.value as InicioJuros })}>
            <option value="VENCIMENTO">o vencimento de cada parcela</option>
            <option value="CITACAO">a citação</option>
          </select>
        </label>
        {pensao.inicioJuros === 'CITACAO' && (
          <Campo rotulo="Data da citação">
            <input type="date" required value={pensao.citacao} onChange={(e) => alterar({ citacao: e.target.value })} />
          </Campo>
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
          <Campo rotulo="Mês da parcela">
            <input
              type="month"
              required
              min={pensao.primeiroMes || undefined}
              max={pensao.ultimoMes || undefined}
              value={p.mes}
              onChange={(e) => alterarPagamento(i, { mes: e.target.value })}
            />
          </Campo>
          <Campo rotulo="Valor pago (R$)" seVazio="Informe o valor pago (ex.: 350,00).">
            <CampoDecimal casas={2} required value={p.valor} onValor={(v) => alterarPagamento(i, { valor: v })} />
          </Campo>
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
      {/* Sem "Saiba mais": o que não é compensado muda o valor e fica à vista. */}
      <p className="dica">
        O valor pago é abatido da parcela do mesmo mês, antes da correção; o que passar da parcela não é compensado com
        as outras.
      </p>
      <ColarPagamentos pensao={pensao} onPagamentos={(pagamentos) => alterar({ pagamentos })} />
    </div>
  )
}

interface PropsColar {
  pensao: PensaoEditavel
  onPagamentos: (pagamentos: PagamentoRequest[]) => void
}

const PENDENTE = 'Inclua os pagamentos colados, com "Incluir pagamentos", ou apague o texto antes de calcular.'

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
    caixa.current?.setCustomValidity(texto.trim() ? PENDENTE : '')
  }, [texto])
  // Depois de Calcular, o texto esquecido na caixa diz por que o cálculo não saiu. Com os erros de
  // uma tentativa de incluir à vista, eles já dizem o que corrigir.
  const pendente = useContext(TentouCalcular) && texto.trim() !== '' && erros.length === 0

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
        {/* O resumo de erros lê o nome do campo daqui. */}
        <span className="rotulo">Mês e valor pago, um pagamento por linha</span>
        {pendente && <MensagemErro id={`${id}-pendente`}>{PENDENTE}</MensagemErro>}
        <textarea
          ref={caixa}
          id={`${id}-caixa`}
          rows={5}
          spellCheck={false}
          autoComplete="off"
          placeholder={'04/2024\t350,00\n05/2024\t200,00'}
          aria-describedby={pendente ? `${id}-pendente ${id}-dica` : `${id}-dica`}
          aria-invalid={erros.length > 0 || pendente || undefined}
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
      <Dica id={`${id}-dica`} curta="Copie da planilha as colunas do mês e do valor e cole aqui.">
        O mês pode vir como 04/2024 ou abr/24. Um cabeçalho na primeira linha é ignorado. Se uma linha não for lida,
        nenhum pagamento entra.
      </Dica>
      {/* O role="alert" fica em volta: no próprio <ul>, ele tiraria do leitor de tela a semântica de lista. */}
      {erros.length > 0 && (
        <div id={`${id}-erros`} role="alert">
          <ul className="erros-colagem">
            {erros.map((erro) => (
              <li key={erro}>{erro}</li>
            ))}
          </ul>
        </div>
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
