import { useState, type FormEvent } from 'react'
import { buscarRegimePadrao, calcular, type CalculoRequest, type Demonstrativo as Resultado, type ParcelaRequest } from './api'
import { Demonstrativo } from './Demonstrativo'
import { mes } from './formato'
import { EditorPensao } from './EditorPensao'
import { CampoDecimal } from './CampoDecimal'
import { exigirDecimal } from './numero'
import { paraPensao, pensaoVazia, type PensaoEditavel } from './pensao'
import { EditorRegime } from './EditorRegime'
import { paraEditavel, paraRegime, regraVazia, type RegraEditavel } from './regime'
import './App.css'

/**
 * Último dia do mês anterior. O cálculo usa os índices até o mês anterior ao da data-base, e o
 * índice do mês que acabou de fechar só sai lá pelo dia 10; antes disso, com a data de hoje, o
 * cálculo falharia. Depois, o padrão fica para trás e a dica do campo pede para avançá-lo.
 */
const fimDoMesAnterior = () => {
  const hoje = new Date()
  return new Date(hoje.getFullYear(), hoje.getMonth(), 0).toLocaleDateString('sv-SE')
}

const parcelaVazia = (): ParcelaRequest => ({ descricao: '', valor: '', inicioCorrecao: '', inicioJuros: '' })
const estaVazia = (p: ParcelaRequest) => !p.descricao && !p.valor && !p.inicioCorrecao && !p.inicioJuros

interface Calculo {
  /** Muda a cada cálculo, para o demonstrativo recomeçar do topo. */
  numero: number
  pedido: CalculoRequest
  resultado: Resultado
}

function App() {
  const [dataBase, setDataBase] = useState(fimDoMesAnterior)
  const [mesFechado] = useState(() => mes(fimDoMesAnterior().slice(0, 7)))
  const [aplicarArt523, setAplicarArt523] = useState(true)
  const [comPensao, setComPensao] = useState(true)
  const [separarRitos, setSepararRitos] = useState(true)
  const [pensao, setPensao] = useState<PensaoEditavel>(pensaoVazia)
  const [parcelas, setParcelas] = useState<ParcelaRequest[]>([])
  const [outrasAbertas, setOutrasAbertas] = useState(false)
  const [regimeProprio, setRegimeProprio] = useState(false)
  const [nomeRegime, setNomeRegime] = useState('')
  const [regras, setRegras] = useState<RegraEditavel[]>([])
  const [calculo, setCalculo] = useState<Calculo | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)

  const ritosAtivos = comPensao && separarRitos

  const alterarParcela = (indice: number, campo: keyof ParcelaRequest, valor: string) =>
    setParcelas((atuais) => atuais.map((p, i) => (i === indice ? { ...p, [campo]: valor } : p)))

  function usarPensao(ativa: boolean) {
    setComPensao(ativa)
    // Com a pensão, as parcelas avulsas são opcionais: some a parcela em branco que bloquearia o envio.
    if (ativa) {
      const restantes = parcelas.filter((p) => !estaVazia(p))
      setParcelas(restantes)
      // As que sobraram ficam à vista: um campo obrigatório escondido trava o envio sem aviso.
      if (restantes.length > 0) setOutrasAbertas(true)
    } else if (parcelas.length === 0) setParcelas([parcelaVazia()])
  }

  function adicionarParcela() {
    setParcelas([...parcelas, parcelaVazia()])
    setOutrasAbertas(true)
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
      const pedido: CalculoRequest = {
        dataBase,
        aplicarArt523,
        parcelas: parcelas.map((p) => ({ ...p, valor: exigirDecimal(p.valor, 'Valor da parcela'), inicioJuros: p.inicioJuros || null })),
        pensao: comPensao ? paraPensao(pensao, separarRitos) : undefined,
        regime: regimeProprio ? paraRegime(nomeRegime, regras) : undefined,
      }
      const resultado = await calcular(pedido)
      setCalculo((anterior) => ({ numero: (anterior?.numero ?? 0) + 1, pedido, resultado }))
    } catch (e) {
      setCalculo(null)
      setErro(e instanceof Error ? e.message : String(e))
    } finally {
      setCarregando(false)
    }
  }

  const camposDaParcela = (p: ParcelaRequest, i: number) => (
    <fieldset key={i} className="parcela">
      <legend className="sr-only">Parcela {i + 1}</legend>
      <label>
        Descrição
        <input required value={p.descricao} onChange={(e) => alterarParcela(i, 'descricao', e.target.value)} />
      </label>
      <label>
        Valor (R$)
        <CampoDecimal casas={2} required value={p.valor} onValor={(v) => alterarParcela(i, 'valor', v)} />
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
        <input type="date" value={p.inicioJuros ?? ''} onChange={(e) => alterarParcela(i, 'inicioJuros', e.target.value)} />
      </label>
      {(parcelas.length > 1 || comPensao) && (
        <button
          type="button"
          className="secundario"
          aria-label={`Remover a parcela ${p.descricao || i + 1}`}
          onClick={() => setParcelas(parcelas.filter((_, j) => j !== i))}
        >
          Remover
        </button>
      )}
    </fieldset>
  )

  return (
    <main>
      <header className="topo">
        <h1>Debeatur</h1>
        <p>Execução de alimentos, do título ao demonstrativo.</p>
      </header>

      <form onSubmit={enviar}>
        <section className="bloco" aria-labelledby="bloco-pensao">
          <h2 id="bloco-pensao">Pensão</h2>
          <label className="checkbox">
            <input type="checkbox" checked={comPensao} onChange={(e) => usarPensao(e.target.checked)} />
            Alimentos fixados em percentual do salário mínimo
          </label>
          {comPensao && <EditorPensao pensao={pensao} onPensao={setPensao} />}
        </section>

        <section className="bloco" aria-labelledby="bloco-processo">
          <h2 id="bloco-processo">Processo</h2>
          <div className="campos quatro">
            <label>
              Data-base do cálculo
              <input
                type="date"
                required
                aria-describedby="dica-data-base"
                value={dataBase}
                onChange={(e) => setDataBase(e.target.value)}
              />
            </label>
            {ritosAtivos && (
              <label>
                Ajuizamento da execução
                <input
                  type="date"
                  required
                  value={pensao.ajuizamento}
                  onChange={(e) => setPensao({ ...pensao, ajuizamento: e.target.value })}
                />
              </label>
            )}
          </div>
          <p className="dica" id="dica-data-base">
            O débito é atualizado até a data-base. Ela começa no fim do mês anterior porque o índice de {mesFechado} só
            sai por volta do dia 10. Depois que ele sair, avance a data-base: parada no fim do mês anterior, ela deixa de
            fora a correção e os juros do mês corrente.
          </p>

          {comPensao && (
            <>
              <label className="checkbox">
                <input type="checkbox" checked={separarRitos} onChange={(e) => setSepararRitos(e.target.checked)} />
                Separar os ritos da prisão e da penhora (art. 528 do CPC)
              </label>
              <p className="dica recuo">
                {separarRitos
                  ? 'Vão para a prisão as três parcelas vencidas até o ajuizamento e as que vencem depois dele; as demais, para a penhora.'
                  : 'Sem a separação, todas as parcelas formam um débito só.'}
              </p>
            </>
          )}

          <label className="checkbox">
            <input type="checkbox" checked={aplicarArt523} onChange={(e) => setAplicarArt523(e.target.checked)} />
            {ritosAtivos
              ? 'Multa e honorários do art. 523, §1º, do CPC (10% + 10%) sobre a penhora'
              : 'Multa e honorários do art. 523, §1º, do CPC (10% + 10%)'}
          </label>
        </section>

        {comPensao ? (
          <details
            className="bloco"
            open={outrasAbertas}
            onToggle={(e) => setOutrasAbertas(e.currentTarget.open)}
            // Fechado, o campo inválido não recebe foco e o navegador barra o envio sem mensagem;
            // abre na hora, antes de o navegador procurar o campo para avisar.
            onInvalidCapture={(e) => {
              e.currentTarget.open = true
            }}
          >
            <summary>
              <h2>Outras parcelas{parcelas.length > 0 && ` (${parcelas.length})`}</h2>
            </summary>
            <p className="dica">
              Valores fora da pensão, como despesas extraordinárias, com a própria data de correção e de juros.
            </p>
            {parcelas.map(camposDaParcela)}
            <button type="button" className="secundario" onClick={adicionarParcela}>
              Adicionar parcela
            </button>
          </details>
        ) : (
          <section className="bloco" aria-labelledby="bloco-parcelas">
            <h2 id="bloco-parcelas">Parcelas</h2>
            {parcelas.map(camposDaParcela)}
            <button type="button" className="secundario" onClick={adicionarParcela}>
              Adicionar parcela
            </button>
          </section>
        )}

        <section className="bloco" aria-labelledby="bloco-regime">
          <h2 id="bloco-regime">Correção e juros</h2>
          <div className="opcoes-regime" role="radiogroup" aria-labelledby="bloco-regime">
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
        </section>

        <div className="enviar">
          <button type="submit" disabled={carregando}>
            {carregando ? 'Calculando…' : 'Calcular'}
          </button>
        </div>
      </form>

      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}

      {calculo && <Demonstrativo key={calculo.numero} pedido={calculo.pedido} resultado={calculo.resultado} />}
    </main>
  )
}

export default App
