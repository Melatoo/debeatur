import { calcularCaso, expect, test } from './apoio.ts'

// A área útil do A4 com as margens de 12mm do `@page`: 186mm a 96dpi. A captura mostra a página
// como uma folha só, sem as quebras, mas pega tudo o que o tema `impressao` muda.
test.use({ viewport: { width: 703, height: 1000 } })

test('demonstrativo impresso', async ({ page }) => {
  await calcularCaso(page)
  await page.emulateMedia({ media: 'print' })
  await expect(page).toHaveScreenshot('demonstrativo.png', { fullPage: true })
})

// O tema `impressao` redefine todas as cores: um token esquecido levaria o valor escuro ao papel.
// A comparação é com a impressão clara da mesma página, e não com `demonstrativo.png`: com
// `--update-snapshots`, a captura escura regravaria a referência e o defeito passaria a ser o esperado.
test('com o sistema no tema escuro, imprime igual ao tema claro', async ({ page }) => {
  await calcularCaso(page)
  await page.emulateMedia({ media: 'print', colorScheme: 'light' })
  const claro = await page.screenshot({ fullPage: true })
  await page.emulateMedia({ colorScheme: 'dark' })
  const escuro = await page.screenshot({ fullPage: true })
  expect(escuro.equals(claro), 'a impressão no tema escuro difere da do tema claro').toBe(true)
})

test('demonstrativo desatualizado impresso', async ({ page, api }) => {
  await calcularCaso(page)
  api.calculo = { status: 422, arquivo: 'serie-indisponivel.json' }
  await page.getByLabel('Data-base do cálculo').fill('2026-12-31')
  await expect(page.getByText('Demonstrativo desatualizado.')).toBeVisible()
  await page.emulateMedia({ media: 'print' })
  await expect(page).toHaveScreenshot('desatualizado.png', { fullPage: true })
})

test('memória com colunas escondidas', async ({ page }) => {
  await calcularCaso(page)
  await page.getByText(/^Colunas \(/).click()
  for (const coluna of ['Vencimento', 'Salário mínimo', 'Juros (%)']) {
    await page.getByRole('checkbox', { name: coluna, exact: true }).uncheck()
  }
  await page.emulateMedia({ media: 'print' })
  await expect(page).toHaveScreenshot('colunas-escondidas.png', { fullPage: true })
})
