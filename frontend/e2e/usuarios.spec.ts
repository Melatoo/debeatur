import { EMAIL_CONVIDADA, expect, semViolacoes, test, TOKEN_CONVITE } from './apoio.ts'

test('o membro não vê a gestão de usuários', async ({ page, api }) => {
  api.papel = 'MEMBRO'
  await page.goto('/')

  await expect(page.getByRole('button', { name: 'Cálculos salvos' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Usuários' })).toHaveCount(0)
})

test('o admin convida, vê o convite pendente e reenvia o vencido', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const janela = page.getByRole('dialog', { name: 'Usuários' })
  await expect(janela.getByLabel('E-mail')).toBeFocused()

  // o e-mail sem domínio nem chega à API
  await janela.getByLabel('E-mail').fill('bia')
  await janela.getByRole('button', { name: 'Enviar convite' }).click()
  await expect(janela.getByText('Confira o e-mail: ele precisa ter um @')).toBeVisible()
  await expect(janela.getByLabel('E-mail')).toHaveAttribute('aria-invalid', 'true')

  await janela.getByLabel('E-mail').fill(` ${EMAIL_CONVIDADA} `)
  await janela.getByRole('radio', { name: /^Admin:/ }).check()
  await janela.getByRole('button', { name: 'Enviar convite' }).click()

  await expect(janela.getByRole('status').filter({ hasText: 'Convite enviado' })).toHaveText(
    `Convite enviado para ${EMAIL_CONVIDADA}. O link vale até 18/11/2026, 14:00.`,
  )
  await expect(janela.getByLabel('E-mail')).toHaveValue('')
  expect(api.convites.map((c) => [c.email, c.papel])).toEqual([
    [EMAIL_CONVIDADA, 'ADMIN'],
    ['davi@escritorio.adv.br', 'MEMBRO'],
  ])
  const pendentes = janela.getByRole('region', { name: 'Convites pendentes' })
  await expect(pendentes.getByRole('row', { name: /davi@/ })).toContainText('Venceu em 08/11/2026')

  await pendentes.getByRole('button', { name: 'Reenviar o convite de davi@escritorio.adv.br' }).click()
  await expect(janela.getByRole('status').filter({ hasText: 'de novo' })).toHaveText(
    'Convite enviado de novo para davi@escritorio.adv.br. O link anterior não vale mais.',
  )
  await expect(pendentes.getByRole('row', { name: /davi@/ })).not.toContainText('Venceu')

  await pendentes.getByRole('button', { name: `Cancelar convite de ${EMAIL_CONVIDADA}` }).click()
  await expect(pendentes.getByRole('row', { name: new RegExp(EMAIL_CONVIDADA) })).toHaveCount(0)
  expect(api.convites.map((c) => c.email)).toEqual(['davi@escritorio.adv.br'])
})

test('convidar um e-mail que já tem conta mostra o motivo da API', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const janela = page.getByRole('dialog', { name: 'Usuários' })

  await janela.getByLabel('E-mail').fill('caio@escritorio.adv.br')
  await janela.getByRole('button', { name: 'Enviar convite' }).click()

  await expect(janela.getByRole('alert')).toHaveText(
    'Não foi possível convidar caio@escritorio.adv.br. Este e-mail já tem conta no Debeatur.',
  )
  // o botão ficou desabilitado na espera; o foco volta ao campo para corrigir
  await expect(janela.getByLabel('E-mail')).toBeFocused()
})

test('o mesmo e-mail inválido duas vezes volta o foco ao campo nas duas', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const janela = page.getByRole('dialog', { name: 'Usuários' })
  const campo = janela.getByLabel('E-mail')

  await campo.fill('bia')
  for (let vez = 0; vez < 2; vez++) {
    await janela.getByRole('button', { name: 'Enviar convite' }).focus()
    await page.keyboard.press('Enter')
    await expect(campo).toBeFocused()
  }
  // e um clique depois, no perfil, não puxa o foco de volta ao e-mail
  await janela.getByRole('radio', { name: /^Admin:/ }).check()
  await expect(janela.getByRole('radio', { name: /^Admin:/ })).toBeFocused()
})

test('quem perde o admin leva junto os convites que fez', async ({ page, api }) => {
  api.usuarios.find((u) => u.id === 'u-caio')!.papel = 'ADMIN'
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const janela = page.getByRole('dialog', { name: 'Usuários' })
  await expect(janela.getByRole('row', { name: /davi@/ })).toBeVisible()

  await janela.getByRole('button', { name: 'Tornar membro: caio@escritorio.adv.br' }).click()

  await expect(janela.getByRole('status').filter({ hasText: 'agora é' })).toHaveText(
    'caio@escritorio.adv.br agora é membro. Os convites pendentes feitos por essa pessoa foram cancelados.',
  )
  await expect(janela.getByRole('row', { name: /davi@/ })).toHaveCount(0)
})

test('cancelar um convite que já não estava pendente atualiza a lista em vez de dar erro', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const janela = page.getByRole('dialog', { name: 'Usuários' })
  await expect(janela.getByRole('row', { name: /davi@/ })).toBeVisible()
  // aceito ou cancelado em outra janela depois que esta abriu
  api.convites = []
  await page.route('**/api/convites/c-davi', (route) =>
    route.fulfill({
      status: 404,
      contentType: 'application/problem+json',
      json: { status: 404, detail: 'Convite c-davi não encontrado' },
    }),
  )

  await janela.getByRole('button', { name: 'Cancelar convite de davi@escritorio.adv.br' }).click()

  await expect(janela.getByRole('status').filter({ hasText: 'pendente' })).toHaveText(
    'O convite de davi@escritorio.adv.br já não estava pendente. A lista foi atualizada.',
  )
  await expect(janela.getByRole('alert')).toHaveCount(0)
  await expect(janela.getByRole('region', { name: 'Convites pendentes' })).toHaveCount(0)
})

test('o admin muda o perfil e desativa depois de confirmar, mas não mexe no próprio acesso', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Usuários' }).click()
  const lista = page.getByRole('dialog', { name: 'Usuários' }).getByRole('region', { name: 'Usuários do escritório' })

  const ana = lista.getByRole('row', { name: /ana@/ })
  await expect(ana).toContainText('Outro admin muda o seu acesso.')
  await expect(ana.getByRole('button')).toHaveCount(0)

  await lista.getByRole('button', { name: 'Tornar admin: caio@escritorio.adv.br' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'agora é' })).toHaveText('caio@escritorio.adv.br agora é admin.')
  await expect(lista.getByRole('button', { name: 'Tornar membro: caio@escritorio.adv.br' })).toBeFocused()

  await lista.getByRole('button', { name: 'Desativar caio@escritorio.adv.br' }).click()
  const confirmar = lista.getByRole('group', { name: /Desativar caio@escritorio.adv.br\?/ })
  await expect(confirmar.getByRole('button', { name: 'Cancelar' })).toBeFocused()
  expect(api.usuarios.find((u) => u.id === 'u-caio')?.ativo).toBe(true)
  await confirmar.getByRole('button', { name: 'Desativar' }).click()

  await expect(lista.getByRole('row', { name: /caio@/ })).toContainText('Desativado')
  await expect(lista.getByRole('button', { name: 'Reativar caio@escritorio.adv.br' })).toBeFocused()
  expect(api.usuarios.find((u) => u.id === 'u-caio')).toMatchObject({ papel: 'ADMIN', ativo: false })
})

test('o convidado cria a senha pelo link e entra como membro', async ({ page, api }) => {
  api.logado = false
  await page.goto(`/convite#${TOKEN_CONVITE}`)

  await expect(page.getByRole('heading', { name: 'Criar a senha' })).toBeVisible()
  await expect(page.getByText('Convite para o escritório Silva Advogados.')).toBeVisible()
  await expect(page.getByLabel('E-mail')).toHaveValue(EMAIL_CONVIDADA)
  // o token sai da barra de endereço assim que é lido
  expect(new URL(page.url()).hash).toBe('')

  await page.getByLabel('Senha', { exact: true }).fill('uma senha da bia')
  await page.getByLabel('Repita a senha').fill('outra senha')
  await page.getByRole('button', { name: 'Criar a senha e entrar' }).click()
  // com o erro dentro do rótulo, o texto do label muda; o campo se acha pelo autocomplete
  const senha = page.locator('input[autocomplete="new-password"]').first()
  await expect(page.getByText('As duas senhas não são iguais. Digite a mesma senha nos dois campos.')).toBeVisible()
  await expect(senha).toBeFocused()
  await expect(senha).toHaveAttribute('aria-invalid', 'true')
  await expect(senha).toHaveAccessibleDescription('As duas senhas não são iguais. Digite a mesma senha nos dois campos.')
  expect(api.aceites).toEqual([])

  // corrigido, o erro some antes de enviar de novo
  await page.getByLabel('Repita a senha').fill('uma senha da bia')
  await expect(senha).not.toHaveAttribute('aria-invalid')
  await page.getByRole('button', { name: 'Criar a senha e entrar' }).click()

  await expect(page.getByRole('button', { name: 'Calcular', exact: true })).toBeVisible()
  await expect(page.getByText(EMAIL_CONVIDADA)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Usuários' })).toHaveCount(0)
  expect(api.aceites).toEqual([{ token: TOKEN_CONVITE, senha: 'uma senha da bia' }])
})

test('o link vencido ou já usado diz o que fazer e leva ao login', async ({ page, api }) => {
  api.logado = false
  api.conviteValido = null
  await page.goto(`/convite#${TOKEN_CONVITE}`)

  await expect(page.getByRole('alert')).toHaveText(/Este convite não vale mais.*Peça um novo ao admin do escritório\./)
  await page.getByRole('link', { name: 'Ir para o login' }).click()
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
})

test('o link sem o token não chama a API', async ({ page, api }) => {
  api.logado = false
  await page.goto('/convite')

  await expect(page.getByRole('alert')).toHaveText(/O link do convite está incompleto/)
})

for (const [tema, nome] of [
  ['light', 'claro'],
  ['dark', 'escuro'],
] as const) {
  test.describe(`acessibilidade no tema ${nome}`, () => {
    test.use({ colorScheme: tema })

    test('janela de usuários com convite vencido e confirmação', async ({ page }) => {
      await page.goto('/')
      await page.getByRole('button', { name: 'Usuários' }).click()
      const janela = page.getByRole('dialog', { name: 'Usuários' })
      await janela.getByRole('button', { name: 'Desativar caio@escritorio.adv.br' }).click()
      await expect(janela.getByRole('group', { name: /Desativar caio/ })).toBeVisible()
      await semViolacoes(page)
    })

    test('página do convite', async ({ page, api }) => {
      api.logado = false
      await page.goto(`/convite#${TOKEN_CONVITE}`)
      await expect(page.getByLabel('E-mail')).toHaveValue(EMAIL_CONVIDADA)
      await semViolacoes(page)
    })
  })
}
