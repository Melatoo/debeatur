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
})

test('com o e-mail ou a senha em branco, nem chama a API', async ({ page, api }) => {
  api.logado = false
  await page.goto('/')

  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByRole('alert')).toHaveText('Informe o e-mail e a senha.')
})

test('a senha errada apaga a senha e mantém o e-mail', async ({ page, api }) => {
  api.logado = false
  await page.goto('/')

  await page.getByLabel('E-mail').fill(EMAIL)
  await page.getByLabel('Senha').fill('errada')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page.getByRole('alert')).toHaveText('E-mail ou senha incorretos.')
  await expect(page.getByLabel('E-mail')).toHaveValue(EMAIL)
  await expect(page.getByLabel('Senha')).toHaveValue('')
})

test('sair volta ao login', async ({ page }) => {
  await page.goto('/')

  await page.getByRole('button', { name: 'Sair' }).click()

  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
})

test('a sessão que expira pede login sem perder o caso da tela', async ({ page, api }) => {
  await calcularCaso(page)
  api.expirar = true

  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Entrar de novo' })
  await expect(janela).toBeVisible()
  await expect(janela.getByLabel('E-mail')).toHaveValue(EMAIL)
  await expect(janela.getByLabel('Senha')).toBeFocused()
  await janela.getByLabel('Senha').fill(SENHA)
  await janela.getByRole('button', { name: 'Entrar' }).click()

  await expect(janela).toBeHidden()
  await expect(page.getByLabel('Número do processo')).toHaveValue('5001234-56.2024.8.13.0024')
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
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
