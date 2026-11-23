import { describe, expect, it } from 'vitest'
import type { Convite } from './api'
import { comConvite, problemaDaSenha, problemaDoEmail, tokenDoLink, venceu } from './gestaoUsuarios'

const convite = (email: string, expiraEm = '2026-11-30T12:00:00Z'): Convite => ({
  id: email,
  email,
  papel: 'MEMBRO',
  criadoEm: '2026-11-23T12:00:00Z',
  expiraEm,
})

describe('problemaDoEmail', () => {
  it('aceita o e-mail com espaços nas pontas', () => {
    expect(problemaDoEmail('  maria@escritorio.adv.br ')).toBeNull()
  })

  it('pede o e-mail vazio e o sem domínio com exemplo', () => {
    expect(problemaDoEmail(' ')).toMatch(/Informe o e-mail/)
    expect(problemaDoEmail('maria')).toMatch(/precisa ter um @/)
    expect(problemaDoEmail('maria@escritorio')).toMatch(/precisa ter um @/)
  })
})

describe('problemaDaSenha', () => {
  it('aceita de 8 caracteres a 72 bytes iguais nos dois campos', () => {
    expect(problemaDaSenha('12345678', '12345678')).toBeNull()
    expect(problemaDaSenha('a'.repeat(72), 'a'.repeat(72))).toBeNull()
  })

  it('recusa a curta, a de mais de 72 bytes e a confirmação diferente', () => {
    expect(problemaDaSenha('curta', 'curta')).toMatch(/ao menos 8/)
    // 37 letras acentuadas são 74 bytes
    expect(problemaDaSenha('ç'.repeat(37), 'ç'.repeat(37))).toMatch(/72 bytes/)
    expect(problemaDaSenha('uma senha longa', 'uma senha longA')).toMatch(/não são iguais/)
  })
})

describe('comConvite', () => {
  it('põe o novo no topo e tira o pendente do mesmo e-mail, sem diferenciar maiúsculas', () => {
    const lista = [convite('bia@x.adv.br'), convite('Caio@x.adv.br')]

    expect(comConvite(lista, convite('caio@X.adv.br')).map((c) => c.email)).toEqual(['caio@X.adv.br', 'bia@x.adv.br'])
  })
})

describe('venceu', () => {
  it('compara o vencimento com agora', () => {
    expect(venceu(convite('a@x.adv.br', '2026-11-30T12:00:00Z'), new Date('2026-11-30T11:59:59Z'))).toBe(false)
    expect(venceu(convite('a@x.adv.br', '2026-11-30T12:00:00Z'), new Date('2026-11-30T12:00:00Z'))).toBe(true)
  })
})

describe('tokenDoLink', () => {
  it('tira o # e aceita só o alfabeto do token', () => {
    const token = 'abcDEF123_-abcDEF123_-abcDEF123_-abcDEF1234'
    expect(tokenDoLink(`#${token}`)).toBe(token)
    expect(tokenDoLink('')).toBeNull()
    expect(tokenDoLink('#curto')).toBeNull()
    expect(tokenDoLink('#tem espaço no meio do token de convite')).toBeNull()
  })
})
