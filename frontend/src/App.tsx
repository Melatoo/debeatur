import { useEffect, useEffectEvent, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  buscarRegimePadrao,
  calcular,
  type CalculoRequest,
  type Demonstrativo as Resultado,
  type ParcelaRequest,
  type ProcessoRequest,
} from './api'
import { Demonstrativo } from './Demonstrativo'
import { mes } from './formato'
import { EditorPensao } from './EditorPensao'
import { CampoDecimal } from './CampoDecimal'
import { exigirDecimal } from './numero'
import { paraPensao, pensaoVazia } from './pensao'
import { armazenamentoDoNavegador } from './armazenamento'
import {
  ESPERA_RASCUNHO_MS,
  apagarRascunho,
  ehRascunhoVazio,
  lerRascunho,
  salvarRascunho,
  type Rascunho,
} from './rascunho'
import { EditorRegime } from './EditorRegime'
import { ESPERA_RECALCULO_MS, chaveDoPedido, formularioValido } from './recalculo'
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

const processoVazio = (): ProcessoRequest => ({ numero: '', juizo: '', exequente: '', executado: '' })

/** Os campos sem espaços nas pontas; sem nenhum preenchido, o processo nem vai no pedido. */
function paraProcesso(processo: ProcessoRequest): ProcessoRequest | undefined {
  const limpo: ProcessoRequest = {
    numero: processo.numero.trim(),
    juizo: processo.juizo.trim(),
    exequente: processo.exequente.trim(),
    executado: processo.executado.trim(),
  }
  return Object.values(limpo).some(Boolean) ? limpo : undefined
}

/** O formulário em branco, como ele abre e como fica depois do "Limpar". */
const rascunhoVazio = (): Rascunho => ({
  processo: processoVazio(),
  dataBase: fimDoMesAnterior(),
  aplicarArt523: true,
  comPensao: true,
  separarRitos: true,
  pensao: pensaoVazia(),
  parcelas: [],
  outrasAbertas: false,
  regimeProprio: false,
  nomeRegime: '',
  regras: [],
})

/** Critérios do título escolhidos, mas sem as regras: elas ainda precisam vir do servidor. */
const faltamRegras = (r: Rascunho) => r.regimeProprio && r.regras.length === 0

const quando = (d: Date) =>
  d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })

interface Calculo {
  /** Muda a cada clique em Calcular, para o demonstrativo recomeçar do topo; o recálculo automático o mantém. */
  numero: number
  pedido: CalculoRequest
  /** `chaveDoPedido(pedido)`: comparada com a do formulário, diz se o demonstrativo está desatualizado. */
  chave: string
  resultado: Resultado
}

/** O que o "Limpar" tirou da tela, para o "Desfazer" devolver. */
interface Limpeza {
  rascunho: Rascunho
  calculo: Calculo | null
}

function App() {
  // Lido uma vez, na carga: o caso em que a pessoa estava antes de recarregar ou fechar a aba.
  const [inicio] = useState(() => {
    const salvo = lerRascunho(armazenamentoDoNavegador())
    return {
      salvoEm: salvo?.salvoEm ?? null,
      rascunho: salvo?.rascunho ?? rascunhoVazio(),
      // Quem restaura o rascunho passa a responder por ele: o "Limpar" desta aba o apaga.
      aba: salvo?.aba || Math.random().toString(36).slice(2),
    }
  })
  const { aba } = inicio
  /** O formulário inteiro, num estado só: é ele que vai para o rascunho, para o "Limpar" e para o "Desfazer". */
  const [rascunho, setRascunho] = useState<Rascunho>(inicio.rascunho)
  const {
    processo,
    dataBase,
    aplicarArt523,
    comPensao,
    separarRitos,
    pensao,
    parcelas,
    outrasAbertas,
    regimeProprio,
    nomeRegime,
    regras,
  } = rascunho
  const alterar = <K extends keyof Rascunho>(campo: K, valor: Rascunho[K]) =>
    setRascunho((atual) => ({ ...atual, [campo]: valor }))
  const [mesFechado] = useState(() => mes(fimDoMesAnterior().slice(0, 7)))
  /** Quando o rascunho restaurado na carga foi salvo; some com o "Limpar". */
  const [restauradoDe, setRestauradoDe] = useState(inicio.salvoEm)
  const [limpeza, setLimpeza] = useState<Limpeza | null>(null)
  const [calculo, setCalculo] = useState<Calculo | null>(null)
  /** Erro do último cálculo, com a chave do formulário que o provocou: editar de novo o tira da tela. */
  const [erro, setErro] = useState<{ chave: string; mensagem: string } | null>(null)
  const [carregando, setCarregando] = useState(false)
  /** Chave do formulário que, depois do primeiro cálculo, ficou com campo inválido e não pôde ser recalculado. */
  const [invalidoEm, setInvalidoEm] = useState<string | null>(null)
  const formulario = useRef<HTMLFormElement>(null)
  /** O cálculo que está no servidor; um mais novo cancela o anterior, que chegaria fora de ordem. */
  const emCurso = useRef<AbortController | null>(null)
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  /** Chave do formulário com um recálculo esperando a pausa na digitação. */
  const agendadaPara = useRef<string | null>(null)
  const [buscandoRegime, setBuscandoRegime] = useState(() => faltamRegras(inicio.rascunho))
  /** Muda quando o formulário é trocado inteiro, para respostas que chegam depois serem descartadas. */
  const versaoDoFormulario = useRef(0)

  const ritosAtivos = comPensao && separarRitos

  const emBranco = useMemo(() => ehRascunhoVazio(rascunho, rascunhoVazio()), [rascunho])

  /** O que está no navegador agora; abrir a página sem mexer em nada não regrava, nem muda a hora do rascunho. */
  const ultimoSalvo = useRef(JSON.stringify(inicio.rascunho))
  /** A gravação esperando a pausa na digitação; sair da página a faz na hora. */
  const gravacaoPendente = useRef<(() => void) | null>(null)
  // Cada edição vai para o navegador, depois de uma pausa; o formulário em branco apaga o que havia.
  useEffect(() => {
    const texto = JSON.stringify(rascunho)
    if (texto === ultimoSalvo.current) {
      gravacaoPendente.current = null
      return
    }
    const gravarAgora = () => {
      gravacaoPendente.current = null
      ultimoSalvo.current = texto
      const armazenamento = armazenamentoDoNavegador()
      if (emBranco) apagarRascunho(armazenamento, aba)
      else salvarRascunho(armazenamento, rascunho, aba)
    }
    gravacaoPendente.current = gravarAgora
    const espera = setTimeout(gravarAgora, ESPERA_RASCUNHO_MS)
    return () => clearTimeout(espera)
  }, [rascunho, emBranco, aba])
  useEffect(() => {
    const descarregar = () => gravacaoPendente.current?.()
    window.addEventListener('pagehide', descarregar)
    return () => {
      window.removeEventListener('pagehide', descarregar)
      descarregar()
    }
  }, [])

  const alterarParcela = (indice: number, campo: keyof ParcelaRequest, valor: string) =>
    setRascunho((r) => ({ ...r, parcelas: r.parcelas.map((p, i) => (i === indice ? { ...p, [campo]: valor } : p)) }))

  function usarPensao(ativa: boolean) {
    setRascunho((r) => {
      if (!ativa) return { ...r, comPensao: false, parcelas: r.parcelas.length === 0 ? [parcelaVazia()] : r.parcelas }
      // Com a pensão, as parcelas avulsas são opcionais: some a parcela em branco que bloquearia o envio.
      const restantes = r.parcelas.filter((p) => !estaVazia(p))
      // As que sobraram ficam à vista: um campo obrigatório escondido trava o envio sem aviso.
      return { ...r, comPensao: true, parcelas: restantes, outrasAbertas: r.outrasAbertas || restantes.length > 0 }
    })
  }

  function adicionarParcela() {
    setRascunho((r) => ({ ...r, parcelas: [...r.parcelas, parcelaVazia()], outrasAbertas: true }))
  }

  /**
   * Traz as regras do regime padrão para o editor. Sem elas, o pedido iria sem regime: quem chama
   * marca `buscandoRegime`, e o recálculo espera a busca.
   */
  async function buscarRegras() {
    const versao = versaoDoFormulario.current
    let novas: RegraEditavel[]
    try {
      novas = paraEditavel((await buscarRegimePadrao()).regras)
    } catch {
      novas = [regraVazia()]
    }
    // Um "Limpar" ou "Desfazer" durante a busca trocou o formulário inteiro: as regras chegaram tarde,
    // e o aviso de busca em curso já é de outra busca, ou de nenhuma.
    if (versaoDoFormulario.current !== versao) return
    alterar('regras', novas)
    setBuscandoRegime(false)
  }

  function usarRegimeProprio(proprio: boolean) {
    alterar('regimeProprio', proprio)
    if (proprio && regras.length === 0 && !buscandoRegime) {
      setBuscandoRegime(true)
      void buscarRegras()
    }
  }

  // O rascunho restaurado pode ter ficado sem as regras, se a aba fechou no meio da busca.
  const buscarRegrasDoInicio = useEffectEvent(() => {
    if (faltamRegras(inicio.rascunho)) void buscarRegras()
  })
  useEffect(() => buscarRegrasDoInicio(), [])

  /**
   * O formulário como pedido de cálculo, sem o processo, que só identifica o PDF. Refeito só quando
   * um dado do cálculo muda; se algum valor nem pode ser lido, fica a mensagem, e a chave vazia.
   */
  const atual = useMemo(() => {
    try {
      const pedido: CalculoRequest = {
        dataBase,
        aplicarArt523,
        parcelas: parcelas.map((p) => ({ ...p, valor: exigirDecimal(p.valor, 'Valor da parcela'), inicioJuros: p.inicioJuros || null })),
        pensao: comPensao ? paraPensao(pensao, separarRitos) : undefined,
        regime: regimeProprio ? paraRegime(nomeRegime, regras) : undefined,
      }
      return { pedido, chave: chaveDoPedido(pedido), falha: null }
    } catch (e) {
      return { pedido: null, chave: '', falha: e instanceof Error ? e.message : String(e) }
    }
  }, [dataBase, aplicarArt523, parcelas, comPensao, pensao, separarRitos, regimeProprio, nomeRegime, regras])
  const chaveAtual = atual.chave
  const chaveCalculada = calculo?.chave ?? null

  const desatualizado = calculo !== null && chaveAtual !== calculo.chave
  const invalido = desatualizado && invalidoEm === chaveAtual
  // Antes do primeiro cálculo, o erro fica até o próximo clique; depois, só enquanto o formulário não muda.
  const mensagemDeErro = erro && (calculo === null || erro.chave === chaveAtual) ? erro.mensagem : null

  /** @param manual pelo botão Calcular: leva até o resultado; o recálculo automático deixa a tela onde está */
  async function executar(manual: boolean) {
    emCurso.current?.abort()
    const controle = new AbortController()
    emCurso.current = controle
    const { pedido, chave, falha } = atual
    // Uma nova tentativa tira o erro anterior da tela, e a faixa passa a dizer que está atualizando.
    setErro(null)
    setCarregando(true)
    try {
      if (!pedido) throw new Error(falha ?? 'Há campos a corrigir no formulário.')
      const resultado = await calcular({ ...pedido, processo: paraProcesso(processo) }, controle.signal)
      if (controle.signal.aborted) return
      setInvalidoEm(null)
      setCalculo((anterior) => ({
        numero: manual || !anterior ? (anterior?.numero ?? 0) + 1 : anterior.numero,
        pedido,
        chave,
        resultado,
      }))
    } catch (e) {
      if (controle.signal.aborted) return
      // O demonstrativo anterior fica, marcado como desatualizado, em vez de sumir com o erro.
      setErro({ chave, mensagem: e instanceof Error ? e.message : String(e) })
    } finally {
      if (emCurso.current === controle) {
        emCurso.current = null
        setCarregando(false)
      }
    }
  }

  const recalcular = useEffectEvent(() => {
    if (!chaveAtual || !formularioValido(formulario.current)) {
      emCurso.current?.abort()
      setInvalidoEm(chaveAtual)
      return
    }
    void executar(false)
  })

  function cancelarEspera() {
    clearTimeout(espera.current)
    agendadaPara.current = null
  }

  // Depois do primeiro cálculo, cada edição recalcula sozinha, assim que o formulário fica válido.
  useEffect(() => {
    if (chaveCalculada === null) return
    if (chaveAtual === chaveCalculada) {
      cancelarEspera()
      // Voltou ao que já está na tela: o cálculo em curso, de um valor intermediário, ficou velho.
      emCurso.current?.abort()
      return
    }
    if (buscandoRegime) {
      cancelarEspera()
      return
    }
    // Uma resposta que chega durante a espera muda o demonstrativo, não o formulário: a espera segue.
    if (agendadaPara.current === chaveAtual) return
    clearTimeout(espera.current)
    agendadaPara.current = chaveAtual
    espera.current = setTimeout(() => {
      agendadaPara.current = null
      recalcular()
    }, ESPERA_RECALCULO_MS)
  }, [chaveAtual, chaveCalculada, buscandoRegime])

  useEffect(() => () => clearTimeout(espera.current), [])

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    cancelarEspera()
    void executar(true)
  }

  /** Depois de uma falha, o formulário não muda sozinho, e nada chamaria outro recálculo. */
  function tentarDeNovo() {
    cancelarEspera()
    void executar(false)
  }

  /** Troca o formulário inteiro e o demonstrativo, sem deixar cálculo em curso nem erro do caso anterior. */
  function trocarCaso(r: Rascunho, novoCalculo: Calculo | null) {
    versaoDoFormulario.current++
    cancelarEspera()
    emCurso.current?.abort()
    emCurso.current = null
    setCarregando(false)
    setErro(null)
    setInvalidoEm(null)
    setRascunho(r)
    setCalculo(novoCalculo)
    // O caso desfeito pode ter sido limpo no meio da busca das regras: ela recomeça para ele.
    setBuscandoRegime(faltamRegras(r))
    if (faltamRegras(r)) void buscarRegras()
  }

  /** Sem confirmação: o "Desfazer" devolve o caso enquanto o formulário não for preenchido de novo. */
  function limpar() {
    setLimpeza({ rascunho, calculo })
    setRestauradoDe(null)
    trocarCaso(rascunhoVazio(), null)
    apagarRascunho(armazenamentoDoNavegador(), aba)
  }

  function desfazer() {
    if (!limpeza) return
    trocarCaso(limpeza.rascunho, limpeza.calculo)
    setLimpeza(null)
  }

  // O "Desfazer" só vale enquanto o formulário segue em branco: depois, sobrescreveria o caso novo.
  const podeDesfazer = limpeza !== null && emBranco && calculo === null

  const situacao = !desatualizado
    ? carregando
      ? 'atualizando'
      : 'atual'
    : invalido || mensagemDeErro
      ? 'desatualizado'
      : 'atualizando'

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
          onClick={() => alterar('parcelas', parcelas.filter((_, j) => j !== i))}
        >
          Remover
        </button>
      )}
    </fieldset>
  )

  return (
    <main>
      <div className="coluna-parametros">
        <header className="topo">
          <h1>Debeatur</h1>
          <p>Execução de alimentos, do título ao demonstrativo.</p>
        </header>

        <form ref={formulario} onSubmit={enviar}>
          <section className="bloco" aria-labelledby="bloco-pensao">
            <h2 id="bloco-pensao">Pensão</h2>
            <label className="checkbox">
              <input type="checkbox" checked={comPensao} onChange={(e) => usarPensao(e.target.checked)} />
              Alimentos fixados em percentual do salário mínimo
            </label>
            {comPensao && <EditorPensao pensao={pensao} onPensao={(p) => alterar('pensao', p)} />}
          </section>

          <section className="bloco" aria-labelledby="bloco-processo">
            <h2 id="bloco-processo">Processo</h2>
            <div className="campos quatro">
              <label>
                Número do processo
                <input
                  maxLength={50}
                  placeholder="0000000-00.0000.8.13.0000"
                  value={processo.numero}
                  onChange={(e) => alterar('processo', { ...processo, numero: e.target.value })}
                />
              </label>
              <label>
                Juízo
                <input
                  maxLength={200}
                  value={processo.juizo}
                  onChange={(e) => alterar('processo', { ...processo, juizo: e.target.value })}
                />
              </label>
              <label>
                Exequente
                <input
                  maxLength={200}
                  value={processo.exequente}
                  onChange={(e) => alterar('processo', { ...processo, exequente: e.target.value })}
                />
              </label>
              <label>
                Executado
                <input
                  maxLength={200}
                  value={processo.executado}
                  onChange={(e) => alterar('processo', { ...processo, executado: e.target.value })}
                />
              </label>
            </div>
            <p className="dica">Opcionais: só identificam o processo no cabeçalho do PDF.</p>
            <div className="campos quatro">
              <label>
                Data-base do cálculo
                <input
                  type="date"
                  required
                  aria-describedby="dica-data-base"
                  value={dataBase}
                  onChange={(e) => alterar('dataBase', e.target.value)}
                />
              </label>
              {ritosAtivos && (
                <label>
                  Ajuizamento da execução
                  <input
                    type="date"
                    required
                    value={pensao.ajuizamento}
                    onChange={(e) => alterar('pensao', { ...pensao, ajuizamento: e.target.value })}
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
                  <input type="checkbox" checked={separarRitos} onChange={(e) => alterar('separarRitos', e.target.checked)} />
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
              <input type="checkbox" checked={aplicarArt523} onChange={(e) => alterar('aplicarArt523', e.target.checked)} />
              {ritosAtivos
                ? 'Multa e honorários do art. 523, §1º, do CPC (10% + 10%) sobre a penhora'
                : 'Multa e honorários do art. 523, §1º, do CPC (10% + 10%)'}
            </label>
          </section>

          {comPensao ? (
            <details
              className="bloco"
              open={outrasAbertas}
              onToggle={(e) => alterar('outrasAbertas', e.currentTarget.open)}
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
              <EditorRegime nome={nomeRegime} onNome={(n) => alterar('nomeRegime', n)} regras={regras} onRegras={(r) => alterar('regras', r)} />
            )}
          </section>

          <div className="enviar">
            <div className="botoes">
              {/* Depois do primeiro cálculo, o andamento aparece no demonstrativo, e o botão não pisca a cada edição. */}
              <button type="submit" disabled={carregando && !calculo}>
                {carregando && !calculo ? 'Calculando…' : 'Calcular'}
              </button>
              <button type="button" className="secundario" onClick={limpar} disabled={emBranco && !calculo}>
                Limpar
              </button>
            </div>
            {calculo && <p className="dica">O demonstrativo se atualiza sozinho a cada alteração.</p>}
            <p className="dica rascunho" role="status">
              {podeDesfazer ? (
                <>
                  Formulário limpo.{' '}
                  <button type="button" className="secundario" onClick={desfazer}>
                    Desfazer
                  </button>
                </>
              ) : (
                !emBranco && (
                  <>
                    {restauradoDe && `Caso restaurado do rascunho de ${quando(restauradoDe)}. `}
                    Salvo só neste navegador; em computador compartilhado, use Limpar ao terminar.
                  </>
                )
              )}
            </p>
          </div>
        </form>
      </div>

      <div className="resultado">
        {/*
          Com demonstrativo desatualizado, o erro vai na faixa dele, que acompanha a rolagem. Se o
          formulário ainda bate com o demonstrativo, os números valem, e o erro fica aqui.
        */}
        {mensagemDeErro && !desatualizado && (
          <p className="erro" role="alert">
            {mensagemDeErro}
          </p>
        )}

        {!calculo && (
          <p className="resultado-vazio">
            O demonstrativo aparece aqui depois do primeiro cálculo e acompanha cada alteração no formulário.
          </p>
        )}

        {calculo && (
          <Demonstrativo
            key={calculo.numero}
            pedido={calculo.pedido}
            resultado={calculo.resultado}
            processo={paraProcesso(processo)}
            situacao={situacao}
            motivo={
              invalido
                ? 'Há campos a corrigir no formulário. Os valores abaixo são do cálculo anterior.'
                : mensagemDeErro
                  ? `O cálculo com os dados atuais falhou: ${mensagemDeErro.replace(/\.$/, '')}. Os valores abaixo são do cálculo anterior.`
                  : null
            }
            aoTentarDeNovo={mensagemDeErro && !invalido ? tentarDeNovo : undefined}
          />
        )}
      </div>
    </main>
  )
}

export default App
