import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Janela } from './Janela'
import {
  alterarUsuario,
  cancelarConvite,
  convidar,
  ErroDaApi,
  listarConvites,
  listarUsuarios,
  type Convite,
  type Papel,
  type Usuario,
} from './api'
import { MensagemErro } from './Campo'
import { dataHora } from './formato'
import { useSessao } from './sessao'
import { comConvite, NOME_DO_PAPEL, O_QUE_O_PAPEL_PODE, problemaDoEmail, venceu } from './gestaoUsuarios'

/**
 * O 403 vem sem texto: quem abriu a janela deixou de ser admin depois de entrar. O resto a API
 * explica no `detail`.
 */
const mensagem = (e: unknown) =>
  e instanceof ErroDaApi && e.status === 403
    ? 'Só um admin do escritório gerencia os usuários. Recarregue a página para ver o seu acesso atual.'
    : e instanceof Error
      ? e.message
      : String(e)

const ID_EMAIL = 'email-convite'

interface Props {
  aberta: boolean
  aoFechar: () => void
}

/** Os usuários e os convites do escritório, para o admin; buscados de novo a cada abertura. */
export function JanelaUsuarios({ aberta, aoFechar }: Props) {
  return (
    <Janela id="titulo-usuarios" titulo="Usuários" aberta={aberta} aoFechar={aoFechar}>
      <ConteudoUsuarios />
    </Janela>
  )
}

function ConteudoUsuarios() {
  const sessao = useSessao()
  const [usuarios, setUsuarios] = useState<Usuario[] | null>(null)
  const [convites, setConvites] = useState<Convite[] | null>(null)
  /** As listas não vieram do servidor. */
  const [erroLista, setErroLista] = useState<string | null>(null)
  /** Uma ação falhou; as listas continuam. */
  const [erro, setErro] = useState<string | null>(null)
  /** O que acabou de mudar, para o leitor de tela. */
  const [feito, setFeito] = useState<string | null>(null)
  /** A ação que espera o servidor, como `convidar` ou `desativar-<id>`. */
  const [emCurso, setEmCurso] = useState<string | null>(null)
  /** O usuário que pede confirmação para ser desativado. */
  const [confirmando, setConfirmando] = useState<string | null>(null)
  const [email, setEmail] = useState('')
  const [papel, setPapel] = useState<Papel>('MEMBRO')
  const [erroEmail, setErroEmail] = useState<string | null>(null)
  /** O id do controle que recebe o foco depois de a linha mudar de forma. */
  const focar = useRef<string | null>(null)
  /**
   * Conta os envios com o e-mail inválido: o mesmo erro duas vezes não muda `erroEmail`, e sem um
   * render o foco não voltaria ao campo.
   */
  const [, setEnviosInvalidos] = useState(0)
  // Lido na abertura: o vencimento que muda com a janela aberta aparece na próxima.
  const [agora] = useState(() => new Date())

  useEffect(() => {
    // Com uma ação no servidor, os botões estão desabilitados e não recebem foco: espera ela acabar.
    if (focar.current === null || emCurso !== null) return
    const alvo = document.getElementById(focar.current) ?? document.getElementById(ID_EMAIL)
    if (alvo && document.activeElement === alvo) alvo.blur()
    alvo?.focus()
    focar.current = null
  })

  const geracao = sessao?.geracao ?? 0
  useEffect(() => {
    let vale = true
    Promise.all([listarUsuarios(), listarConvites()]).then(
      ([u, c]) => {
        if (!vale) return
        setErroLista(null)
        setUsuarios(u)
        setConvites(c)
      },
      (e) => vale && setErroLista(mensagem(e)),
    )
    return () => {
      vale = false
    }
  }, [geracao])

  /**
   * @param focoNaFalha o controle que recebe o foco se a ação falhar: o botão clicado ficou
   *                    desabilitado na espera e perdeu o foco
   */
  async function executar(chave: string, acao: () => Promise<string>, falha: string, focoNaFalha: string) {
    setErro(null)
    setFeito(null)
    setEmCurso(chave)
    try {
      setFeito(await acao())
    } catch (e) {
      setErro(`${falha} ${mensagem(e)}`)
      focar.current = focoNaFalha
    } finally {
      setEmCurso(null)
    }
  }

  /**
   * A API apaga os convites de quem perde o admin; a lista vem dela de novo.
   * @returns a lista nova, ou `null` se ela não veio e a antiga continua na tela
   */
  async function recarregarConvites(): Promise<Convite[] | null> {
    try {
      const lista = await listarConvites()
      setConvites(lista)
      return lista
    } catch {
      return null
    }
  }

  function enviarConvite(evento: FormEvent) {
    evento.preventDefault()
    const problema = problemaDoEmail(email)
    setErroEmail(problema)
    if (problema) {
      focar.current = ID_EMAIL
      setEnviosInvalidos((n) => n + 1)
      return
    }
    const destino = email.trim()
    void executar(
      'convidar',
      async () => {
        const novo = await convidar(destino, papel)
        setConvites((c) => comConvite(c ?? [], novo))
        setEmail('')
        setPapel('MEMBRO')
        focar.current = ID_EMAIL
        return `Convite enviado para ${novo.email}. O link vale até ${dataHora(new Date(novo.expiraEm))}.`
      },
      `Não foi possível convidar ${destino}.`,
      ID_EMAIL,
    )
  }

  function reenviar(c: Convite) {
    void executar(
      `reenviar-${c.id}`,
      async () => {
        const novo = await convidar(c.email, c.papel)
        setConvites((lista) => comConvite(lista ?? [], novo))
        focar.current = `reenviar-${novo.id}`
        return `Convite enviado de novo para ${novo.email}. O link anterior não vale mais.`
      },
      `Não foi possível reenviar o convite de ${c.email}.`,
      `reenviar-${c.id}`,
    )
  }

  function cancelar(c: Convite) {
    void executar(
      `cancelar-${c.id}`,
      async () => {
        try {
          await cancelarConvite(c.id)
        } catch (e) {
          if (!(e instanceof ErroDaApi && e.status === 404)) throw e
          // já não estava pendente (aceito, cancelado em outra janela ou caído com quem convidou)
          focar.current = ID_EMAIL
          return (await recarregarConvites())
            ? `O convite de ${c.email} já não estava pendente. A lista foi atualizada.`
            : `O convite de ${c.email} já não estava pendente. Feche e abra a janela para ver a lista atual.`
        }
        setConvites((lista) => lista && lista.filter((x) => x.id !== c.id))
        focar.current = ID_EMAIL
        return `O convite de ${c.email} foi cancelado; o link dele não vale mais.`
      },
      `Não foi possível cancelar o convite de ${c.email}.`,
      `cancelar-${c.id}`,
    )
  }

  function alterar(u: Usuario, alteracao: { papel?: Papel; ativo?: boolean }, chave: string, feito: (novo: Usuario) => string) {
    void executar(
      `${chave}-${u.id}`,
      async () => {
        const novo = await alterarUsuario(u.id, alteracao)
        setUsuarios((lista) => lista && lista.map((x) => (x.id === novo.id ? novo : x)))
        setConfirmando(null)
        focar.current = `${chave === 'papel' ? 'papel' : 'ativo'}-${u.id}`
        if (u.papel === 'ADMIN' && u.ativo && (novo.papel !== 'ADMIN' || !novo.ativo)) {
          // a resposta não diz quais convites eram dessa pessoa: o que sumiu da lista foi dela
          const antes = convites?.length ?? 0
          const depois = await recarregarConvites()
          if (depois && depois.length < antes) {
            return `${feito(novo)} Os convites pendentes feitos por essa pessoa foram cancelados.`
          }
        }
        return feito(novo)
      },
      `Não foi possível alterar ${u.email}.`,
      // na confirmação, o Cancelar continua na tela; fora dela, o próprio botão
      confirmando === u.id ? `cancelar-desativacao-${u.id}` : `${chave === 'papel' ? 'papel' : 'ativo'}-${u.id}`,
    )
  }

  function pedirDesativacao(u: Usuario) {
    setErro(null)
    setFeito(null)
    setConfirmando(u.id)
    focar.current = `cancelar-desativacao-${u.id}`
  }

  function desistirDaDesativacao(id: string) {
    setConfirmando(null)
    focar.current = `ativo-${id}`
  }

  const travado = emCurso !== null
  const esperando = (chave: string) => (emCurso === chave ? 'esperando' : undefined)

  return (
    <div className="gestao-usuarios">
      <section aria-labelledby="titulo-convidar">
        <h3 id="titulo-convidar">Convidar</h3>
        <form className="formulario-convite" onSubmit={enviarConvite} noValidate>
          <label htmlFor={ID_EMAIL}>E-mail</label>
          {erroEmail && <MensagemErro id={`${ID_EMAIL}-erro`}>{erroEmail}</MensagemErro>}
          <input
            id={ID_EMAIL}
            type="email"
            autoComplete="off"
            data-foco-inicial
            aria-invalid={erroEmail ? true : undefined}
            aria-describedby={erroEmail ? `${ID_EMAIL}-erro` : 'dica-convite'}
            value={email}
            onChange={(e) => {
              setEmail(e.target.value)
              if (erroEmail) setErroEmail(problemaDoEmail(e.target.value))
            }}
          />
          <p className="dica" id="dica-convite">
            A pessoa recebe um link para criar a senha, que vale 7 dias.
          </p>
          <fieldset className="opcoes-perfil">
            <legend>Perfil</legend>
            {(['MEMBRO', 'ADMIN'] as const).map((p) => (
              <label key={p} className="checkbox">
                <input type="radio" name="perfil-convite" checked={papel === p} onChange={() => setPapel(p)} />
                {NOME_DO_PAPEL[p]}: {O_QUE_O_PAPEL_PODE[p]}
              </label>
            ))}
          </fieldset>
          <div className="botoes">
            <button type="submit" className={esperando('convidar')} disabled={travado}>
              {emCurso === 'convidar' ? 'Enviando convite…' : 'Enviar convite'}
            </button>
          </div>
        </form>
      </section>

      {(erroLista ?? erro) && (
        <p className="erro" role="alert">
          {erroLista ?? erro}
        </p>
      )}
      <p className="status-salvos" role="status">
        {feito}
      </p>
      {usuarios === null && !erroLista && <p role="status">Carregando os usuários…</p>}

      {convites && convites.length > 0 && (
        <section aria-labelledby="titulo-convites">
          <h3 id="titulo-convites">Convites pendentes</h3>
          <div className="rolagem" tabIndex={0} role="region" aria-label="Convites pendentes">
            <table className="tabela-salvos tabela-usuarios" role="table">
              <caption className="sr-only">Convites ainda não aceitos, do mais recente para o mais antigo</caption>
              <thead role="rowgroup">
                <tr role="row">
                  <th scope="col" role="columnheader">E-mail</th>
                  <th scope="col" role="columnheader">Perfil</th>
                  <th scope="col" role="columnheader">Vale até</th>
                  <th scope="col" role="columnheader">
                    <span className="sr-only">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                {convites.map((c) => (
                  <tr key={c.id} role="row">
                    <th scope="row" role="rowheader">
                      {c.email}
                    </th>
                    <td role="cell" data-rotulo="Perfil">{NOME_DO_PAPEL[c.papel]}</td>
                    <td role="cell" data-rotulo="Vale até">
                      {venceu(c, agora) ? (
                        <>
                          <strong>Venceu</strong> em {dataHora(new Date(c.expiraEm))}
                        </>
                      ) : (
                        dataHora(new Date(c.expiraEm))
                      )}
                    </td>
                    <td className="acoes-salvo acoes-usuario" role="cell">
                      <button
                        type="button"
                        id={`reenviar-${c.id}`}
                        className={['secundario', esperando(`reenviar-${c.id}`)].filter(Boolean).join(' ')}
                        aria-label={`Reenviar o convite de ${c.email}`}
                        disabled={travado}
                        onClick={() => reenviar(c)}
                      >
                        {emCurso === `reenviar-${c.id}` ? 'Reenviando…' : 'Reenviar'}
                      </button>
                      <button
                        type="button"
                        className={['link', esperando(`cancelar-${c.id}`)].filter(Boolean).join(' ')}
                        id={`cancelar-${c.id}`}
                        aria-label={`Cancelar convite de ${c.email}`}
                        disabled={travado}
                        onClick={() => cancelar(c)}
                      >
                        {emCurso === `cancelar-${c.id}` ? 'Cancelando…' : 'Cancelar convite'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {usuarios && (
        <section aria-labelledby="titulo-lista-usuarios">
          <h3 id="titulo-lista-usuarios">Usuários do escritório</h3>
          <div className="rolagem" tabIndex={0} role="region" aria-label="Usuários do escritório">
            <table className="tabela-salvos tabela-usuarios" role="table">
              <caption className="sr-only">Usuários do escritório, na ordem em que entraram</caption>
              <thead role="rowgroup">
                <tr role="row">
                  <th scope="col" role="columnheader">E-mail</th>
                  <th scope="col" role="columnheader">Perfil</th>
                  <th scope="col" role="columnheader">Situação</th>
                  <th scope="col" role="columnheader">
                    <span className="sr-only">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                {usuarios.map((u) => {
                  const voce = u.email.toLowerCase() === sessao?.email.toLowerCase()
                  const outroPapel: Papel = u.papel === 'ADMIN' ? 'MEMBRO' : 'ADMIN'
                  return (
                    <tr key={u.id} role="row" data-ativo={u.ativo}>
                      <th scope="row" role="rowheader">
                        {u.email}
                        {voce && <span className="nota">Você</span>}
                      </th>
                      <td role="cell" data-rotulo="Perfil">{NOME_DO_PAPEL[u.papel]}</td>
                      <td role="cell" data-rotulo="Situação">{u.ativo ? 'Ativo' : 'Desativado'}</td>
                      <td className="acoes-salvo acoes-usuario" role="cell">
                        {voce ? (
                          <span className="nota">Outro admin muda o seu acesso.</span>
                        ) : confirmando === u.id ? (
                          <div className="confirmar-exclusao" role="group" aria-labelledby={`pergunta-${u.id}`}>
                            <p id={`pergunta-${u.id}`}>
                              <strong>Desativar {u.email}?</strong> A pessoa sai na hora e não entra mais. Os cálculos
                              dela continuam com os admins, e dá para reativar depois.
                            </p>
                            <div className="acoes-linha">
                              <button
                                type="button"
                                className={esperando(`ativo-${u.id}`)}
                                disabled={travado}
                                onClick={() =>
                                  alterar(u, { ativo: false }, 'ativo', (n) => `${n.email} foi desativado e não entra mais.`)
                                }
                              >
                                {emCurso === `ativo-${u.id}` ? 'Desativando…' : 'Desativar'}
                              </button>
                              <button
                                type="button"
                                id={`cancelar-desativacao-${u.id}`}
                                className="secundario"
                                disabled={travado}
                                onClick={() => desistirDaDesativacao(u.id)}
                              >
                                Cancelar
                              </button>
                            </div>
                          </div>
                        ) : (
                          <>
                            {u.ativo && (
                              <button
                                type="button"
                                id={`papel-${u.id}`}
                                className={['secundario', esperando(`papel-${u.id}`)].filter(Boolean).join(' ')}
                                aria-label={`Tornar ${NOME_DO_PAPEL[outroPapel].toLowerCase()}: ${u.email}`}
                                disabled={travado}
                                onClick={() =>
                                  alterar(
                                    u,
                                    { papel: outroPapel },
                                    'papel',
                                    (n) => `${n.email} agora é ${NOME_DO_PAPEL[n.papel].toLowerCase()}.`,
                                  )
                                }
                              >
                                {emCurso === `papel-${u.id}` ? 'Alterando…' : `Tornar ${NOME_DO_PAPEL[outroPapel].toLowerCase()}`}
                              </button>
                            )}
                            <button
                              type="button"
                              id={`ativo-${u.id}`}
                              className={['link', esperando(`ativo-${u.id}`)].filter(Boolean).join(' ')}
                              aria-label={`${u.ativo ? 'Desativar' : 'Reativar'} ${u.email}`}
                              disabled={travado}
                              onClick={() =>
                                u.ativo
                                  ? pedirDesativacao(u)
                                  : alterar(u, { ativo: true }, 'ativo', (n) => `${n.email} foi reativado e pode entrar de novo.`)
                              }
                            >
                              {emCurso === `ativo-${u.id}` ? 'Reativando…' : u.ativo ? 'Desativar' : 'Reativar'}
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  )
}
