import type { Page } from '@playwright/test'
import { expect, fixture, preencherCaso, semViolacoes, test } from './apoio.ts'

const TEMA_1368 = /^Título omisso ou “juros legais”: Tema 1368\/STJ/
const OUTROS = 'Outros critérios fixados no título'

/** Marca o Tema 1368 e espera o regime chegar do servidor. */
async function escolherTema1368(page: Page) {
  const resposta = page.waitForResponse('**/api/regimes/tema-1368')
  await page.getByRole('radio', { name: TEMA_1368 }).check()
  await resposta
}

test('título omisso: o pedido leva o regime do Tema 1368 que veio do servidor', async ({ page, api }) => {
  await page.goto('/')
  await preencherCaso(page)
  await escolherTema1368(page)
  await expect(page.getByText('SELIC de 01/2003 a 07/2024, sem outros juros')).toBeVisible()
  // O editor de regras é dos outros critérios; o Tema 1368 não se edita.
  await expect(page.getByLabel('Nome do regime (aparece no demonstrativo)')).toHaveCount(0)
  await semViolacoes(page)

  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
  expect(api.pedidos).toEqual([{ ...fixture<object>('pedido.json'), regime: fixture('regime-tema-1368.json') }])
})

test('a escolha do Tema 1368 volta com o rascunho', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)
  await escolherTema1368(page)
  await page.reload()
  await expect(page.getByRole('radio', { name: TEMA_1368 })).toBeChecked()
})

test('dos outros critérios, o editor começa pelas regras do Tema 1368 e as guarda ao trocar de opção', async ({ page }) => {
  await page.goto('/')
  await escolherTema1368(page)
  await expect(page.getByText('SELIC de 01/2003 a 07/2024, sem outros juros')).toBeVisible()
  await page.getByRole('radio', { name: OUTROS }).check()

  const primeira = page.getByRole('group', { name: 'Período 1' })
  await expect(primeira.locator('select').first()).toHaveValue('SELIC')
  await expect(primeira.getByLabel('Até', { exact: true })).toHaveValue('2024-07')
  await primeira.getByLabel('Até', { exact: true }).fill('2024-06')

  // Ida e volta pelo Tema 1368 não perde o que foi editado nos outros critérios.
  await page.getByRole('radio', { name: TEMA_1368 }).check()
  await page.getByRole('radio', { name: OUTROS }).check()
  await expect(page.getByRole('group', { name: 'Período 1' }).getByLabel('Até', { exact: true })).toHaveValue('2024-06')
})

test('se o regime do Tema 1368 não carrega, o aviso oferece tentar de novo e some quando ele chega', async ({ page, api }) => {
  let falhas = 1
  let liberar = () => {}
  const segunda = new Promise<void>((resolve) => (liberar = resolve))
  await page.route('**/api/regimes/tema-1368', async (route) => {
    if (falhas-- > 0) return route.fulfill({ status: 503 })
    await segunda
    await route.fallback()
  })
  await page.goto('/')
  await preencherCaso(page)
  await page.getByRole('radio', { name: TEMA_1368 }).check()
  const aviso = page.getByRole('alert').filter({ hasText: 'O regime do Tema 1368 não carregou.' })
  await expect(aviso).toBeVisible()

  // O aviso sai quando a nova busca começa; o Calcular clicado antes de o regime chegar espera por ele.
  await aviso.getByRole('button', { name: 'Tentar de novo' }).click()
  await expect(aviso).toHaveCount(0)
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Calculando…' })).toBeDisabled()
  expect(api.pedidos).toHaveLength(0)
  liberar()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(api.pedidos).toEqual([{ ...fixture<object>('pedido.json'), regime: fixture('regime-tema-1368.json') }])
})

test('o Calcular que espera o regime do Tema 1368 desiste se a busca falha de novo', async ({ page, api }) => {
  let liberar = () => {}
  const segunda = new Promise<void>((resolve) => (liberar = resolve))
  let buscas = 0
  await page.route('**/api/regimes/tema-1368', async (route) => {
    if (buscas++ > 0) await segunda
    await route.fulfill({ status: 503 })
  })
  await page.goto('/')
  await preencherCaso(page)
  await page.getByRole('radio', { name: TEMA_1368 }).check()
  const aviso = page.getByRole('alert').filter({ hasText: 'O regime do Tema 1368 não carregou.' })
  await aviso.getByRole('button', { name: 'Tentar de novo' }).click()
  await expect(aviso).toHaveCount(0)
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Calculando…' })).toBeDisabled()

  liberar()
  await expect(aviso).toBeVisible()
  await expect(page.getByRole('button', { name: 'Calcular', exact: true })).toBeEnabled()
  expect(api.pedidos).toHaveLength(0)
})

test('trocar de opção no meio da busca do Tema 1368 não recalcula sem regime', async ({ page, api }) => {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()

  let liberarTema = () => {}
  let liberarPadrao = () => {}
  const tema = new Promise<void>((resolve) => (liberarTema = resolve))
  const padrao = new Promise<void>((resolve) => (liberarPadrao = resolve))
  await page.route('**/api/regimes/tema-1368', async (route) => {
    await tema
    await route.fallback()
  })
  await page.route('**/api/regimes/padrao', async (route) => {
    await padrao
    await route.fallback()
  })
  await page.getByRole('radio', { name: TEMA_1368 }).check()
  await page.getByRole('radio', { name: OUTROS }).check()

  // A resposta do Tema 1368 chega depois da troca: é de uma busca vencida e não libera o recálculo,
  // que continua esperando as regras do padrão além da pausa da digitação.
  const tardia = page.waitForResponse('**/api/regimes/tema-1368')
  liberarTema()
  await tardia
  await page.waitForTimeout(1000)
  expect(api.pedidos).toHaveLength(1)

  liberarPadrao()
  await expect(page.getByRole('group', { name: 'Período 1' }).locator('select').first()).toHaveValue('INPC')
  await expect.poll(() => api.pedidos.length).toBe(2)
  const ultimo = api.pedidos.at(-1) as { regime: { regras: { correcao: string }[] } }
  expect(ultimo.regime.regras[0].correcao).toBe('INPC')
  await expect(page.getByRole('alert')).toHaveCount(0)
})
