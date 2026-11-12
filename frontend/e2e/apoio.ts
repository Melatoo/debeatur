import AxeBuilder from '@axe-core/playwright'
import { test as base, expect, type Page, type Route } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * Hoje, nos testes. A data-base padrão fica em 31/10/2026, e outubro de 2026 é o mês do IPCA que
 * ainda não saiu em `serie-indisponivel.json`.
 */
export const HOJE = new Date('2026-11-11T14:00:00-03:00')

const caminho = (nome: string) => fileURLToPath(new URL(`./fixtures/${nome}`, import.meta.url))
export const fixture = <T>(nome: string): T => JSON.parse(readFileSync(caminho(nome), 'utf8'))

const ID_SALVO = fixture<{ id: string }>('calculo-salvo.json').id

export const EMAIL = 'ana@escritorio.adv.br'
export const SENHA = 'uma senha longa'

export interface ApiSimulada {
  /** Se há sessão; começa logado, e o teste de login o desliga antes de abrir a página. */
  logado: boolean
  /** Se a próxima chamada fora de /api/sessao responde 401, como quando a sessão expira. */
  expirar: boolean
  /** O status do GET /api/sessao quando não é o normal (500 simula a API fora do ar). */
  falhaSessao: number | null
  /** O status do DELETE /api/sessao quando não é o normal. */
  falhaSair: number | null
  /** Se o POST /api/sessao responde 429, como depois de muitas falhas. */
  bloquearLogin: boolean
  /** A resposta do POST /api/calculos; o teste a troca para simular uma falha. */
  calculo: { status: number; arquivo: string }
  /** Os pedidos de cálculo recebidos, na ordem. */
  pedidos: unknown[]
  /** Chamadas que nenhuma rota simulada atendeu; o teste falha se sobrar alguma. */
  inesperadas: string[]
  /** POST e DELETE sem o token CSRF do cookie no cabeçalho; o teste falha se sobrar algum. */
  semToken: string[]
}

/**
 * A API simulada faz o CSRF como o Spring: o GET da sessão grava o cookie XSRF-TOKEN se não houver
 * um, o login troca o token, sair o apaga, e POST e DELETE sem o mesmo valor no cabeçalho
 * X-XSRF-TOKEN levam 403.
 */
async function simularApi(page: Page): Promise<ApiSimulada> {
  const api: ApiSimulada = {
    logado: true,
    expirar: false,
    falhaSessao: null,
    falhaSair: null,
    bloquearLogin: false,
    calculo: { status: 200, arquivo: 'demonstrativo.json' },
    pedidos: [],
    inesperadas: [],
    semToken: [],
  }
  let emitidos = 0
  const responder = (route: Route, arquivo: string, status = 200) =>
    route.fulfill({
      status,
      contentType: status < 400 ? 'application/json' : 'application/problem+json',
      path: caminho(arquivo),
    })
  const naoAutenticado = (route: Route) => route.fulfill({ status: 401, body: '' })
  const tokenNoCookie = async () =>
    (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN')?.value ?? null
  const gravarToken = (origem: string) =>
    page.context().addCookies([{ name: 'XSRF-TOKEN', value: `token-${++emitidos}`, url: origem }])
  await page.route('**/api/**', async (route) => {
    const pedido = route.request()
    const url = new URL(pedido.url())
    const chave = `${pedido.method()} ${url.pathname}`
    if (pedido.method() !== 'GET') {
      const token = await tokenNoCookie()
      if (!token || (await pedido.headerValue('x-xsrf-token')) !== token) {
        api.semToken.push(chave)
        return route.fulfill({ status: 403, body: '' })
      }
    }
    if (!chave.endsWith(' /api/sessao') && (!api.logado || api.expirar)) {
      api.expirar = false
      api.logado = false
      return naoAutenticado(route)
    }
    switch (chave) {
      case 'GET /api/sessao':
        if (api.falhaSessao) return route.fulfill({ status: api.falhaSessao, body: '' })
        if (!(await tokenNoCookie())) await gravarToken(url.origin)
        return api.logado ? route.fulfill({ json: { email: EMAIL } }) : naoAutenticado(route)
      case 'POST /api/sessao': {
        const { email, senha } = pedido.postDataJSON()
        if (api.bloquearLogin) {
          return route.fulfill({
            status: 429,
            headers: { 'Retry-After': '840' },
            contentType: 'application/problem+json',
            json: { status: 429, detail: 'Muitas tentativas de entrar. Tente de novo em 14 minutos.' },
          })
        }
        if (email !== EMAIL || senha !== SENHA) {
          return route.fulfill({
            status: 401,
            contentType: 'application/problem+json',
            json: { status: 401, detail: 'E-mail ou senha incorretos. Confira e tente de novo.' },
          })
        }
        api.logado = true
        await gravarToken(url.origin)
        return route.fulfill({ json: { email: EMAIL } })
      }
      case 'DELETE /api/sessao':
        if (api.falhaSair) return route.fulfill({ status: api.falhaSair, body: '' })
        api.logado = false
        await page.context().clearCookies({ name: 'XSRF-TOKEN' })
        return route.fulfill({ status: 204, body: '' })
      case 'GET /api/regimes/padrao':
        return responder(route, 'regime-padrao.json')
      case 'POST /api/calculos':
        api.pedidos.push(pedido.postDataJSON())
        return responder(route, api.calculo.arquivo, api.calculo.status)
      case 'GET /api/calculos/salvos':
        return responder(route, 'calculos-salvos.json')
      case `GET /api/calculos/salvos/${ID_SALVO}`:
        return responder(route, 'calculo-salvo.json')
      case 'POST /api/calculos/salvos':
        return responder(route, 'calculo-salvo.json', 201)
      default:
        api.inesperadas.push(chave)
        return route.fulfill({ status: 501, json: { detail: `Sem simulação para ${chave}` } })
    }
  })
  return api
}

/** O `test` do Playwright com o relógio parado em `HOJE` e a API simulada em toda página. */
export const test = base.extend<{ api: ApiSimulada }>({
  api: [
    async ({ page }, use) => {
      await page.clock.setFixedTime(HOJE)
      const api = await simularApi(page)
      await use(api)
      expect(api.inesperadas, 'chamadas à API sem simulação').toEqual([])
      expect(api.semToken, 'POST e DELETE sem o token CSRF').toEqual([])
    },
    { auto: true },
  ],
})
export { expect }

/** Preenche o caso de `pedido.json`: pensão de 30% do salário mínimo, ritos separados e dois pagamentos. */
export async function preencherCaso(page: Page) {
  await page.getByLabel('% do salário mínimo').fill('30')
  await page.getByLabel('Primeira parcela').fill('2024-01')
  await page.getByLabel('Última parcela').fill('2025-06')
  for (const [mes, valor] of [
    ['2024-03', '200,00'],
    ['2024-11', '300,00'],
  ]) {
    await page.getByRole('button', { name: 'Adicionar pagamento' }).click()
    await page.getByLabel('Mês da parcela').last().fill(mes)
    await page.getByLabel('Valor pago (R$)').last().fill(valor)
  }
  await page.getByLabel('Número do processo').fill('5001234-56.2024.8.13.0024')
  await page.getByLabel('Juízo').fill('2ª Vara de Família de Belo Horizonte')
  await page.getByLabel('Exequente').fill('A. B. C., representada por D. B. C.')
  await page.getByLabel('Executado').fill('E. F. C.')
  await page.getByLabel('Data-base do cálculo').fill('2025-06-30')
  await page.getByLabel('Ajuizamento da execução').fill('2024-09-15')
}

/** Abre a página, preenche o caso e calcula, até o demonstrativo aparecer. */
export async function calcularCaso(page: Page) {
  await page.goto('/')
  await preencherCaso(page)
  await page.getByRole('button', { name: 'Calcular', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Demonstrativo atualizado até 30/06/2025' })).toBeVisible()
}

/** WCAG 2.2 AA, a referência do `docs/design-system.md`. */
const CRITERIOS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

/**
 * O demonstrativo desatualizado, apagado a 0.4 de propósito (princípio 2 do design system): o
 * contraste dele fica abaixo de 4,5:1. É exceção consciente, e só dessa regra; o resto vale ali.
 */
const APAGADO = ".demonstrativo[data-situacao='desatualizado'] > :not(.situacao, .cabecalho-resultado)"

/** Passa a página pelo axe; a falha lista cada regra violada e onde. */
export async function semViolacoes(page: Page) {
  const pagina = await new AxeBuilder({ page }).withTags(CRITERIOS).exclude(APAGADO).analyze()
  const violacoes = [...pagina.violations]
  if ((await page.locator(APAGADO).count()) > 0) {
    const apagado = await new AxeBuilder({ page }).withTags(CRITERIOS).include(APAGADO).disableRules(['color-contrast']).analyze()
    violacoes.push(...apagado.violations)
  }
  const resumo = violacoes.map(
    (v) => `${v.id} (${v.impact}): ${v.help}\n${v.nodes.map((n) => `  ${n.target.join(' ')}: ${n.failureSummary}`).join('\n')}`,
  )
  expect(resumo).toEqual([])
}
