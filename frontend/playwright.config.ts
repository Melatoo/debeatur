import { defineConfig, devices } from '@playwright/test'

/**
 * Testes de ponta a ponta: acessibilidade (axe) e comparação visual da impressão. A API é
 * simulada com as respostas gravadas em `e2e/fixtures`, para os números não dependerem do SGS.
 *
 * As imagens de referência são da imagem oficial do Playwright (Linux), e só lá são comparadas:
 * em outro sistema, a fonte sai com outro antialiasing. `npm run e2e` roda tudo nela, como o CI;
 * `npx playwright test` roda na máquina, sem as comparações visuais.
 */
export default defineConfig({
  testDir: 'e2e',
  snapshotPathTemplate: '{testDir}/referencias/{testFileName}/{arg}{ext}',
  ignoreSnapshots: !process.env.E2E_NA_IMAGEM,
  forbidOnly: !!process.env.CI,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run build && npm run preview -- --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
  },
})
