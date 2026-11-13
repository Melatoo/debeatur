import { calcularCaso, expect, preencherCaso, test } from './apoio.ts'

const DIA = 'Informe um dia de 1 a 31, só com números (ex.: 10).'
const PERCENTUAL = 'Informe o percentual da sentença (ex.: 30 ou 33,33).'

test('com dois erros ou mais, o resumo lista todos e recebe o foco', async ({ page, api }) => {
  await page.goto('/')
  await preencherCaso(page)
  // O caso da avaliação da issue #72: o navegador mostrava só o primeiro, e só no balão.
  await page.getByLabel('% do salário mínimo').fill('')
  await page.getByLabel('Dia do vencimento').fill('45')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()

  const resumo = page.getByRole('region', { name: 'Corrija 2 campos para calcular' })
  await expect(resumo).toBeFocused()
  await expect(resumo.getByRole('listitem')).toHaveText([`% do salário mínimo: ${PERCENTUAL}`, `Dia do vencimento: ${DIA}`])
  // Cada campo diz o próprio erro, e não só o primeiro.
  await expect(page.getByLabel('% do salário mínimo')).toHaveAccessibleDescription(PERCENTUAL)
  await expect(page.getByLabel('Dia do vencimento')).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByLabel('Dia do vencimento')).toHaveAccessibleDescription(
    `${DIA} Cada parcela vale o percentual do salário mínimo do mês do vencimento.`,
  )
  expect(api.pedidos).toEqual([])

  await resumo.getByRole('link', { name: 'Dia do vencimento' }).click()
  await expect(page.getByLabel('Dia do vencimento')).toBeFocused()

  // Corrigido, o campo deixa de acusar na hora, sem esperar outro Calcular.
  await page.getByLabel('Dia do vencimento').fill('10')
  await expect(page.getByLabel('Dia do vencimento')).not.toHaveAttribute('aria-invalid')
  await expect(page.getByLabel('Dia do vencimento')).toHaveAccessibleDescription(
    'Cada parcela vale o percentual do salário mínimo do mês do vencimento.',
  )
})

test('com um erro só, o foco vai para o campo, com a mensagem escrita', async ({ page, api }) => {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByLabel('Valor pago (R$)').last().fill('')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()

  const campo = page.getByLabel('Valor pago (R$)').last()
  await expect(campo).toBeFocused()
  await expect(campo).toHaveAccessibleDescription('Informe o valor pago (ex.: 350,00).')
  await expect(page.getByRole('region', { name: /^Corrija/ })).toHaveCount(0)
  expect(api.pedidos).toEqual([])

  await campo.fill('300,00')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
})

test('antes de Calcular, só o valor mal escrito acusa, ao sair do campo', async ({ page }) => {
  await page.goto('/')
  // Passar por um obrigatório ainda vazio não acusa nada: a pessoa só está preenchendo.
  await page.getByLabel('% do salário mínimo').focus()
  await page.keyboard.press('Tab')
  await expect(page.getByLabel('% do salário mínimo')).not.toHaveAttribute('aria-invalid')

  await page.getByLabel('Dia do vencimento').fill('45')
  await expect(page.getByText(DIA)).toHaveCount(0)
  await page.keyboard.press('Tab')
  await expect(page.getByText(DIA)).toBeVisible()
})

test('o Limpar tira os erros da tela junto com o caso', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Dia do vencimento').fill('45')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('region', { name: /^Corrija/ })).toBeVisible()
  await page.getByRole('button', { name: 'Limpar' }).click()
  await expect(page.getByRole('region', { name: /^Corrija/ })).toHaveCount(0)
  await expect(page.locator('[aria-invalid="true"]')).toHaveCount(0)
})

test('o resumo some quando o recálculo automático sai, sem outro Calcular', async ({ page }) => {
  await calcularCaso(page)
  await page.getByLabel('% do salário mínimo').fill('')
  await page.getByLabel('Dia do vencimento').fill('45')
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Corrija 2 campos para calcular' })).toBeVisible()

  await page.getByLabel('% do salário mínimo').fill('30')
  await page.getByLabel('Dia do vencimento').fill('10')
  await expect(page.getByRole('region', { name: /^Corrija/ })).toHaveCount(0)
})

test('com um cálculo salvo aberto, o campo que fica inválido acusa na hora', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  await page.getByRole('button', { name: 'Abrir Alimentos de A. B. C. até 30/06/2025' }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()

  await page.getByLabel('% do salário mínimo').fill('')
  await expect(page.getByLabel('% do salário mínimo')).toHaveAccessibleDescription(PERCENTUAL)
})

test('o Ctrl+Enter de dentro do campo inválido lê o erro dele', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)
  const dia = page.getByLabel('Dia do vencimento')
  await dia.fill('45')
  await page.keyboard.press('Control+Enter')
  await expect(dia).toBeFocused()
  await expect(dia).toHaveAccessibleDescription(new RegExp(`^${DIA.replace(/[().]/g, '\\$&')}`))

  // Outro Ctrl+Enter, sem sair do campo, relê a validade: a mensagem acompanha o valor novo.
  await dia.fill('')
  await page.keyboard.press('Control+Enter')
  await expect(dia).toHaveAccessibleDescription(/^Informe o dia do vencimento \(ex\.: 10\)\./)
})

test('o texto colado e não incluído diz por que o cálculo não saiu', async ({ page }) => {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByText('Colar de uma planilha').click()
  await page.getByLabel('Mês e valor pago, um pagamento por linha').fill('04/2024\t100,00')
  await page.getByText('Colar de uma planilha').click()
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()

  const caixa = page.getByLabel('Mês e valor pago, um pagamento por linha')
  await expect(caixa).toBeFocused()
  await expect(caixa).toHaveAccessibleDescription(/^Inclua os pagamentos colados/)
})
