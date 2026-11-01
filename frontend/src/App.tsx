import { useState, type FormEvent } from 'react'
import { buscarRegimePadrao, calcular, type Demonstrativo, type ParcelaRequest } from './api'
import { EditorPensao } from './EditorPensao'
import { paraPensao, pensaoVazia, type PensaoEditavel } from './pensao'
import { EditorRegime } from './EditorRegime'
import { INDICES, descreverPeriodo, paraEditavel, paraRegime, regraVazia, type RegraEditavel } from './regime'
import './App.css'

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const data = (iso: string | null) => (iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR') : '—')

const hoje = () => new Date().toLocaleDateString('sv-SE')

const parcelaVazia =(): ParcelaRequest => ({ descricao: '', valor: '', inicioCorrecao: '', inicioJuros: '' })
const estaVazia = (p: ParcelaRequest) => !p.descricao && !p.valor && !p.inicioCorrecao && !p.inicioJuros

function App() {
  const [dataBase, setDataBase] = useState(hoje)
  const [aplicarArt523, setAplicarArt523] = useState(true)
  const [comPensao, setComPensao] = useState(false)
  const [pensao, setPensao] = useState<PensaoEditavel>(pensaoVazia)
  const [parcelas, setParcelas] = useState<ParcelaRequest[]>([parcelaVazia()])
  const [regimeProprio, setRegimeProprio] = useState(false)
  const [nomeRegime, setNomeRegime] = useState('')
  const [regras, setRegras] = useState<RegraEditavel[]>([])
  const [resultado, setResultado] = useState<Demonstrativo | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)

  const alterarParcela = (indice: number, campo: keyof ParcelaRequest, valor: string) =>
    setParcelas((atuais) => atuais.map((p, i) => (i === indice ? { ...p, [campo]: valor } : p)))

  function usarPensao(ativa: boolean) {
    setComPensao(ativa)
    // Com a pensão, as parcelas avulsas são opcionais: some a parcela em branco que bloquearia o envio.
    if (ativa) setParcelas((atuais) => atuais.filter((p) => !estaVazia(p)))
    else if (parcelas.length === 0) setParcelas([parcelaVazia()])
  }

  async function usarRegimeProprio(proprio: boolean) {
    setRegimeProprio(proprio)
    if (!proprio || regras.length > 0) return
    try {
      setRegras(paraEditavel((await buscarRegimePadrao()).regras))
    } catch {
      setRegras([regraVazia()])
    }
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErro(null)
    setCarregando(true)
    try {
      setResultado(
        await calcular({
          dataBase,
          aplicarArt523,
          parcelas: parcelas.map((p) => ({ ...p, inicioJuros: p.inicioJuros || null })),
          pensao: comPensao ? paraPensao(pensao) : undefined,
          regime: regimeProprio ? paraRegime(nomeRegime, regras) : undefined,
        }),
      )
    } catch (e) {
      setResultado(null)
      setErro(e instanceof Error ? e.message : String(e))
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main>
      <header>
        <h1>Debeatur</h1>
        <p>Cálculo de cumprimento de sentença, do índice ao demonstrativo.</p>
      </header>

      <form onSubmit={enviar}>
        <div className="linha">
          <label>
            Data-base do cálculo
            <input type="date" required value={dataBase} onChange={(e) => setDataBase(e.target.value)} />
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={aplicarArt523} onChange={(e) => setAplicarArt523(e.target.checked)} />
            Multa e honorários do art. 523, §1º, CPC (10% + 10%)
          </label>
        </div>

        <h2>Pensão alimentícia</h2>
        <label className="checkbox">
          <input type="checkbox" checked={comPensao} onChange={(e) => usarPensao(e.target.checked)} />
          Alimentos fixados em percentual do salário mínimo
        </label>
        {comPensao && <EditorPensao pensao={pensao} onPensao={setPensao} />}

        <h2>{comPensao ? 'Outras parcelas (opcional)' : 'Parcelas'}</h2>
        {parcelas.map((p, i) => (
          <fieldset key={i} className="parcela">
            <label>
              Descrição
              <input required value={p.descricao} onChange={(e) => alterarParcela(i, 'descricao', e.target.value)} />
            </label>
            <label>
              Valor (R$)
              <input
                type="number"
                step="0.01"
                min="0.01"
                required
                value={p.valor}
                onChange={(e) => alterarParcela(i, 'valor', e.target.value)}
              />
            </label>
            <label>
              Início da correção
              <input
                type="date"
                required
                value={p.inicioCorrecao}
                onChange={(e) => alterarParcela(i, 'inicioCorrecao', e.target.value)}
              />
            </label>
            <label>
              Início dos juros
              <input
                type="date"
                value={p.inicioJuros ?? ''}
                onChange={(e) => alterarParcela(i, 'inicioJuros', e.target.value)}
              />
            </label>
            {(parcelas.length > 1 || comPensao) && (
              <button type="button" className="secundario" onClick={() => setParcelas(parcelas.filter((_, j) => j !== i))}>
                Remover
              </button>
            )}
          </fieldset>
        ))}

        <div className="acoes">
          <button type="button" className="secundario" onClick={() => setParcelas([...parcelas, parcelaVazia()])}>
            Adicionar parcela
          </button>
        </div>

        <h2>Correção e juros</h2>
        <div className="opcoes-regime">
          <label className="checkbox">
            <input type="radio" name="regime" checked={!regimeProprio} onChange={() => usarRegimeProprio(false)} />
            Regime padrão do TJMG (tabela da CGJ + 1% a.m.; desde a Lei 14.905/2024, IPCA + taxa legal)
          </label>
          <label className="checkbox">
            <input type="radio" name="regime" checked={regimeProprio} onChange={() => usarRegimeProprio(true)} />
            Critérios fixados no título
          </label>
        </div>
        {regimeProprio && (
          <EditorRegime nome={nomeRegime} onNome={setNomeRegime} regras={regras} onRegras={setRegras} />
        )}

        <div className="acoes enviar">
          <button type="submit" disabled={carregando}>
            {carregando ? 'Calculando…' : 'Calcular'}
          </button>
        </div>
      </form>

      {erro && <p className="erro">{erro}</p>}

      {resultado && (
        <section>
          <h2>Demonstrativo atualizado até {data(resultado.dataBase)}</h2>
          <p className="regime">Regime: {resultado.regime.nome}</p>
          <ul className="regras">
            {resultado.regime.regras.map((r, i) => (
              <li key={i}>
                {descreverPeriodo(r)}: {INDICES[r.correcao]} + {r.descricaoJuros}
              </li>
            ))}
          </ul>
          <div className="tabela">
            <table>
              <thead>
                <tr>
                  <th>Parcela</th>
                  <th>Valor original</th>
                  <th>Correção desde</th>
                  <th>Fator</th>
                  <th>Corrigido</th>
                  <th>Juros desde</th>
                  <th>Juros (%)</th>
                  <th>Juros (R$)</th>
                  <th>Subtotal</th>
                </tr>
              </thead>
              <tbody>
                {resultado.linhas.map((l, i) => (
                  <tr key={i}>
                    <td>{l.descricao}</td>
                    <td>{moeda.format(l.valorOriginal)}</td>
                    <td>{data(l.inicioCorrecao)}</td>
                    <td>{l.fatorCorrecao.toFixed(7)}</td>
                    <td>{moeda.format(l.valorCorrigido)}</td>
                    <td>{data(l.inicioJuros)}</td>
                    <td>{l.percentualJuros.toFixed(2)}%</td>
                    <td>{moeda.format(l.valorJuros)}</td>
                    <td>{moeda.format(l.subtotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <dl className="totais">
            <dt>Débito atualizado</dt>
            <dd>{moeda.format(resultado.debitoAtualizado)}</dd>
            <dt>Multa (art. 523)</dt>
            <dd>{moeda.format(resultado.multaArt523)}</dd>
            <dt>Honorários (art. 523)</dt>
            <dd>{moeda.format(resultado.honorariosArt523)}</dd>
            <dt className="total">Total</dt>
            <dd className="total">{moeda.format(resultado.total)}</dd>
          </dl>
        </section>
      )}
    </main>
  )
}

export default App
