import { useEffect, useRef, useState, type FormEvent } from 'react'
import { aceitarConvite, consultarConvite, entrar, ErroDaApi, type ConviteAberto } from './api'
import { MensagemErro } from './Campo'
import { problemaDaSenha, tokenDoLink } from './gestaoUsuarios'

const mensagem = (e: unknown) => (e instanceof Error ? e.message : String(e))

type Estado =
  | { tipo: 'abrindo' }
  /** O token não abre convite: venceu, já foi usado, foi trocado ou o link veio cortado. */
  | { tipo: 'invalido'; motivo: string }
  /** A API não respondeu; o convite pode estar bom. */
  | { tipo: 'falhou'; motivo: string }
  | { tipo: 'aberto'; convite: ConviteAberto }

const LINK_CORTADO =
  'O link do convite está incompleto. Abra-o de novo pelo e-mail, de preferência clicando nele em vez de copiar.'

/**
 * A página do link do convite (`/convite#<token>`): mostra o escritório e o e-mail, pede a senha
 * e, criada a conta, entra e leva à tela do cálculo. O token sai da barra de endereço assim que é
 * lido, para não ficar no histórico nem num print da tela.
 */
export function PaginaConvite() {
  const [token] = useState(() => tokenDoLink(window.location.hash))
  const [estado, setEstado] = useState<Estado>(() =>
    token ? { tipo: 'abrindo' } : { tipo: 'invalido', motivo: LINK_CORTADO },
  )
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    if (window.location.hash) window.history.replaceState(null, '', window.location.pathname)
  }, [])

  useEffect(() => {
    if (!token) return
    let vale = true
    consultarConvite(token).then(
      (convite) => vale && setEstado({ tipo: 'aberto', convite }),
      (e) =>
        vale &&
        setEstado(
          e instanceof ErroDaApi && e.status === 404
            ? { tipo: 'invalido', motivo: e.message }
            : { tipo: 'falhou', motivo: `Não foi possível abrir o convite. ${mensagem(e)}` },
        ),
    )
    return () => {
      vale = false
    }
  }, [token, tentativa])

  return (
    <main className="acesso">
      <header className="topo">
        <div>
          <h1>Debeatur</h1>
          <p>Execução de alimentos, do título ao demonstrativo.</p>
        </div>
      </header>
      <h2 id="titulo-convite">Criar a senha</h2>
      {estado.tipo === 'abrindo' && <p role="status">Abrindo o convite…</p>}
      {estado.tipo === 'invalido' && (
        <>
          <p className="erro" role="alert">
            {estado.motivo}
          </p>
          <p>
            <a href="/">Ir para o login</a>
          </p>
        </>
      )}
      {estado.tipo === 'falhou' && (
        <>
          <p className="erro" role="alert">
            {estado.motivo}
          </p>
          <button
            type="button"
            onClick={() => {
              setEstado({ tipo: 'abrindo' })
              setTentativa((n) => n + 1)
            }}
          >
            Tentar de novo
          </button>
        </>
      )}
      {estado.tipo === 'aberto' && token && (
        <FormularioSenha
          token={token}
          convite={estado.convite}
          aoInvalidar={(motivo) => setEstado({ tipo: 'invalido', motivo })}
        />
      )}
    </main>
  )
}

interface PropsSenha {
  token: string
  convite: ConviteAberto
  /** O convite deixou de valer entre abrir a página e criar a senha. */
  aoInvalidar: (motivo: string) => void
}

const ID_ERRO_SENHA = 'erro-senha'

function FormularioSenha({ token, convite, aoInvalidar }: PropsSenha) {
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  /** O que falta à senha digitada; fica ligado aos campos e some ao corrigir. */
  const [erroSenha, setErroSenha] = useState<string | null>(null)
  /** A falha do servidor ao criar a senha, que não é culpa dos campos. */
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const [criando, setCriando] = useState(false)
  /** A conta foi criada, mas o login logo depois falhou: falta só entrar pela tela de login. */
  const [criada, setCriada] = useState(false)
  const campoSenha = useRef<HTMLInputElement>(null)
  /** O campo da senha recebe o foco quando o erro aparece; o botão fica desabilitado e o perde. */
  const focarDepois = useRef(false)

  useEffect(() => {
    if (!criando && focarDepois.current) {
      campoSenha.current?.focus()
      focarDepois.current = false
    }
  }, [criando, erroEnvio])

  function mudou(novaSenha: string, novaConfirmacao: string) {
    setSenha(novaSenha)
    setConfirmacao(novaConfirmacao)
    // como nos campos do formulário: o erro acompanha o que foi digitado e some quando a senha serve
    if (erroSenha) setErroSenha(problemaDaSenha(novaSenha, novaConfirmacao))
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setErroEnvio(null)
    const problema = problemaDaSenha(senha, confirmacao)
    setErroSenha(problema)
    if (problema) {
      // sai e volta, para o leitor de tela reler o campo com a mensagem nova
      campoSenha.current?.blur()
      campoSenha.current?.focus()
      return
    }
    setCriando(true)
    try {
      await aceitarConvite(token, senha)
    } catch (e) {
      if (e instanceof ErroDaApi && e.status === 404) return aoInvalidar(e.message)
      setErroEnvio(`Não foi possível criar a senha. ${mensagem(e)}`)
      focarDepois.current = true
      setCriando(false)
      return
    }
    try {
      await entrar(convite.email, senha)
      window.location.replace('/')
    } catch {
      setCriada(true)
      setCriando(false)
    }
  }

  if (criada) {
    return (
      <p role="status">
        <strong>Senha criada.</strong> Entre com {convite.email} e a senha escolhida.{' '}
        <a href="/">Ir para o login</a>
      </p>
    )
  }

  const invalido = erroSenha ? true : undefined
  const descricao = erroSenha ? ID_ERRO_SENHA : undefined
  return (
    <form className="formulario-entrar" aria-labelledby="titulo-convite" onSubmit={enviar} noValidate>
      <p className="dica">
        Convite para o escritório <strong>{convite.escritorio}</strong>. A senha precisa de ao menos 8 caracteres.
      </p>
      <label>
        E-mail
        <input type="email" autoComplete="username" readOnly value={convite.email} />
      </label>
      <label>
        Senha
        {erroSenha && <MensagemErro id={ID_ERRO_SENHA}>{erroSenha}</MensagemErro>}
        <input
          ref={campoSenha}
          type="password"
          autoComplete="new-password"
          autoFocus
          aria-invalid={invalido}
          aria-describedby={descricao}
          value={senha}
          onChange={(e) => mudou(e.target.value, confirmacao)}
        />
      </label>
      <label>
        Repita a senha
        <input
          type="password"
          autoComplete="new-password"
          aria-invalid={invalido}
          aria-describedby={descricao}
          value={confirmacao}
          onChange={(e) => mudou(senha, e.target.value)}
        />
      </label>
      {erroEnvio && (
        <p className="erro" role="alert">
          {erroEnvio}
        </p>
      )}
      <div className="botoes">
        <button type="submit" className="esperando" disabled={criando}>
          {criando ? 'Criando a senha…' : 'Criar a senha e entrar'}
        </button>
      </div>
    </form>
  )
}
