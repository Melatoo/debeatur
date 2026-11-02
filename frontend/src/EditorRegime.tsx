import type { IndiceCorrecao, TipoJuros } from './api'
import { CampoDecimal } from './CampoDecimal'
import {
  INDICES,
  TIPOS_JUROS,
  mesSeguinte,
  regraVazia,
  selicNaViradaDaLei14905,
  usaPercentual,
  type RegraEditavel,
} from './regime'

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
          <legend className="sr-only">Período {i + 1}</legend>
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
            <CampoDecimal
              casas={4}
              milhar={false}
              disabled={!usaPercentual(r.juros)}
              required={usaPercentual(r.juros)}
              value={usaPercentual(r.juros) ? r.percentual : ''}
              onValor={(v) => alterar(i, { percentual: v })}
            />
          </label>
          {regras.length > 1 ? (
            <button
              type="button"
              className="secundario"
              aria-label={`Remover o período ${i + 1}`}
              onClick={() => onRegras(regras.filter((_, j) => j !== i))}
            >
              Remover
            </button>
          ) : (
            <span />
          )}
          {r.juros === 'TRANSICAO_TAXA_LEGAL' && (
            <label className="dia-corte">
              Último dia da taxa fixa
              <input
                type="date"
                required
                min={r.inicio ? `${r.inicio}-01` : undefined}
                value={r.ultimoDiaTaxaFixa}
                title="A taxa fixa vale até este dia, inclusive; nos dias seguintes do mês, a taxa legal pro rata"
                onChange={(e) => alterar(i, { ultimoDiaTaxaFixa: e.target.value })}
              />
            </label>
          )}
          {r.correcao === 'SELIC' && r.juros !== 'SEM_JUROS' && (
            <p className="aviso">
              A SELIC já engloba correção e juros (Tema 1368/STJ). Só some outra taxa se o título mandar.
            </p>
          )}
          {selicNaViradaDaLei14905(r) && (
            <p className="aviso">
              Os períodos valem por mês inteiro, então agosto/2024 fica todo{' '}
              {r.fim === '2024-07' ? 'com o critério da regra seguinte' : 'com a SELIC'}. A Lei 14.905/2024 vale desde
              30/08/2024, e a divisão do mês por dias ainda não é feita: confira esse mês no cálculo.
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
