import type { IndiceCorrecao, TipoJuros } from './api'
import { INDICES, TIPOS_JUROS, mesSeguinte, regraVazia, type RegraEditavel } from './regime'

interface Props {
  nome: string
  onNome: (nome: string) => void
  regras: RegraEditavel[]
  onRegras: (regras: RegraEditavel[]) => void
}

export function EditorRegime({ nome, onNome, regras, onRegras }: Props) {
  function alterar(indice: number, campos: Partial<RegraEditavel>) {
    const novas = regras.map((r, i) => (i === indice ? { ...r, ...campos } : r))
    // Mantém os períodos contíguos: a regra seguinte começa no mês após o novo fim.
    if (campos.fim && indice + 1 < novas.length) {
      novas[indice + 1] = { ...novas[indice + 1], inicio: mesSeguinte(campos.fim) }
    }
    onRegras(novas)
  }

  function adicionar() {
    const ultima = regras.at(-1)
    onRegras([...regras, regraVazia(ultima?.fim ? mesSeguinte(ultima.fim) : '')])
  }

  return (
    <div className="editor-regime">
      <label>
        Nome do regime (aparece no demonstrativo)
        <input value={nome} placeholder="Critérios fixados no título" onChange={(e) => onNome(e.target.value)} />
      </label>

      {regras.map((r, i) => (
        <fieldset key={i} className="regra">
          <label>
            De
            <input type="month" required value={r.inicio} onChange={(e) => alterar(i, { inicio: e.target.value })} />
          </label>
          <label>
            Até
            <input
              type="month"
              value={r.fim}
              required={i < regras.length - 1}
              title="Deixe em branco na última regra para valer até a data-base"
              onChange={(e) => alterar(i, { fim: e.target.value })}
            />
          </label>
          <label>
            Correção
            <select value={r.correcao} onChange={(e) => alterar(i, { correcao: e.target.value as IndiceCorrecao })}>
              {Object.entries(INDICES).map(([valor, rotulo]) => (
                <option key={valor} value={valor}>
                  {rotulo}
                </option>
              ))}
            </select>
          </label>
          <label>
            Juros
            <select value={r.juros} onChange={(e) => alterar(i, { juros: e.target.value as TipoJuros })}>
              {Object.entries(TIPOS_JUROS).map(([valor, rotulo]) => (
                <option key={valor} value={valor}>
                  {rotulo}
                </option>
              ))}
            </select>
          </label>
          <label>
            % ao mês
            <input
              type="number"
              step="0.0001"
              min="0.0001"
              disabled={r.juros !== 'TAXA_FIXA'}
              required={r.juros === 'TAXA_FIXA'}
              value={r.juros === 'TAXA_FIXA' ? r.percentual : ''}
              onChange={(e) => alterar(i, { percentual: e.target.value })}
            />
          </label>
          {regras.length > 1 ? (
            <button type="button" className="secundario" onClick={() => onRegras(regras.filter((_, j) => j !== i))}>
              Remover
            </button>
          ) : (
            <span />
          )}
          {r.correcao === 'SELIC' && r.juros !== 'SEM_JUROS' && (
            <p className="aviso">
              A SELIC já engloba correção e juros (Tema 1368/STJ). Só some outra taxa se o título mandar.
            </p>
          )}
        </fieldset>
      ))}

      <p className="dica">
        Os períodos precisam ser contínuos e cobrir do primeiro termo inicial até o mês anterior à data-base. Deixe
        o “Até” da última regra em branco para que ela valha até a data-base.
      </p>
      <button type="button" className="secundario" onClick={adicionar}>
        Adicionar período
      </button>
    </div>
  )
}
