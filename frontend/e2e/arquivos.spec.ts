import { calcularCaso, expect, test } from './apoio.ts'

const TITULO = 'Alimentos de A. B. C. até 30/06/2025'

test('Baixar Excel baixa a planilha do demonstrativo com o processo do formulário', async ({ page, api }) => {
  await calcularCaso(page)

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar Excel' }).click()

  expect((await download).suggestedFilename()).toBe('demonstrativo-5001234-56.2024.8.13.0024-2025-06-30.xlsx')
  expect(api.arquivos).toEqual(['POST /api/calculos/xlsx'])
  await expect(
    page.getByRole('status').filter({ hasText: 'Planilha baixada: demonstrativo-5001234-56.2024.8.13.0024-2025-06-30.xlsx' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Baixar PDF' })).toBeEnabled()
})

test('no celular, as ações do demonstrativo quebram a linha em vez de rolar a página de lado', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await calcularCaso(page)

  await expect(page.getByRole('button', { name: 'Baixar Excel' })).toBeVisible()
  // em texto, porque o tsconfig do e2e não traz os tipos do DOM
  const sobra = await page.evaluate('document.documentElement.scrollWidth - document.documentElement.clientWidth')
  expect(sobra).toBeLessThanOrEqual(0)
})

test('a planilha de um cálculo salvo vem do snapshot dele', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  await page.getByRole('dialog', { name: 'Cálculos salvos' }).getByRole('button', { name: `Abrir ${TITULO}` }).click()
  await expect(page.getByRole('button', { name: 'Salvo', exact: true })).toBeDisabled()

  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar Excel' }).click()

  expect((await download).suggestedFilename()).toBe('demonstrativo-salvo.xlsx')
  expect(api.arquivos).toEqual([expect.stringMatching(/^GET \/api\/calculos\/salvos\/.+\/xlsx$/)])
})
