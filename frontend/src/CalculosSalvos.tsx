import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Janela } from './Janela'
import { excluirCalculo, listarCalculos, renomearCalculo, type ResumoCalculo } from './api'
import { filtrarCalculos, novaDataBase } from './calculoSalvo'
import { MensagemErro } from './Campo'
import { useSessao } from './sessao'
import { data, dataHora, moeda } from './formato'

const mensagem = (e: unknown) => (e instanceof Error ? e.message : String(e))

export type AcaoSalvo = 'abrir' | 'atualizar' | 'duplicar'

const ROTULOS: Record<AcaoSalvo, { botao: string; andamento: string }> = {
  abrir: { botao: 'Abrir', andamento: 'Abrindo…' },
  atualizar: { botao: 'Atualizar', andamento: 'Atualizando…' },
  duplicar: { botao: 'Duplicar', andamento: 'Duplicando…' },
}

const ID_BUSCA = 'busca-calculos'

/** O e-mail que quebra a linha antes do @, e não no meio do nome ou do domínio. */
function EmailQuebravel({ email }: { email: string }) {
  const arroba = email.indexOf('@')
  if (arroba <= 0) return email
  return (
    <>
      {email.slice(0, arroba)}
      <wbr />
      {email.slice(arroba)}
    </>
  )
}

interface PropsLista {
  aberta: boolean
  aoFechar: () => void
  /**
   * Traz o cálculo para o formulário; uma falha fica escrita na janela, que segue aberta. O sinal é
   * abortado se a janela fechar antes da resposta, e aí o formulário não muda.
   */
  aoEscolher: (acao: AcaoSalvo, resumo: ResumoCalculo, sinal: AbortSignal) => Promise<void>
  /** O cálculo já trocou de título no servidor; a tela que o mostra acompanha. */
  aoRenomear: (resumo: ResumoCalculo) => void
  /** O cálculo já foi excluído no servidor; a tela que o mostra deixa de tratá-lo como salvo. */
  aoExcluir: (id: string) => void
}

/** A lista dos cálculos salvos, buscada de novo a cada vez que a janela abre. */
export function ListaCalculosSalvos({ aberta, aoFechar, ...acoes }: PropsLista) {
  return (
    <Janela id="titulo-calculos-salvos" titulo="Cálculos salvos" aberta={aberta} aoFechar={aoFechar}>
      <ConteudoLista {...acoes} />
    </Janela>
  )
}

type EmCurso = { id: string; acao: AcaoSalvo | 'renomear' | 'excluir' }

/** Monta a cada abertura da janela: a lista sai do servidor de novo, e o erro da vez anterior some. */
function ConteudoLista({ aoEscolher, aoRenomear, aoExcluir }: Omit<PropsLista, 'aberta' | 'aoFechar'>) {
  const [lista, setLista] = useState<ResumoCalculo[] | null>(null)
  /** A lista não veio do servidor: sem ela, não há o que buscar. */
  const [erroLista, setErroLista] = useState<string | null>(null)
  /** Uma ação numa linha falhou; a lista e a busca continuam. */
  const [erro, setErro] = useState<string | null>(null)
  /** O título em edição ficou vazio; a mensagem fica no campo, como nos do formulário. */
  const [erroTitulo, setErroTitulo] = useState<string | null>(null)
  const [emCurso, setEmCurso] = useState<EmCurso | null>(null)
  const [busca, setBusca] = useState('')
  /** A linha com o título em edição, e o texto do campo. */
  const [editando, setEditando] = useState<{ id: string; titulo: string } | null>(null)
  /** A linha que pede confirmação para excluir. */
  const [confirmando, setConfirmando] = useState<string | null>(null)
  /** O que acabou de mudar na lista, para o leitor de tela; a busca diz quantos achou. */
  const [feito, setFeito] = useState<string | null>(null)
  /** Abortado quando a janela fecha (Esc, Fechar): uma abertura ainda no servidor não troca mais o caso. */
  const sinal = useRef<AbortSignal | null>(null)
  /** O id do controle que recebe o foco depois de a linha mudar de forma (editar, confirmar, excluir). */
  const focar = useRef<string | null>(null)

  useEffect(() => {
    const controle = new AbortController()
    sinal.current = controle.signal
    return () => controle.abort()
  }, [])

  // Se o controle não está mais na tela (a busca escondeu a linha, a lista esvaziou), o foco vai
  // para a busca e, sem ela, para o Fechar, em vez de cair no corpo da janela.
  const raiz = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (focar.current === null) return
    const alvo =
      document.getElementById(focar.current) ??
      document.getElementById(ID_BUSCA) ??
      raiz.current?.closest('dialog')?.querySelector<HTMLElement>('.cabecalho-janela button')
    // Como no `Campo`: se o foco já está no alvo (Enter no título vazio), sai e volta, senão o leitor
    // de tela não relê o campo com a mensagem de erro.
    if (alvo && document.activeElement === alvo) alvo.blur()
    alvo?.focus()
    focar.current = null
  })

  // De novo quando a pessoa entra outra vez: a lista que levou 401 com a sessão vencida aparece.
  const sessao = useSessao()
  const geracao = sessao?.geracao ?? 0
  /** O admin vê os cálculos de todos e precisa saber de quem é cada um; o membro só vê os dele. */
  const comAutor = sessao?.papel === 'ADMIN'
  useEffect(() => {
    let vale = true
    listarCalculos().then(
      (l) => {
        if (!vale) return
        setErroLista(null)
        setLista(l)
      },
      (e) => vale && setErroLista(mensagem(e)),
    )
    return () => {
      vale = false
    }
  }, [geracao])

  async function escolher(acao: AcaoSalvo, resumo: ResumoCalculo) {
    const s = sinal.current
    if (!s) return
    setErro(null)
    setEmCurso({ id: resumo.id, acao })
    try {
      await aoEscolher(acao, resumo, s)
    } catch (e) {
      if (s.aborted) return
      setErro(`Não foi possível ${ROTULOS[acao].botao.toLowerCase()} “${resumo.titulo}”. ${mensagem(e)}`)
    } finally {
      setEmCurso(null)
    }
  }

  function comecarEdicao(resumo: ResumoCalculo) {
    setErro(null)
    setFeito(null)
    setConfirmando(null)
    setErroTitulo(null)
    setEditando({ id: resumo.id, titulo: resumo.titulo })
    focar.current = `titulo-${resumo.id}`
  }

  function cancelarEdicao(id: string) {
    setEditando(null)
    setErro(null)
    setErroTitulo(null)
    focar.current = `renomear-${id}`
  }

  async function renomear(evento: FormEvent, resumo: ResumoCalculo) {
    evento.preventDefault()
    if (!editando) return
    const titulo = editando.titulo.trim()
    if (titulo === resumo.titulo) return cancelarEdicao(resumo.id)
    if (!titulo) {
      setErroTitulo('Informe o novo título, como “Execução de alimentos da Maria”.')
      focar.current = `titulo-${resumo.id}`
      return
    }
    setErroTitulo(null)
    setErro(null)
    setEmCurso({ id: resumo.id, acao: 'renomear' })
    try {
      const novo = await renomearCalculo(resumo.id, titulo)
      setLista((l) => l && l.map((r) => (r.id === novo.id ? novo : r)))
      setEditando(null)
      setFeito(`“${resumo.titulo}” agora se chama “${novo.titulo}”.`)
      focar.current = `renomear-${resumo.id}`
      aoRenomear(novo)
    } catch (e) {
      setErro(`Não foi possível renomear “${resumo.titulo}”. ${mensagem(e)}`)
    } finally {
      setEmCurso(null)
    }
  }

  function pedirExclusao(resumo: ResumoCalculo) {
    setErro(null)
    setFeito(null)
    setEditando(null)
    setConfirmando(resumo.id)
    // O foco vai para o Cancelar: Enter logo depois não apaga nada.
    focar.current = `cancelar-exclusao-${resumo.id}`
  }

  function cancelarExclusao(id: string) {
    setConfirmando(null)
    setErro(null)
    focar.current = `excluir-${id}`
  }

  async function excluir(resumo: ResumoCalculo) {
    setErro(null)
    setEmCurso({ id: resumo.id, acao: 'excluir' })
    try {
      await excluirCalculo(resumo.id)
      setLista((l) => l && l.filter((r) => r.id !== resumo.id))
      setConfirmando(null)
      setFeito(`“${resumo.titulo}” foi excluído.`)
      focar.current = ID_BUSCA
      aoExcluir(resumo.id)
    } catch (e) {
      setErro(`Não foi possível excluir “${resumo.titulo}”. ${mensagem(e)}`)
    } finally {
      setEmCurso(null)
    }
  }

  /** Esc no campo do título desiste da edição, em vez de fechar a janela. */
  function teclaNoTitulo(evento: KeyboardEvent, id: string) {
    if (evento.key !== 'Escape') return
    evento.preventDefault()
    cancelarEdicao(id)
  }

  const achados = lista && filtrarCalculos(lista, busca)
  const travado = emCurso !== null
  const andamento = (id: string, acao: EmCurso['acao']) => emCurso?.id === id && emCurso.acao === acao

  return (
    <div ref={raiz} className="lista-salvos">
      <p className="dica">
        Abrir mostra o demonstrativo com os índices do dia em que foi salvo. Atualizar leva a data-base ao fim do mês
        anterior, estende até lá a pensão que ainda corria e recalcula com os índices de hoje. Duplicar traz só o formulário, para um caso parecido.
      </p>
      {/* Já na abertura, para receber o foco; some só quando não há o que buscar. */}
      {!erroLista && lista?.length !== 0 && (
        <div className="busca-salvos">
          <label htmlFor={ID_BUSCA}>Buscar</label>
          <input
            id={ID_BUSCA}
            type="search"
            data-foco-inicial
            aria-describedby="dica-busca"
            autoComplete="off"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <p className="dica" id="dica-busca">
            Pelo título, pelo regime{comAutor && ', pelo e-mail de quem salvou'} ou pelo número do processo, com ou sem
            pontos e traço.
          </p>
        </div>
      )}
      {(erroLista ?? erro) && (
        <p className="erro" role="alert">
          {erroLista ?? erro}
        </p>
      )}
      <p className="status-salvos" role="status">
        {feito}
        {feito && achados && busca.trim() && ' '}
        {achados && busca.trim() && `${achados.length} de ${lista?.length} cálculos com “${busca.trim()}”.`}
      </p>
      {lista === null && !erroLista && <p role="status">Carregando os cálculos salvos…</p>}
      {lista?.length === 0 && (
        <p className="lista-vazia">Nenhum cálculo salvo. Depois de calcular, use Salvar no demonstrativo.</p>
      )}
      {lista && lista.length > 0 && achados?.length === 0 && (
        <p className="lista-vazia">
          Nenhum cálculo com “{busca.trim()}”. Confira a grafia ou busque por parte do número do processo.
        </p>
      )}
      {achados && achados.length > 0 && (
        <div className="rolagem" tabIndex={0} role="region" aria-label="Lista de cálculos salvos">
          <table className="tabela-salvos" role="table">
            <caption className="sr-only">Cálculos salvos, do mais recente para o mais antigo</caption>
            <thead role="rowgroup">
              <tr role="row">
                <th scope="col" role="columnheader">Título</th>
                <th scope="col" role="columnheader">Processo</th>
                <th scope="col" role="columnheader">Data-base</th>
                <th scope="col" role="columnheader">Total</th>
                <th scope="col" role="columnheader">Salvo em</th>
                {comAutor && (
                  <th scope="col" role="columnheader" className="autor-salvo">
                    Salvo por
                  </th>
                )}
                <th scope="col" role="columnheader">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody role="rowgroup">
              {achados.map((r) => (
                <tr key={r.id} role="row">
                  {/* O cabeçalho da linha é lido em cada célula: só o título e o regime, sem controles. */}
                  <th scope="row" role="rowheader">
                    {r.titulo}
                    <span className="nota">{r.regime}</span>
                  </th>
                  <td className="processo-salvo" role="cell" data-rotulo="Processo">
                    {r.processo || (
                      <>
                        <span aria-hidden="true">—</span>
                        <span className="sr-only">Sem número</span>
                      </>
                    )}
                  </td>
                  {/* Os rótulos são os do cabeçalho, para a tela estreita, onde cada linha vira um cartão. */}
                  <td role="cell" data-rotulo="Data-base">{data(r.dataBase)}</td>
                  <td role="cell" data-rotulo="Total">{moeda(r.total)}</td>
                  <td role="cell" data-rotulo="Salvo em">{dataHora(new Date(r.criadoEm))}</td>
                  {comAutor && (
                    <td className="autor-salvo" role="cell" data-rotulo="Salvo por">
                      {r.autor ? (
                        // A linha quebra antes do @, e não no meio do nome ou do domínio.
                        <EmailQuebravel email={r.autor} />
                      ) : (
                        <>
                          <span aria-hidden="true">—</span>
                          <span className="sr-only">Salvo antes dos usuários</span>
                        </>
                      )}
                    </td>
                  )}
                  <td className="acoes-salvo" role="cell">
                    <div className="acoes-principais">
                      {(['abrir', 'atualizar', 'duplicar'] as const)
                        // Um cálculo que já vai até o fim do mês anterior não tem o que atualizar.
                        .filter((acao) => acao !== 'atualizar' || novaDataBase(r.dataBase) !== null)
                        .map((acao) => {
                          const esta = andamento(r.id, acao)
                          return (
                            <button
                              key={acao}
                              type="button"
                              // Só o botão que espera o servidor mostra o andamento; os outros, só travados.
                              className={[acao !== 'abrir' && 'secundario', esta && 'esperando'].filter(Boolean).join(' ') || undefined}
                              aria-label={`${ROTULOS[acao].botao} ${r.titulo}`}
                              disabled={travado}
                              onClick={() => void escolher(acao, r)}
                            >
                              {esta ? ROTULOS[acao].andamento : ROTULOS[acao].botao}
                            </button>
                          )
                        })}
                    </div>
                    {editando?.id === r.id ? (
                      <form className="renomear-salvo" onSubmit={(e) => void renomear(e, r)}>
                        <label className="sr-only" htmlFor={`titulo-${r.id}`}>
                          Novo título de “{r.titulo}”
                        </label>
                        {erroTitulo && <MensagemErro id={`titulo-${r.id}-erro`}>{erroTitulo}</MensagemErro>}
                        <input
                          id={`titulo-${r.id}`}
                          maxLength={200}
                          aria-invalid={erroTitulo ? true : undefined}
                          aria-describedby={erroTitulo ? `titulo-${r.id}-erro` : undefined}
                          value={editando.titulo}
                          onChange={(e) => {
                            setEditando({ id: r.id, titulo: e.target.value })
                            // Como nos campos do formulário: corrigido, o erro some.
                            if (e.target.value.trim()) setErroTitulo(null)
                          }}
                          onKeyDown={(e) => teclaNoTitulo(e, r.id)}
                        />
                        <div className="acoes-linha">
                          <button type="submit" className={andamento(r.id, 'renomear') ? 'esperando' : undefined} disabled={travado}>
                            {andamento(r.id, 'renomear') ? 'Salvando…' : 'Salvar título'}
                          </button>
                          <button type="button" className="secundario" disabled={travado} onClick={() => cancelarEdicao(r.id)}>
                            Cancelar
                          </button>
                        </div>
                      </form>
                    ) : confirmando === r.id ? (
                      <div className="confirmar-exclusao" role="group" aria-labelledby={`pergunta-${r.id}`}>
                        <p id={`pergunta-${r.id}`}>
                          <strong>Excluir este cálculo?</strong> Ele sai da lista com os índices guardados, e não dá
                          para desfazer.
                        </p>
                        <div className="acoes-linha">
                          <button
                            type="button"
                            className={andamento(r.id, 'excluir') ? 'esperando' : undefined}
                            disabled={travado}
                            onClick={() => void excluir(r)}
                          >
                            {andamento(r.id, 'excluir') ? 'Excluindo…' : 'Excluir'}
                          </button>
                          <button
                            type="button"
                            id={`cancelar-exclusao-${r.id}`}
                            className="secundario"
                            disabled={travado}
                            onClick={() => cancelarExclusao(r.id)}
                          >
                            Cancelar
                          </button>
                        </div>
                      </div>
                    ) : (
                      <div className="acoes-secundarias">
                          <button
                            type="button"
                            id={`renomear-${r.id}`}
                            className="link"
                            aria-label={`Renomear ${r.titulo}`}
                            disabled={travado}
                            onClick={() => comecarEdicao(r)}
                          >
                            Renomear
                          </button>
                          <button
                            type="button"
                            id={`excluir-${r.id}`}
                            className="link"
                            aria-label={`Excluir ${r.titulo}`}
                            disabled={travado}
                            onClick={() => pedirExclusao(r)}
                          >
                            Excluir
                          </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

interface PropsSalvar {
  aberta: boolean
  /** O título que o servidor dá se o campo ficar vazio. */
  sugestao: string
  aoFechar: () => void
  /** Salva; uma falha fica escrita na janela, que segue aberta. */
  aoSalvar: (titulo: string) => Promise<void>
}

export function SalvarCalculo({ aberta, sugestao, aoFechar, aoSalvar }: PropsSalvar) {
  return (
    <Janela id="titulo-salvar" titulo="Salvar cálculo" aberta={aberta} aoFechar={aoFechar}>
      <FormularioSalvar sugestao={sugestao} aoSalvar={aoSalvar} />
    </Janela>
  )
}

function FormularioSalvar({ sugestao, aoSalvar }: Pick<PropsSalvar, 'sugestao' | 'aoSalvar'>) {
  const [titulo, setTitulo] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErro(null)
    setSalvando(true)
    try {
      await aoSalvar(titulo)
    } catch (e) {
      setErro(`Não foi possível salvar. ${mensagem(e)}`)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <form className="formulario-salvar" onSubmit={enviar}>
      <label>
        Título
        <input
          data-foco-inicial
          maxLength={200}
          placeholder={sugestao}
          aria-describedby="dica-titulo"
          value={titulo}
          onChange={(e) => setTitulo(e.target.value)}
        />
      </label>
      <p className="dica" id="dica-titulo">
        Vazio, fica “{sugestao}”. Os índices de hoje são guardados com o cálculo, que abre sempre com os mesmos
        números.
      </p>
      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}
      <div className="botoes">
        <button type="submit" className="esperando" disabled={salvando}>
          {salvando ? 'Salvando…' : 'Salvar'}
        </button>
      </div>
    </form>
  )
}
