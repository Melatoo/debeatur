import { expect, preencherCaso, semViolacoes, test } from './apoio.ts'

const FATO = '9 parcelas (01/2022 a 09/2022) podem estar prescritas.'

test('avisa das parcelas vencidas mais de dois anos antes do ajuizamento, sem levar o aviso ao papel', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)
  await expect(page.getByText(/podem? estar prescrita/)).toHaveCount(0)

  // Ajuizamento em 15/09/2024: as vencidas antes de 15/09/2022, até a de 10/09/2022.
  await page.getByLabel('Primeira parcela').fill('2022-01')
  const noFormulario = page.locator('form').getByText(FATO)
  await expect(noFormulario).toBeVisible()
  await expect(page.locator('form .aviso-prescricao')).toContainText('art. 197, II')

  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.locator('.demonstrativo').getByText(FATO)).toBeVisible()
  await semViolacoes(page)

  await page.emulateMedia({ media: 'print' })
  await expect(page.locator('.demonstrativo').getByText(FATO)).toBeHidden()
})

test('sem os ritos separados, conta da data-base e diz isso', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByLabel('Separar os ritos da prisão e da penhora (art. 528 do CPC)').uncheck()
  await page.getByLabel('Primeira parcela').fill('2023-05')
  // Data-base 30/06/2025: vencidas até 10/06/2023.
  await expect(page.locator('form .aviso-prescricao')).toContainText(
    'Se a execução ainda não foi proposta, 2 parcelas (05/2023 a 06/2023) podem estar prescritas. Venceram mais de dois anos antes da data-base (30/06/2025)',
  )
})
