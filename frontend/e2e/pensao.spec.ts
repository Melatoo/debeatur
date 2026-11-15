import { expect, semViolacoes, test } from './apoio.ts'

test('pensão em rendimentos com 13º e abatimento vai à API e volta no demonstrativo', async ({ page, api }) => {
  api.calculo = { status: 200, arquivo: 'demonstrativo-rendimentos.json' }
  await page.goto('/')

  await page.getByLabel('Valor fixado no título').selectOption('RENDIMENTOS')
  await page.getByLabel('% dos rendimentos').fill('30')
  await page.getByLabel('Rendimentos (R$)').fill('3.000,00')
  await page.getByLabel('Incluir o 13º salário').check()
  await page.getByLabel('Primeira parcela').fill('2024-01')
  await page.getByLabel('Última parcela').fill('2025-06')
  await page.getByRole('button', { name: 'Adicionar abatimento' }).click()
  await page.getByLabel('Data do recebimento').fill('2025-07-10')
  await page.getByLabel('Valor (R$)').fill('500,00')
  await page.getByLabel('Origem (opcional)').fill('Bloqueio em conta')
  await page.getByLabel('Data-base do cálculo').fill('2025-06-30')
  await page.getByLabel('Ajuizamento da execução').fill('2024-09-15')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()

  expect(api.pedidos.at(-1)).toMatchObject({
    pensao: {
      base: 'RENDIMENTOS',
      percentual: '30',
      rendimentos: '3000.00',
      decimoTerceiro: true,
      abatimentos: [{ data: '2025-07-10', valor: '500.00', origem: 'Bloqueio em conta' }],
      ajuizamento: '2024-09-15',
    },
  })

  const parametros = page.locator('.parametros')
  await expect(parametros).toContainText('30% dos rendimentos de R$ 3.000,00, iguais em todos os meses')
  await expect(parametros).toContainText('Incluído: a parcela de dezembro vale o dobro')
  await expect(parametros).toContainText('10/07/2025 (Bloqueio em conta): R$ 500,00')
  await expect(page.getByRole('columnheader', { name: 'Salário mínimo' })).toHaveCount(0)
  await expect(page.getByRole('rowheader', { name: '12/2024 e 13º' })).toBeVisible()

  const penhora = page.locator('#tabela-penhora').locator('..')
  await expect(penhora.getByRole('row', { name: /Abatimento de 10\/07\/2025 \(Bloqueio em conta\)/ })).toContainText('-500,00')
  await expect(penhora.getByRole('row', { name: /Total da penhora/ })).toContainText('6.694,53')
  await expect(page.locator('.total-topo')).toContainText('R$ 18.727,85')
  await semViolacoes(page)
})

test('pensão em valor fixo recusa o primeiro período depois da primeira parcela', async ({ page, api }) => {
  await page.goto('/')

  await page.getByLabel('Valor fixado no título').selectOption('VALOR_FIXO')
  await page.getByLabel('A partir de').fill('2024-03')
  await page.getByLabel('Valor mensal (R$)').fill('500,00')
  await page.getByLabel('Primeira parcela').fill('2024-01')
  await page.getByLabel('Última parcela').fill('2024-06')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()

  await expect(page.getByText('O primeiro valor começa em 03/2024, depois da primeira parcela (01/2024).').first()).toBeVisible()
  expect(api.pedidos).toEqual([])
})
