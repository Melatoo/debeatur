import type { Convite, Papel } from './api'

/** O nome do perfil na tela. */
export const NOME_DO_PAPEL: Record<Papel, string> = { ADMIN: 'Admin', MEMBRO: 'Membro' }

/** O que cada perfil pode, para a dica do convite. */
export const O_QUE_O_PAPEL_PODE: Record<Papel, string> = {
  MEMBRO: 'calcula e vê só os cálculos que salvou',
  ADMIN: 'também convida, gerencia os usuários e vê os cálculos de todos',
}

/**
 * O problema do e-mail digitado, ou `null` se serve. A conferência é a mínima (algo antes e
 * depois de um @, com ponto no domínio); a da API decide.
 */
export function problemaDoEmail(email: string): string | null {
  const limpo = email.trim()
  if (!limpo) return 'Informe o e-mail de quem vai entrar, como maria@escritorio.adv.br.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(limpo)) {
    return 'Confira o e-mail: ele precisa ter um @ e o domínio, como maria@escritorio.adv.br.'
  }
  return null
}

/**
 * O problema da senha nova, ou `null` se serve. São as regras da API: de 8 caracteres a 72 bytes
 * em UTF-8, o limite do bcrypt.
 */
export function problemaDaSenha(senha: string, confirmacao: string): string | null {
  if (senha.length < 8) return 'A senha precisa de ao menos 8 caracteres. Uma frase curta serve.'
  if (new TextEncoder().encode(senha).length > 72) {
    return 'A senha pode ter até 72 bytes; cada letra acentuada conta 2. Encurte-a um pouco.'
  }
  if (senha !== confirmacao) return 'As duas senhas não são iguais. Digite a mesma senha nos dois campos.'
  return null
}

/** O convite novo entra no topo e tira o pendente do mesmo e-mail, que a API invalidou. */
export function comConvite(convites: Convite[], novo: Convite): Convite[] {
  const email = novo.email.toLowerCase()
  return [novo, ...convites.filter((c) => c.email.toLowerCase() !== email)]
}

/** Se o convite já venceu; ele fica na lista para o admin reenviar. */
export function venceu(convite: Convite, agora: Date = new Date()): boolean {
  return new Date(convite.expiraEm).getTime() <= agora.getTime()
}

/**
 * O token do link do convite, que vem no fragmento (`/convite#<token>`) para não ir ao servidor
 * ao abrir a página.
 */
export function tokenDoLink(hash: string): string | null {
  const token = hash.replace(/^#/, '').trim()
  return /^[A-Za-z0-9_-]{20,100}$/.test(token) ? token : null
}
