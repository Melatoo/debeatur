import { expect, semViolacoes, test } from './apoio.ts'

const TITULO = 'Alimentos de A. B. C. até 30/06/2025'

test('a busca acha pelo título e pelo número do processo, com ou sem a máscara', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  const busca = janela.getByRole('searchbox', { name: 'Buscar' })
  await expect(busca).toBeFocused()
  await expect(janela.getByRole('row')).toHaveCount(3)
  await expect(janela.getByRole('cell', { name: '5001234-56.2024.8.13.0024' })).toBeVisible()

  await busca.fill('despesas escolares')
  await expect(janela.getByRole('row')).toHaveCount(2)
  await expect(janela.getByRole('rowheader', { name: /Despesas escolares de D. E./ })).toBeVisible()
  await expect(janela.getByRole('status').filter({ hasText: '1 de 2 cálculos' })).toBeVisible()

  await busca.fill('50012345620248130024')
  await expect(janela.getByRole('rowheader', { name: new RegExp(TITULO) })).toBeVisible()
  await expect(janela.getByRole('row')).toHaveCount(2)

  await busca.fill('9999')
  await expect(janela.getByRole('table')).toBeHidden()
  await expect(janela.getByText('Nenhum cálculo com “9999”.')).toBeVisible()
})

test('o admin vê quem salvou cada cálculo e busca pelo e-mail', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await expect(janela.getByRole('columnheader', { name: 'Salvo por' })).toBeVisible()
  await expect(janela.getByRole('cell', { name: 'caio@escritorio.adv.br' })).toBeVisible()

  await janela.getByRole('searchbox', { name: 'Buscar' }).fill('caio')
  await expect(janela.getByRole('row')).toHaveCount(2)
  await expect(janela.getByRole('rowheader', { name: /Despesas escolares de D. E./ })).toBeVisible()
  await semViolacoes(page)
})

test('o membro, que só vê os próprios cálculos, não tem a coluna de quem salvou', async ({ page, api }) => {
  api.papel = 'MEMBRO'
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await expect(janela.getByRole('row')).toHaveCount(3)
  await expect(janela.getByRole('columnheader', { name: 'Salvo por' })).toHaveCount(0)
  await expect(janela.getByText('pelo e-mail de quem salvou')).toHaveCount(0)
})

test('renomear troca o título na lista e no pé do formulário', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await janela.getByRole('button', { name: `Abrir ${TITULO}` }).click()
  await expect(page.getByText(`Cálculo salvo “${TITULO}”`)).toBeVisible()

  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  await janela.getByRole('button', { name: `Renomear ${TITULO}` }).click()
  const campo = janela.getByLabel(`Novo título de “${TITULO}”`)
  await expect(campo).toBeFocused()
  await expect(campo).toHaveValue(TITULO)

  // Esc desiste da edição sem fechar a janela.
  await campo.press('Escape')
  await expect(janela).toBeVisible()
  await expect(janela.getByRole('button', { name: `Renomear ${TITULO}` })).toBeFocused()

  await janela.getByRole('button', { name: `Renomear ${TITULO}` }).click()
  await campo.fill('Execução de alimentos de A. B. C.')
  await campo.press('Enter')
  await expect(janela.getByRole('rowheader', { name: /^Execução de alimentos de A. B. C./ })).toBeVisible()
  await expect(janela.getByRole('status').filter({ hasText: 'agora se chama' })).toBeVisible()
  await expect(janela.getByRole('button', { name: 'Renomear Execução de alimentos de A. B. C.' })).toBeFocused()

  await janela.getByRole('button', { name: 'Fechar' }).click()
  await expect(page.getByText('Cálculo salvo “Execução de alimentos de A. B. C.”')).toBeVisible()
})

test('excluir pede confirmação e, com o cálculo na tela, deixa de tratá-lo como salvo', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await janela.getByRole('button', { name: `Abrir ${TITULO}` }).click()
  await expect(page.getByRole('button', { name: 'Salvo', exact: true })).toBeDisabled()

  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  await janela.getByRole('button', { name: `Excluir ${TITULO}` }).click()
  const pergunta = janela.getByRole('group', { name: /Excluir este cálculo\?/ })
  await expect(pergunta.getByRole('button', { name: 'Cancelar' })).toBeFocused()
  await semViolacoes(page)

  await pergunta.getByRole('button', { name: 'Cancelar' }).click()
  await expect(pergunta).toBeHidden()
  expect(api.excluidos).toEqual([])

  await janela.getByRole('button', { name: `Excluir ${TITULO}` }).click()
  await pergunta.getByRole('button', { name: 'Excluir', exact: true }).click()
  await expect(janela.getByRole('rowheader', { name: new RegExp(TITULO) })).toBeHidden()
  await expect(janela.getByRole('row')).toHaveCount(2)
  await expect(janela.getByRole('status').filter({ hasText: `“${TITULO}” foi excluído.` })).toBeVisible()
  await expect(janela.getByRole('searchbox', { name: 'Buscar' })).toBeFocused()
  expect(api.excluidos).toHaveLength(1)

  // Os números da tela vinham do snapshot excluído: refeitos com os índices de hoje, o Salvar volta.
  await janela.getByRole('button', { name: 'Fechar' }).click()
  await expect(page.getByText(/que foi excluído: os números usam os índices de hoje/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Salvar', exact: true })).toBeEnabled()
  expect(api.pedidos).toHaveLength(1)
})

test('o Desfazer que devolve um cálculo já excluído refaz os números com os índices de hoje', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await janela.getByRole('button', { name: `Abrir ${TITULO}` }).click()
  await expect(page.getByRole('button', { name: 'Salvo', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Limpar', exact: true }).click()

  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  await janela.getByRole('button', { name: `Excluir ${TITULO}` }).click()
  await janela.getByRole('button', { name: 'Excluir', exact: true }).click()
  await expect(janela.getByRole('status').filter({ hasText: 'foi excluído.' })).toBeVisible()
  await janela.getByRole('button', { name: 'Fechar' }).click()
  expect(api.pedidos).toHaveLength(0)

  await page.getByRole('button', { name: 'Desfazer' }).click()
  await expect(page.getByText(/que foi excluído: os números usam os índices de hoje/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Salvar', exact: true })).toBeEnabled()
  expect(api.pedidos).toHaveLength(1)
})

test('o título vazio acusa no próprio campo, sem esconder a busca', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await janela.getByRole('searchbox', { name: 'Buscar' }).fill('alimentos')
  await janela.getByRole('button', { name: `Renomear ${TITULO}` }).click()
  const campo = janela.getByLabel(`Novo título de “${TITULO}”`)
  await campo.fill('   ')
  await campo.press('Enter')

  await expect(campo).toHaveAttribute('aria-invalid', 'true')
  await expect(campo).toHaveAccessibleDescription(/Informe o novo título/)
  await expect(campo).toBeFocused()
  await expect(janela.getByRole('searchbox', { name: 'Buscar' })).toHaveValue('alimentos')
  await semViolacoes(page)

  await campo.fill('Execução de A. B. C.')
  await expect(campo).not.toHaveAttribute('aria-invalid')
})

test('o cabeçalho de cada linha tem só o título e o regime, também ao renomear e ao excluir', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  // O leitor de tela repete o cabeçalho da linha em cada célula: um controle ali seria lido com cada valor.
  const cabecalho = janela.getByRole('rowheader', { name: new RegExp(TITULO) })
  const semControles = async () => {
    await expect(cabecalho.getByRole('button')).toHaveCount(0)
    await expect(cabecalho.getByRole('textbox')).toHaveCount(0)
    await expect(cabecalho).not.toContainText(/Renomear|Excluir|Cancelar/)
  }
  await semControles()

  await janela.getByRole('button', { name: `Renomear ${TITULO}` }).click()
  await expect(janela.getByLabel(`Novo título de “${TITULO}”`)).toBeFocused()
  await semControles()
  await janela.getByLabel(`Novo título de “${TITULO}”`).press('Escape')

  await janela.getByRole('button', { name: `Excluir ${TITULO}` }).click()
  await expect(janela.getByRole('group', { name: /Excluir este cálculo\?/ })).toBeVisible()
  await semControles()
  await semViolacoes(page)
})

test('o PDF de um cálculo excluído em outra janela avisa e deixa de tratá-lo como salvo', async ({ page, api }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Cálculos salvos' }).click()
  const janela = page.getByRole('dialog', { name: 'Cálculos salvos' })
  await janela.getByRole('button', { name: `Abrir ${TITULO}` }).click()
  await expect(page.getByRole('button', { name: 'Salvo', exact: true })).toBeDisabled()

  api.excluidoEmOutraJanela = true
  await page.getByRole('button', { name: 'Baixar PDF' }).click()
  // O aviso continua depois do recálculo com os índices de hoje, que troca o objeto do pedido.
  await expect.poll(() => api.pedidos.length).toBe(1)
  await expect(page.getByText(/que foi excluído: os números usam os índices de hoje/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Salvar', exact: true })).toBeEnabled()
  await expect(page.getByRole('status').filter({ hasText: /Este cálculo salvo foi excluído em outra janela/ })).toBeVisible()
})
