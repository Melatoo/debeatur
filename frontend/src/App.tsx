import { useEffect, useEffectEvent, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  buscarRegimePadrao,
  calcular,
  ErroDaApi,
  reabrirCalculo,
  salvarCalculo,
  type CalculoRequest,
  type Demonstrativo as Resultado,
  type ParcelaRequest,
  type ProcessoRequest,
  type Regime,
  type ResumoCalculo,
} from './api'
import { useAlturaFixa } from './alturaFixa'
import { Demonstrativo, type AcaoDaSituacao } from './Demonstrativo'
import { data, dataHora, mes } from './formato'
import { EditorPensao } from './EditorPensao'
import { CampoDecimal } from './CampoDecimal'
import { armazenamentoDoNavegador } from './armazenamento'
import { estaVazia, fimDoMesAnterior, paraPedido, paraProcesso, parcelaVazia, rascunhoVazio } from './caso'
import { novaDataBase, paraRascunho, regimeSalvo, tituloPadrao, type RegimeSalvo } from './calculoSalvo'
import { ListaCalculosSalvos, SalvarCalculo, type AcaoSalvo } from './CalculosSalvos'
import { useSessao } from './sessao'
import {
  ESPERA_RASCUNHO_MS,
  apagarRascunho,
  ehRascunhoVazio,
  lerRascunho,
  salvarRascunho,
  type Rascunho,
} from './rascunho'
import { EditorRegime } from './EditorRegime'
import { competenciasQuitadas, parcelasAntigas, textoDoAviso } from './prescricao'
import { Dica } from './Dica'
import { ehApple, ehAtalhoDeCalcular, rotuloDoAtalho } from './atalho'
import { ESPERA_RECALCULO_MS, chaveDoPedido, formularioValido } from './recalculo'
import { paraEditavel, regraVazia, type RegraEditavel } from './regime'
import { avisoDeSerie, lerSerieIndisponivel, type AvisoDeSerie } from './serieIndisponivel'
import './App.css'

/** Critérios do título escolhidos, mas sem as regras: elas ainda precisam vir do servidor. */
const faltamRegras = (r: Rascunho) => r.regimeProprio && r.regras.length === 0

/** O processo como texto comparável: mudar só ele não muda o cálculo, mas muda o PDF. */
const textoDoProcesso = (processo: ProcessoRequest) => JSON.stringify(paraProcesso(processo) ?? null)

interface Calculo {
  /** Muda a cada clique em Calcular, para o demonstrativo recomeçar do topo; o recálculo automático o mantém. */
  numero: number
  pedido: CalculoRequest
  /** `chaveDoPedido(pedido)`: comparada com a do formulário, diz se o demonstrativo está desatualizado. */
  chave: string
  resultado: Resultado
  /**
   * Se o resultado é o de um cálculo salvo, refeito com o snapshot: ao abri-lo ou ao salvar. O
   * recálculo automático cria outro `Calculo`, sem ele, porque usa as séries de hoje.
   */
  salvo?: { id: string; processo: string }
}

/** De onde veio o caso que está no formulário, para o pé do formulário dizer. */
interface Origem {
  modo: AcaoSalvo | 'salvo'
  titulo: string
  criadoEm: Date
  /** O regime salvo diante do padrão de hoje: um padrão antigo pede aviso quando os índices são os de hoje. */
  regime: RegimeSalvo
}

/** O caso que o "Limpar" ou a abertura de um cálculo salvo tirou da tela, para o "Desfazer" devolver. */
interface Troca {
  motivo: 'limpar' | 'abrir'
  rascunho: Rascunho
  calculo: Calculo | null
  origem: Origem | null
  /** O formulário que entrou no lugar; editá-lo encerra o "Desfazer" de uma abertura. */
  depois: Rascunho
}

function App() {
  const sessao = useSessao()
  const [saindo, setSaindo] = useState(false)
  const [erroSair, setErroSair] = useState<string | null>(null)

  /** Sem resposta do servidor, a sessão continua e o erro fica escrito no topo. */
  async function sairDaSessao() {
    if (!sessao) return
    setErroSair(null)
    setSaindo(true)
    try {
      await sessao.sair()
    } catch (e) {
      setErroSair(`Não foi possível sair. ${e instanceof Error ? e.message : String(e)}`)
      setSaindo(false)
    }
  }
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
  const [troca, setTroca] = useState<Troca | null>(null)
  const [calculo, setCalculo] = useState<Calculo | null>(null)
  /** O `calculo` de agora, para quem o lê depois de esperar o servidor: um recálculo pode ter chegado no meio. */
  const calculoVigente = useRef(calculo)
  useEffect(() => {
    calculoVigente.current = calculo
  }, [calculo])
  const [origem, setOrigem] = useState<Origem | null>(null)
  const [listaAberta, setListaAberta] = useState(false)
  const [salvarAberta, setSalvarAberta] = useState(false)
  /** O regime padrão, buscado uma vez para reconhecê-lo num cálculo salvo; nulo se a busca falhou. */
  const regimePadrao = useRef<Promise<Regime | null> | null>(null)
  /** Erro do último cálculo, com a chave do formulário que o provocou: editar de novo o tira da tela. */
  const [erro, setErro] = useState<{ chave: string; mensagem: string; serie: AvisoDeSerie | null } | null>(null)
  const [carregando, setCarregando] = useState(false)
  /** Chave do formulário que, depois do primeiro cálculo, ficou com campo inválido e não pôde ser recalculado. */
  const [invalidoEm, setInvalidoEm] = useState<string | null>(null)
  const formulario = useRef<HTMLFormElement>(null)
  const pe = useRef<HTMLDivElement>(null)
  useAlturaFixa(pe, '--altura-pe', formulario)
  /** O cálculo que está no servidor; um mais novo cancela o anterior, que chegaria fora de ordem. */
  const emCurso = useRef<AbortController | null>(null)
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  /** Chave do formulário com um recálculo esperando a pausa na digitação. */
  const agendadaPara = useRef<string | null>(null)
  const [buscandoRegime, setBuscandoRegime] = useState(() => faltamRegras(inicio.rascunho))
  /** Muda quando o formulário é trocado inteiro, para respostas que chegam depois serem descartadas. */
  const versaoDoFormulario = useRef(0)

  const ritosAtivos = comPensao && separarRitos

  /**
   * Parcelas da pensão vencidas há mais de dois anos: só avisa, e o cálculo segue com elas. As
   * quitadas vêm do último demonstrativo: o formulário não sabe o salário mínimo de cada mês.
   */
  const antigas = comPensao
    ? parcelasAntigas(
        { primeiroMes: pensao.primeiroMes, ultimoMes: pensao.ultimoMes, diaVencimento: Number(pensao.diaVencimento) },
        ritosAtivos ? pensao.ajuizamento : null,
        dataBase,
        calculo ? competenciasQuitadas(calculo.resultado.linhas) : undefined,
      )
    : null
  const avisoPrescricao = antigas && textoDoAviso(antigas)

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
    } catch (e) {
      // Com a sessão vencida, o login abre por cima e a busca fica pendente: o recálculo segue
      // esperando, e ela recomeça quando a pessoa entra de novo.
      if (e instanceof ErroDaApi && e.status === 401) return
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

  // De volta depois de a sessão expirar: a busca das regras que levou 401 recomeça.
  const retomarRegras = useEffectEvent(() => {
    if (buscandoRegime && faltamRegras(rascunho)) void buscarRegras()
  })
  const geracao = sessao?.geracao ?? 0
  useEffect(() => {
    if (geracao > 0) retomarRegras()
  }, [geracao])

  /**
   * O formulário como pedido de cálculo, sem o processo, que só identifica o PDF. Refeito só quando
   * um dado do cálculo muda; se algum valor nem pode ser lido, fica a mensagem, e a chave vazia.
   */
  const atual = useMemo(() => {
    try {
      const pedido = paraPedido({ dataBase, aplicarArt523, parcelas, comPensao, pensao, separarRitos, regimeProprio, nomeRegime, regras })
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
  const erroAtual = erro && (calculo === null || erro.chave === chaveAtual) ? erro : null
  const mensagemDeErro = erroAtual?.mensagem ?? null
  /** Falta o valor de uma série no mês: em vez da mensagem do servidor, o aviso diz se só não saiu ainda. */
  const avisoSerie = erroAtual?.serie ?? null

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
      const serie = e instanceof ErroDaApi ? lerSerieIndisponivel(e.problema) : null
      setErro({
        chave,
        mensagem: e instanceof Error ? e.message : String(e),
        serie: serie && pedido ? avisoDeSerie(serie, pedido.dataBase) : null,
      })
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

  const [apple] = useState(() => ehApple(navigator.platform))
  const atalho = rotuloDoAtalho(apple)
  // Ctrl+Enter calcula de qualquer ponto da página, como o botão: o requestSubmit valida o formulário
  // antes. Com uma janela modal aberta (Cálculos salvos, Salvar), o atalho fica com ela.
  useEffect(() => {
    const aoTeclar = (evento: KeyboardEvent) => {
      if (!ehAtalhoDeCalcular(evento, apple) || document.querySelector('dialog[open]')) return
      evento.preventDefault()
      formulario.current?.requestSubmit()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [apple])

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

  /**
   * Recua a data-base para a que não depende do índice que falta e calcula. O requestSubmit espera
   * o React aplicar a data nova: o clique é um evento discreto, que o React aplica antes do timeout.
   */
  function usarDataBase(novaData: string) {
    alterar('dataBase', novaData)
    setTimeout(() => formulario.current?.requestSubmit())
  }

  /** O botão que acompanha o erro, na faixa do demonstrativo ou abaixo do formulário. */
  const sugerida = avisoSerie?.dataBaseSugerida
  const acaoDoErro: AcaoDaSituacao | undefined = sugerida
    ? { rotulo: `Usar a data-base ${data(sugerida)}`, aoClicar: () => usarDataBase(sugerida) }
    : mensagemDeErro
      ? { rotulo: 'Tentar de novo', aoClicar: tentarDeNovo }
      : undefined

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
    setTroca({ motivo: 'limpar', rascunho, calculo, origem, depois: rascunhoVazio() })
    setRestauradoDe(null)
    setOrigem(null)
    trocarCaso(rascunhoVazio(), null)
    apagarRascunho(armazenamentoDoNavegador(), aba)
  }

  function desfazer() {
    if (!troca) return
    trocarCaso(troca.rascunho, troca.calculo)
    setOrigem(troca.origem)
    setTroca(null)
  }

  // O "Desfazer" só vale enquanto o caso que entrou segue intocado: depois, sobrescreveria o caso novo.
  const podeDesfazer =
    troca !== null && (troca.motivo === 'limpar' ? emBranco && calculo === null : rascunho === troca.depois)

  function buscarPadrao(): Promise<Regime | null> {
    regimePadrao.current ??= buscarRegimePadrao().catch(() => {
      // Sem o padrão, o cálculo salvo abre com as regras por extenso; a próxima abertura tenta de novo.
      regimePadrao.current = null
      return null
    })
    return regimePadrao.current
  }

  /**
   * Traz um cálculo salvo para o formulário, no lugar do caso que estava nele, com "Desfazer".
   * Abrir mostra o demonstrativo do snapshot; atualizar também, mas com a data-base avançada, e o
   * recálculo automático refaz o demonstrativo com as séries de hoje; duplicar traz só o formulário.
   *
   * @param sinal abortado quando a janela fecha antes da resposta: quem desistiu não perde o caso da tela
   * @throws Error se o cálculo não pôde ser buscado; a janela mostra a mensagem
   */
  async function escolherSalvo(acao: AcaoSalvo, resumo: ResumoCalculo, sinal: AbortSignal) {
    const [salvo, padrao] = await Promise.all([reabrirCalculo(resumo.id, sinal), buscarPadrao()])
    if (sinal.aborted) return
    // O formulário não muda com a janela aberta, mas o demonstrativo pode: um recálculo chega durante a espera.
    const calculo = calculoVigente.current
    const aberto = paraRascunho(salvo.calculo, padrao)
    let novoCalculo: Calculo | null = null
    if (acao !== 'duplicar') {
      // O pedido sai do formulário, e não do servidor, para a chave bater com a do formulário aberto.
      const pedido = paraPedido(aberto)
      novoCalculo = {
        numero: (calculo?.numero ?? 0) + 1,
        pedido,
        chave: chaveDoPedido(pedido),
        resultado: salvo.demonstrativo,
        salvo: { id: salvo.id, processo: textoDoProcesso(aberto.processo) },
      }
    }
    const depois = acao === 'atualizar' ? { ...aberto, dataBase: novaDataBase(aberto.dataBase) ?? aberto.dataBase } : aberto
    // Logo depois de um "Limpar", o caso a devolver é o que ele tirou da tela, e não o formulário em branco.
    const anterior =
      podeDesfazer && troca.motivo === 'limpar' ? troca : !emBranco || calculo ? { rascunho, calculo, origem } : null
    setTroca(anterior && { motivo: 'abrir', rascunho: anterior.rascunho, calculo: anterior.calculo, origem: anterior.origem, depois })
    setRestauradoDe(null)
    trocarCaso(depois, novoCalculo)
    setOrigem({
      modo: acao,
      titulo: salvo.titulo,
      criadoEm: new Date(salvo.criadoEm),
      regime: regimeSalvo(salvo.calculo, padrao),
    })
    setListaAberta(false)
  }

  /**
   * Salva o cálculo que está na tela. O servidor refaz o cálculo com as séries de hoje, as mesmas
   * do demonstrativo, e devolve o refeito com o snapshot, que passa a ser o da tela.
   *
   * @throws Error se não deu para salvar; a janela mostra a mensagem
   */
  async function salvar(titulo: string) {
    if (!calculo) return
    const enviado = calculo
    const salvo = await salvarCalculo(titulo, { ...enviado.pedido, processo: paraProcesso(processo) })
    const deste = { id: salvo.id, processo: textoDoProcesso(processo) }
    // Se o demonstrativo mudou durante o salvamento, o salvo é o anterior, e o da tela não é ele.
    setCalculo((atual) => (atual === enviado ? { ...atual, resultado: salvo.demonstrativo, salvo: deste } : atual))
    // Um regime antigo que veio de outro salvo continua antigo neste: o aviso segue com ele.
    setOrigem({ modo: 'salvo', titulo: salvo.titulo, criadoEm: new Date(salvo.criadoEm), regime: origem?.regime ?? 'padrao' })
    setRestauradoDe(null)
    setSalvarAberta(false)
  }

  /** O cálculo salvo na tela, se nada mudou desde que foi aberto ou salvo, nem o processo. */
  const idSalvo =
    calculo?.salvo && !desatualizado && calculo.salvo.processo === textoDoProcesso(processo) ? calculo.salvo.id : undefined

  const descricaoDaOrigem = (o: Origem) => {
    const nome = `“${o.titulo}”, de ${dataHora(o.criadoEm)}`
    if (idSalvo) return `Cálculo salvo ${nome}, com os índices daquele dia.`
    if (o.modo === 'duplicar') return `Cópia do cálculo salvo ${nome}.`
    if (o.modo === 'atualizar') return `Atualização do cálculo salvo ${nome}, com os índices de hoje. Salve para guardá-la.`
    return `Alterado a partir do cálculo salvo ${nome}: os números usam os índices de hoje. Salve para guardar esta versão.`
  }

  /**
   * Com os índices de hoje, as regras de um regime padrão antigo deixam de fora o que mudou depois
   * do salvamento. O formulário as mantém (é o regime do cálculo que foi aos autos), mas avisa.
   */
  const avisoDoRegime =
    origem && regimeProprio && !idSalvo
      ? origem.regime === 'padrao-anterior'
        ? 'O regime padrão do TJMG mudou desde que este cálculo foi salvo, e o formulário segue as regras daquela época como critérios do título. Confira o regime antes de levar estes números aos autos.'
        : origem.regime === 'desconhecido'
          ? 'Não foi possível buscar o regime padrão de hoje, e o formulário segue as regras salvas como critérios do título. Confira o regime antes de levar estes números aos autos.'
          : null
      : null

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
          <div>
            <h1>Debeatur</h1>
            <p>Execução de alimentos, do título ao demonstrativo.</p>
          </div>
          <div className="acoes-topo">
            <button type="button" className="secundario" onClick={() => setListaAberta(true)}>
              Cálculos salvos
            </button>
            {sessao && (
              <button
                type="button"
                className={saindo ? 'secundario esperando' : 'secundario'}
                aria-describedby="sessao-email"
                disabled={saindo}
                onClick={() => void sairDaSessao()}
              >
                {saindo ? 'Saindo…' : 'Sair'}
              </button>
            )}
          </div>
          {sessao && (
            <p className="sessao-email" id="sessao-email">
              {sessao.email}
            </p>
          )}
          {erroSair && (
            <p className="erro" role="alert">
              {erroSair}
            </p>
          )}
        </header>

        <form ref={formulario} onSubmit={enviar}>
          <section className="bloco" aria-labelledby="bloco-pensao">
            <h2 id="bloco-pensao">Pensão</h2>
            <label className="checkbox">
              <input type="checkbox" checked={comPensao} onChange={(e) => usarPensao(e.target.checked)} />
              Alimentos fixados em percentual do salário mínimo
            </label>
            {comPensao && <EditorPensao pensao={pensao} onPensao={(p) => alterar('pensao', p)} />}
            {/* A região fica sempre na página, para o leitor de tela anunciar o aviso quando ele aparece. */}
            <div role="status">
              {avisoPrescricao && (
                <p className="aviso aviso-prescricao">
                  <strong>{avisoPrescricao.fato}</strong> {avisoPrescricao.detalhe}
                </p>
              )}
            </div>
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
            {/* O que fica de fora muda o valor, então fica à vista; só o porquê vai para o "Saiba mais". */}
            <Dica
              id="dica-data-base"
              curta="O débito é atualizado até esta data: parada no fim do mês anterior, ela deixa de fora a correção e os juros do mês corrente."
            >
              Ela começa no fim do mês anterior porque o índice de {mesFechado} só sai por volta do dia 10. Depois que ele
              sair, avance a data-base.
            </Dica>

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

          <div ref={pe} className="enviar">
            <div className="botoes">
              {/* Depois do primeiro cálculo, o andamento aparece no demonstrativo, e o botão não pisca a cada edição. */}
              <button
                type="submit"
                className="esperando"
                disabled={carregando && !calculo}
                aria-keyshortcuts={atalho.aria}
              >
                {carregando && !calculo ? 'Calculando…' : 'Calcular'}
              </button>
              <button type="button" className="secundario" onClick={limpar} disabled={emBranco && !calculo}>
                Limpar
              </button>
              {/* O leitor de tela já anuncia o atalho pelo aria-keyshortcuts do Calcular. */}
              <span className="atalho" aria-hidden="true">
                <kbd>{atalho.tela}</kbd> calcula de qualquer campo
              </span>
            </div>
            {calculo && <p className="dica">O demonstrativo se atualiza sozinho a cada alteração.</p>}
            <p className="aviso" role="status">
              {avisoDoRegime}
            </p>
            <p className="dica rascunho" role="status">
              {podeDesfazer && troca.motivo === 'limpar' ? (
                <>
                  Formulário limpo.{' '}
                  <button type="button" className="secundario" onClick={desfazer}>
                    Desfazer
                  </button>
                </>
              ) : (
                !emBranco && (
                  <>
                    {origem && `${descricaoDaOrigem(origem)} `}
                    {podeDesfazer && (
                      <>
                        <button type="button" className="secundario" onClick={desfazer}>
                          Desfazer
                        </button>{' '}
                      </>
                    )}
                    {restauradoDe && `Caso restaurado do rascunho de ${dataHora(restauradoDe)}. `}
                    O formulário fica guardado neste navegador; em computador compartilhado, use Limpar ao terminar. O
                    Limpar não apaga os cálculos salvos, que ficam no servidor.
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
          <p className={avisoSerie?.recente ? 'erro pendente' : 'erro'} role="alert">
            {avisoSerie ? (
              <>
                <strong>{avisoSerie.fato}</strong> {avisoSerie.detalhe}
                {sugerida && (
                  <>
                    {' '}
                    <button type="button" className="secundario" onClick={() => usarDataBase(sugerida)}>
                      Usar a data-base {data(sugerida)}
                    </button>
                  </>
                )}
              </>
            ) : (
              mensagemDeErro
            )}
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
                : avisoSerie
                  ? `${avisoSerie.fato} ${avisoSerie.detalhe} Os valores abaixo são do cálculo anterior.`
                  : mensagemDeErro
                    ? `O cálculo com os dados atuais falhou: ${mensagemDeErro.replace(/\.$/, '')}. Os valores abaixo são do cálculo anterior.`
                    : null
            }
            acao={invalido ? undefined : acaoDoErro}
            idSalvo={idSalvo}
            aoSalvar={() => setSalvarAberta(true)}
          />
        )}
      </div>

      <ListaCalculosSalvos aberta={listaAberta} aoFechar={() => setListaAberta(false)} aoEscolher={escolherSalvo} />
      {calculo && (
        <SalvarCalculo
          aberta={salvarAberta}
          sugestao={tituloPadrao({ dataBase: calculo.pedido.dataBase, processo: paraProcesso(processo) })}
          aoFechar={() => setSalvarAberta(false)}
          aoSalvar={salvar}
        />
      )}
    </main>
  )
}

export default App
