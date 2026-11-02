import type { InicioJuros, PagamentoRequest } from './api'
import { mes } from './formato'
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
      <div className="campos quatro">
        <label>
          % do salário mínimo
          <input
            type="number"
            step="0.01"
            min="0.01"
            required
            inputMode="decimal"
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
            <input
              type="number"
              step="0.01"
              min="0.01"
              required
              inputMode="decimal"
              value={p.valor}
              onChange={(e) => alterarPagamento(i, { valor: e.target.value })}
            />
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
    </div>
  )
}
