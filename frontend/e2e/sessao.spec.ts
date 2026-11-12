import { calcularCaso, EMAIL, expect, preencherCaso, SENHA, test } from './apoio.ts'

test('sem sessão, pede login e depois mostra o formulário', async ({ page, api }) => {
  api.logado = false
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  await expect(page.getByLabel('E-mail')).toBeFocused()
  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill(SENHA)
  await page.getByLabel('Senha').press('Enter')

  await expect(page.getByRole('button', { name: 'Calcular', exact: true })).toBeVisible()
  await expect(page.getByText(EMAIL)).toBeVisible()
})

test('com o e-mail ou a senha em branco, nem chama a API e leva o foco ao campo vazio', async ({ page, api }) => {
  api.logado = false
  await page.goto('/')

  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('alert')).toHaveText('Informe o e-mail e a senha.')
  await expect(page.getByLabel('E-mail')).toBeFocused()

  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('E-mail').press('Enter')
  await expect(page.getByLabel('Senha')).toBeFocused()
})

test('a senha errada apaga a senha, mantém o e-mail e volta o foco à senha', async ({ page, api }) => {
  api.logado = false
  await page.goto('/')

  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill('errada')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByRole('alert')).toHaveText('E-mail ou senha incorretos. Confira e tente de novo.')
  await expect(page.getByLabel('E-mail')).toHaveValue(EMAIL)
  await expect(page.getByLabel('Senha')).toHaveValue('')
  await expect(page.getByLabel('Senha')).toBeFocused()
})

test('depois de muitas falhas, mostra só o aviso da API com o tempo de espera', async ({ page, api }) => {
  api.logado = false
  api.bloquearLogin = true
  await page.goto('/')

  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill(SENHA)
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByRole('alert')).toHaveText('Muitas tentativas de entrar. Tente de novo em 14 minutos.')
})

test('sair volta ao login, e dá para entrar de novo com o token renovado', async ({ page }) => {
  await page.goto('/')

  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()

  // sair apagou o cookie do token: o login busca outro antes do POST
  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill(SENHA)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('button', { name: 'Calcular', exact: true })).toBeVisible()
})

test('sair que falha avisa e mantém a sessão', async ({ page, api }) => {
  await page.goto('/')
  api.falhaSair = 500

  await page.getByRole('button', { name: 'Sair' }).click()

  await expect(page.getByRole('alert')).toContainText('Não foi possível sair.')
  await expect(page.getByRole('button', { name: 'Sair' })).toBeEnabled()
})

test('com a API fora do ar na abertura, avisa e tenta de novo', async ({ page, api }) => {
  api.falhaSessao = 500
  await page.goto('/')

  await expect(page.getByRole('alert')).toHaveText('Não foi possível verificar a sessão. Confira a conexão e tente de novo.')
  api.falhaSessao = null
  await page.getByRole('button', { name: 'Tentar de novo' }).click()

  await expect(page.getByRole('button', { name: 'Calcular', exact: true })).toBeVisible()
})

test('a sessão que expira pede login sem perder o caso da tela e recarrega a lista', async ({ page, api }) => {
  await calcularCaso(page)
  api.expirar = true

  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Entrar de novo' })
  await expect(janela).toBeVisible()
  await expect(janela.getByLabel('E-mail')).toHaveValue(EMAIL)
  await expect(janela.getByLabel('E-mail')).not.toBeEditable()
  await expect(janela.getByLabel('Senha')).toBeFocused()
  await janela.getByLabel('Senha').fill(SENHA)
  await janela.getByRole('button', { name: 'Entrar' }).click()

  await expect(janela).toBeHidden()
  await expect(page.getByRole('dialog', { name: 'Cálculos salvos' }).getByText('Alimentos de A. B. C. até 30/06/2025'))
    .toBeVisible()
  await expect(page.getByLabel('Número do processo')).toHaveValue('5001234-56.2024.8.13.0024')
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
})

test('se outra aba saiu, o próximo cálculo pede login em vez de falhar com 403', async ({ page, api }) => {
  await calcularCaso(page)
  // o que o logout em outra aba deixa: sem sessão e sem o cookie do token, que é do navegador inteiro
  api.logado = false
  await page.context().clearCookies({ name: 'XSRF-TOKEN' })

  await page.getByLabel('Data-base do cálculo').fill('2025-05-31')

  await expect(page.getByRole('dialog', { name: 'Entrar de novo' })).toBeVisible()
})

test('o rascunho sobrevive a sair e entrar de novo', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)

  await page.getByRole('button', { name: 'Sair' }).click()
  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill(SENHA)
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByLabel('Número do processo')).toHaveValue('5001234-56.2024.8.13.0024')
})
