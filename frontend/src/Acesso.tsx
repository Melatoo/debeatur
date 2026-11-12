import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { aoExpirarSessao, buscarSessao, entrar, ErroDaApi, sair, type Sessao } from './api'
import { Janela } from './Janela'
import { ContextoSessao, type SessaoAtual } from './sessao'

const mensagem = (e: unknown) => (e instanceof Error ? e.message : String(e))

type Estado =
  | { tipo: 'verificando' }
  | { tipo: 'falhou' }
  | { tipo: 'anonimo' }
  | { tipo: 'logado'; sessao: Sessao; expirou: boolean; geracao: number }

/**
 * Sem sessão, mostra o login no lugar da aplicação. Se a sessão expira no meio do uso, o login
 * abre numa janela por cima, sem desmontar o formulário: o caso da tela continua lá depois de
 * entrar, e basta repetir a ação.
 */
export function Acesso({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'verificando' })
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    let vale = true
    buscarSessao().then(
      (sessao) =>
        vale && setEstado(sessao ? { tipo: 'logado', sessao, expirou: false, geracao: 0 } : { tipo: 'anonimo' }),
      () => vale && setEstado({ tipo: 'falhou' }),
    )
    return () => {
      vale = false
    }
  }, [tentativa])

  useEffect(
    () =>
      aoExpirarSessao(() =>
        setEstado((atual) => (atual.tipo === 'logado' ? { ...atual, expirou: true } : atual)),
      ),
    [],
  )

  function tentarDeNovo() {
    setEstado({ tipo: 'verificando' })
    setTentativa((n) => n + 1)
  }

  switch (estado.tipo) {
    case 'verificando':
      return null
    case 'falhou':
      return (
        <main className="acesso">
          <Marca />
          <p className="erro" role="alert">
            Não foi possível verificar a sessão. Confira a conexão e tente de novo.
          </p>
          <button type="button" onClick={tentarDeNovo}>
            Tentar de novo
          </button>
        </main>
      )
    case 'anonimo':
      return (
        <main className="acesso">
          <Marca />
          <h2 id="titulo-entrar">Entrar</h2>
          <FormularioEntrar
            rotulo="titulo-entrar"
            aoEntrar={(sessao) => setEstado({ tipo: 'logado', sessao, expirou: false, geracao: 0 })}
          />
        </main>
      )
    case 'logado': {
      const atual: SessaoAtual = {
        email: estado.sessao.email,
        geracao: estado.geracao,
        sair: async () => {
          try {
            await sair()
          } catch (e) {
            // 401 ou 403: a sessão já não vale no servidor, então sair aqui também é o certo
            if (!(e instanceof ErroDaApi && (e.status === 401 || e.status === 403))) throw e
          }
          setEstado({ tipo: 'anonimo' })
        },
      }
      return (
        <ContextoSessao.Provider value={atual}>
          {children}
          <Janela
            id="titulo-sessao-expirada"
            titulo="Entrar de novo"
            aberta={estado.expirou}
            aoFechar={() => setEstado({ ...estado, expirou: false })}
          >
            <p className="dica">A sessão expirou. Entre de novo; o caso da tela continua aqui.</p>
            <FormularioEntrar
              rotulo="titulo-sessao-expirada"
              email={estado.sessao.email}
              aoEntrar={(sessao) =>
                setEstado({ tipo: 'logado', sessao, expirou: false, geracao: estado.geracao + 1 })
              }
            />
          </Janela>
        </ContextoSessao.Provider>
      )
    }
  }
}

function Marca() {
  return (
    <header className="topo">
      <div>
        <h1>Debeatur</h1>
        <p>Execução de alimentos, do título ao demonstrativo.</p>
      </div>
    </header>
  )
}

interface PropsEntrar {
  /** O id do título que nomeia o formulário. */
  rotulo: string
  /**
   * Fixo ao entrar de novo: o caso da tela é deste usuário, e outra conta (de outro escritório)
   * não pode herdá-lo.
   */
  email?: string
  aoEntrar: (sessao: Sessao) => void
}

function FormularioEntrar({ rotulo, email: emailFixo, aoEntrar }: PropsEntrar) {
  const [email, setEmail] = useState(emailFixo ?? '')
  const [senha, setSenha] = useState('')
  const [entrando, setEntrando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const campoEmail = useRef<HTMLInputElement>(null)
  const campoSenha = useRef<HTMLInputElement>(null)
  /** O campo que recebe o foco quando o erro aparece; o botão fica desabilitado e o perde. */
  const focarDepois = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!entrando && focarDepois.current) {
      focarDepois.current.focus()
      focarDepois.current = null
    }
  }, [entrando, erro])

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (!email.trim() || !senha) {
      setErro('Informe o e-mail e a senha.')
      ;(email.trim() ? campoSenha : campoEmail).current?.focus()
      return
    }
    setErro(null)
    setEntrando(true)
    try {
      aoEntrar(await entrar(email.trim(), senha))
    } catch (e) {
      // a API já diz o que houve num 401 (credencial) ou 503 (falha dela); o resto é a rede
      setErro(
        e instanceof ErroDaApi && (e.status === 401 || e.status === 503)
          ? e.message
          : `Não foi possível entrar. ${mensagem(e)}`,
      )
      setSenha('')
      focarDepois.current = campoSenha.current
      setEntrando(false)
    }
  }

  return (
    <form className="formulario-entrar" aria-labelledby={rotulo} onSubmit={enviar} noValidate>
      <label>
        E-mail
        <input
          ref={campoEmail}
          type="email"
          autoComplete="username"
          readOnly={emailFixo !== undefined}
          autoFocus={emailFixo === undefined}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <label>
        Senha
        <input
          ref={campoSenha}
          type="password"
          autoComplete="current-password"
          data-foco-inicial={emailFixo !== undefined ? true : undefined}
          value={senha}
          onChange={(e) => setSenha(e.target.value)}
        />
      </label>
      {erro && (
        <p className="erro" role="alert">
          {erro}
        </p>
      )}
      <div className="botoes">
        <button type="submit" className="esperando" disabled={entrando}>
          {entrando ? 'Entrando…' : 'Entrar'}
        </button>
      </div>
    </form>
  )
}
