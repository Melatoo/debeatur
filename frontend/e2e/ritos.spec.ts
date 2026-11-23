import { expect, fixture, preencherCaso, semViolacoes, test } from './apoio.ts'

const DPMG = 'As três últimas do cálculo (DPMG)'
const LEI = /As três até o ajuizamento/

test('a regra dos ritos abre na da DPMG e vai no pedido', async ({ page, api }) => {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByRole('radio', { name: DPMG }).check()
  await expect(page.getByRole('radio', { name: LEI })).not.toBeChecked()
  await semViolacoes(page)

  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
  const pedido = fixture<{ pensao: object }>('pedido.json')
  // Só o pedido importa aqui: a resposta simulada é a da regra da lei.
  expect(api.pedidos).toEqual([{ ...pedido, pensao: { ...pedido.pensao, regraRitos: 'ULTIMOS_MESES_DO_CALCULO' } }])
})

test('o formulário em branco separa os ritos pela regra da DPMG', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('radio', { name: DPMG })).toBeChecked()
  await page.getByLabel('Separar os ritos da prisão e da penhora (art. 528 do CPC)').uncheck()
  await expect(page.getByRole('radio', { name: DPMG })).toHaveCount(0)
})
