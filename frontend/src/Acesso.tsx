import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { aoExpirarSessao, buscarSessao, entrar, ErroDaApi, sair, type Sessao } from './api'
import { Janela } from './Janela'
import { ContextoSessao, type SessaoAtual } from './sessao'

const mensagem = (e: unknown) => (e instanceof Error ? e.message : String(e))

type Estado =
  | { tipo: 'verificando' }
  | { tipo: 'falhou'; erro: string }
  | { tipo: 'anonimo' }
  | { tipo: 'logado'; sessao: Sessao; expirou: boolean }

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
      (sessao) => vale && setEstado(sessao ? { tipo: 'logado', sessao, expirou: false } : { tipo: 'anonimo' }),
      (e) => vale && setEstado({ tipo: 'falhou', erro: mensagem(e) }),
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

  const logado = (sessao: Sessao) => setEstado({ tipo: 'logado', sessao, expirou: false })

  switch (estado.tipo) {
    case 'verificando':
      return null
    case 'falhou':
      return (
        <main className="acesso">
          <Marca />
          <p className="erro" role="alert">
            Não foi possível verificar a sessão. {estado.erro}
          </p>
          <button type="button" onClick={() => setTentativa((n) => n + 1)}>
            Tentar de novo
          </button>
        </main>
      )
    case 'anonimo':
      return (
        <main className="acesso">
          <Marca />
          <h2 id="titulo-entrar">Entrar</h2>
          <FormularioEntrar aoEntrar={logado} />
        </main>
      )
    case 'logado': {
      const atual: SessaoAtual = {
        email: estado.sessao.email,
        sair: async () => {
          await sair()
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
            <FormularioEntrar emailInicial={estado.sessao.email} aoEntrar={logado} />
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
  emailInicial?: string
  aoEntrar: (sessao: Sessao) => void
}

function FormularioEntrar({ emailInicial = '', aoEntrar }: PropsEntrar) {
  const [email, setEmail] = useState(emailInicial)
  const [senha, setSenha] = useState('')
  const [entrando, setEntrando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (!email.trim() || !senha) {
      setErro('Informe o e-mail e a senha.')
      return
    }
    setErro(null)
    setEntrando(true)
    try {
      aoEntrar(await entrar(email.trim(), senha))
    } catch (e) {
      // a API já diz "E-mail ou senha incorretos."; o resto é falha de rede ou do servidor
      setErro(e instanceof ErroDaApi && e.status === 401 ? e.message : `Não foi possível entrar. ${mensagem(e)}`)
      setSenha('')
    } finally {
      setEntrando(false)
    }
  }

  return (
    <form className="formulario-entrar" onSubmit={enviar} noValidate>
      <label>
        E-mail
        <input
          type="email"
          autoComplete="username"
          data-foco-inicial={emailInicial ? undefined : true}
          autoFocus={!emailInicial}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <label>
        Senha
        <input
          type="password"
          autoComplete="current-password"
          data-foco-inicial={emailInicial ? true : undefined}
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
