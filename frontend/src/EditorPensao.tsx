import type { InicioJuros, PagamentoRequest } from './api'
import type { PensaoEditavel } from './pensao'

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
      <fieldset className="pensao">
        <label>
          % do salário mínimo
          <input
            type="number"
            step="0.01"
            min="0.01"
            required
            value={pensao.percentual}
            onChange={(e) => alterar({ percentual: e.target.value })}
          />
        </label>
        <label>
          Dia do vencimento
          <input
            type="number"
            min="1"
            max="31"
            required
            value={pensao.diaVencimento}
            title="Em mês sem esse dia (ex.: 31 em fevereiro), a parcela vence no último dia"
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
        <label>
          Juros desde
          <select value={pensao.inicioJuros} onChange={(e) => alterar({ inicioJuros: e.target.value as InicioJuros })}>
            <option value="VENCIMENTO">o vencimento de cada parcela</option>
            <option value="CITACAO">a citação</option>
          </select>
        </label>
        <label>
          Data da citação
          <input
            type="date"
            disabled={pensao.inicioJuros !== 'CITACAO'}
            required={pensao.inicioJuros === 'CITACAO'}
            value={pensao.inicioJuros === 'CITACAO' ? pensao.citacao : ''}
            onChange={(e) => alterar({ citacao: e.target.value })}
          />
        </label>
      </fieldset>
      <p className="dica">
        Cada parcela vale o percentual do salário mínimo vigente no mês do vencimento. Com juros desde a citação, as
        parcelas vencidas antes dela contam juros da citação; as seguintes, do próprio vencimento.
      </p>

      <h3>Pagamentos parciais</h3>
      {pensao.pagamentos.map((p, i) => (
        <fieldset key={i} className="pagamento">
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
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              value={p.valor}
              onChange={(e) => alterarPagamento(i, { valor: e.target.value })}
            />
          </label>
          <button
            type="button"
            className="secundario"
            onClick={() => alterar({ pagamentos: pensao.pagamentos.filter((_, j) => j !== i) })}
          >
            Remover
          </button>
        </fieldset>
      ))}
      <p className="dica">
        O valor pago é abatido da parcela do mesmo mês antes da correção. Um pagamento maior que a parcela não é
        compensado com as outras.
      </p>
      <button
        type="button"
        className="secundario"
        onClick={() => alterar({ pagamentos: [...pensao.pagamentos, { mes: '', valor: '' }] })}
      >
        Adicionar pagamento
      </button>
    </div>
  )
}
