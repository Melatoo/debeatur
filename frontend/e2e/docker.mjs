// Roda o Playwright na imagem oficial da versão instalada, a mesma das imagens de referência.
// Os argumentos passam para o `playwright test`: `npm run e2e -- --update-snapshots`.
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const frontend = fileURLToPath(new URL('..', import.meta.url)).replace(/[\\/]$/, '')
const pacote = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'))
const versao = pacote.devDependencies['@playwright/test']
// Com uma faixa (^1.65.0), a versão instalada poderia não ter imagem com a mesma tag.
if (!/^\d+\.\d+\.\d+$/.test(versao)) {
  console.error(`@playwright/test precisa de versão exata no package.json, e não "${versao}".`)
  process.exit(1)
}

const argumentos = [
  'run',
  '--rm',
  '--init',
  '--ipc=host',
  '-v',
  `${frontend}:/app`,
  // O node_modules da máquina pode ser de outro sistema (os binários do Vite e do oxlint são nativos).
  '-v',
  '/app/node_modules',
  '-w',
  '/app',
  '-e',
  'CI',
  '-e',
  'E2E_NA_IMAGEM=1',
  `mcr.microsoft.com/playwright:v${versao}-noble`,
  'sh',
  '-c',
  'npm ci --no-audit --no-fund && npx playwright test "$@"',
  'sh',
  ...process.argv.slice(2),
]
const resultado = spawnSync('docker', argumentos, { stdio: 'inherit' })
if (resultado.error) console.error(resultado.error.message)
process.exit(resultado.status ?? 1)
