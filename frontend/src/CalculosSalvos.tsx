import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Janela } from './Janela'
import { listarCalculos, type ResumoCalculo } from './api'
import { novaDataBase } from './calculoSalvo'
import { data, dataHora, moeda } from './formato'

const mensagem = (e: unknown) => (e instanceof Error ? e.message : String(e))

export type AcaoSalvo = 'abrir' | 'atualizar' | 'duplicar'

const ROTULOS: Record<AcaoSalvo, { botao: string; andamento: string }> = {
  abrir: { botao: 'Abrir', andamento: 'Abrindo…' },
  atualizar: { botao: 'Atualizar', andamento: 'Atualizando…' },
  duplicar: { botao: 'Duplicar', andamento: 'Duplicando…' },
}

interface PropsLista {
  aberta: boolean
  aoFechar: () => void
  /**
   * Traz o cálculo para o formulário; uma falha fica escrita na janela, que segue aberta. O sinal é
   * abortado se a janela fechar antes da resposta, e aí o formulário não muda.
   */
  aoEscolher: (acao: AcaoSalvo, resumo: ResumoCalculo, sinal: AbortSignal) => Promise<void>
}

/** A lista dos cálculos salvos, buscada de novo a cada vez que a janela abre. */
export function ListaCalculosSalvos({ aberta, aoFechar, aoEscolher }: PropsLista) {
  return (
    <Janela id="titulo-calculos-salvos" titulo="Cálculos salvos" aberta={aberta} aoFechar={aoFechar}>
      <ConteudoLista aoEscolher={aoEscolher} />
    </Janela>
  )
}

/** Monta a cada abertura da janela: a lista sai do servidor de novo, e o erro da vez anterior some. */
function ConteudoLista({ aoEscolher }: Pick<PropsLista, 'aoEscolher'>) {
  const [lista, setLista] = useState<ResumoCalculo[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [emCurso, setEmCurso] = useState<{ id: string; acao: AcaoSalvo } | null>(null)
  /** Abortado quando a janela fecha (Esc, Fechar): uma abertura ainda no servidor não troca mais o caso. */
  const sinal = useRef<AbortSignal | null>(null)

  useEffect(() => {
    const controle = new AbortController()
    sinal.current = controle.signal
    return () => controle.abort()
  }, [])

  useEffect(() => {
    let vale = true
    listarCalculos().then(
      (l) => vale && setLista(l),
      (e) => vale && setErro(mensagem(e)),
    )
    return () => {
      vale = false
    }
  }, [])

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

  return (
    <>
      <p className="dica">
        Abrir mostra o demonstrativo com os índices do dia em que foi salvo. Atualizar leva a data-base ao fim do mês
        anterior e recalcula com os índices de hoje. Duplicar traz só o formulário, para um caso parecido.
      </p>
      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}
      {lista === null && !erro && <p role="status">Carregando os cálculos salvos…</p>}
      {lista?.length === 0 && (
        <p className="lista-vazia">Nenhum cálculo salvo. Depois de calcular, use Salvar no demonstrativo.</p>
      )}
      {lista && lista.length > 0 && (
        <div className="rolagem" tabIndex={0} role="region" aria-label="Lista de cálculos salvos">
          <table className="tabela-salvos">
            <caption className="sr-only">Cálculos salvos, do mais recente para o mais antigo</caption>
            <thead>
              <tr>
                <th scope="col">Título</th>
                <th scope="col">Data-base</th>
                <th scope="col">Total</th>
                <th scope="col">Salvo em</th>
                <th scope="col">
                  <span className="sr-only">Ações</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {lista.map((r) => (
                <tr key={r.id}>
                  <th scope="row">
                    {r.titulo}
                    <span className="nota">{r.regime}</span>
                  </th>
                  <td>{data(r.dataBase)}</td>
                  <td>{moeda(r.total)}</td>
                  <td>{dataHora(new Date(r.criadoEm))}</td>
                  <td className="acoes-salvo">
                    {(['abrir', 'atualizar', 'duplicar'] as const)
                      // Um cálculo que já vai até o fim do mês anterior não tem o que atualizar.
                      .filter((acao) => acao !== 'atualizar' || novaDataBase(r.dataBase) !== null)
                      .map((acao) => {
                        const esta = emCurso?.id === r.id && emCurso.acao === acao
                        return (
                          <button
                            key={acao}
                            type="button"
                            // Só o botão que espera o servidor mostra o andamento; os outros, só travados.
                            className={[acao !== 'abrir' && 'secundario', esta && 'esperando'].filter(Boolean).join(' ') || undefined}
                            aria-label={`${ROTULOS[acao].botao} ${r.titulo}`}
                            disabled={emCurso !== null}
                            onClick={() => void escolher(acao, r)}
                          >
                            {esta ? ROTULOS[acao].andamento : ROTULOS[acao].botao}
                          </button>
                        )
                      })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
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
