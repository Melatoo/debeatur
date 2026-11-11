import { calcularCaso, expect, fixture, preencherCaso, semViolacoes, test } from './apoio.ts'

test('o formulário monta o pedido das respostas gravadas', async ({ page, api }) => {
  await calcularCaso(page)
  // As respostas simuladas só valem para este pedido: se o formulário mudar o que envia, elas mudam junto.
  expect(api.pedidos).toEqual([fixture('pedido.json')])
  await expect(page.getByText('8.524,83').first()).toBeVisible()
})

for (const [tema, nome] of [
  ['light', 'claro'],
  ['dark', 'escuro'],
] as const) {
  test.describe(`acessibilidade no tema ${nome}`, () => {
    test.use({ colorScheme: tema })

    test('formulário em branco', async ({ page }) => {
      await page.goto('/')
      await expect(page.getByRole('heading', { name: 'Debeatur' })).toBeVisible()
      await semViolacoes(page)
    })

    test('formulário com regime próprio e colagem de pagamentos com erro', async ({ page }) => {
      await page.goto('/')
      await preencherCaso(page)
      await page.getByRole('radio', { name: 'Critérios fixados no título' }).check()
      await page.getByText('Colar de uma planilha').click()
      await page.getByLabel('Mês e valor pago, um pagamento por linha').fill('13/2024\t100,00')
      await page.getByRole('button', { name: 'Incluir pagamentos' }).click()
      await expect(page.locator('.erros-colagem')).toBeVisible()
      await semViolacoes(page)
    })

    test('demonstrativo', async ({ page }) => {
      await calcularCaso(page)
      await page.getByText(/^Colunas \(/).click()
      await semViolacoes(page)
    })

    test('demonstrativo desatualizado pelo índice que não saiu', async ({ page, api }) => {
      await calcularCaso(page)
      api.calculo = { status: 422, arquivo: 'serie-indisponivel.json' }
      await page.getByLabel('Data-base do cálculo').fill('2026-12-31')
      await expect(page.getByText('Demonstrativo desatualizado.')).toBeVisible()
      await expect(page.getByRole('button', { name: 'Usar a data-base 31/10/2026' }).first()).toBeVisible()
      await semViolacoes(page)
    })

    test('índice que não saiu, antes do primeiro cálculo', async ({ page, api }) => {
      api.calculo = { status: 422, arquivo: 'serie-indisponivel.json' }
      await page.goto('/')
      await preencherCaso(page)
      await page.getByRole('button', { name: 'Calcular', exact: true }).click()
      await expect(page.getByRole('alert')).toContainText('IPCA')
      await semViolacoes(page)
    })

    test('série mês a mês de um fator', async ({ page }) => {
      await calcularCaso(page)
      await page.getByRole('button', { name: /ver a série mês a mês/ }).first().click()
      await expect(page.getByRole('dialog', { name: /^Fator de correção/ })).toBeVisible()
      await semViolacoes(page)
    })

    test('janela dos cálculos salvos', async ({ page }) => {
      await page.goto('/')
      await page.getByRole('button', { name: 'Cálculos salvos' }).click()
      await expect(page.getByRole('dialog').getByText('Alimentos de A. B. C. até 30/06/2025')).toBeVisible()
      await semViolacoes(page)
    })

    test('janela de salvar', async ({ page }) => {
      await calcularCaso(page)
      await page.getByRole('button', { name: 'Salvar', exact: true }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await semViolacoes(page)
    })

    test('cálculo salvo aberto', async ({ page }) => {
      await page.goto('/')
      await page.getByRole('button', { name: 'Cálculos salvos' }).click()
      await page.getByRole('button', { name: 'Abrir Alimentos de A. B. C. até 30/06/2025' }).click()
      await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
      await semViolacoes(page)
    })
  })
}

test.describe('acessibilidade na tela de celular', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('demonstrativo', async ({ page }) => {
    await calcularCaso(page)
    await semViolacoes(page)
  })
})
