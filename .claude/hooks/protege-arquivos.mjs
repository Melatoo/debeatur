// PreToolUse: pede a confirmação do dono antes de o agente editar o que decide os valores dos autos
// ou as próprias regras do fluxo. Não bloqueia: devolve "ask", e quem responde é a pessoa.
//
// Cobre só as ferramentas de edição (Edit, Write, MultiEdit, NotebookEdit). Uma edição pelo shell
// passa por fora; a guarda de verdade continua no CI (gabarito.yml) e no review.
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';

const entrada = JSON.parse(readFileSync(0, 'utf8') || '{}');
const raiz = process.env.CLAUDE_PROJECT_DIR || entrada.cwd || process.cwd();
const alvo = entrada.tool_input?.file_path ?? entrada.tool_input?.notebook_path;
if (!alvo) process.exit(0);

const relativo = path.relative(raiz, path.resolve(raiz, alvo)).split(path.sep).join('/');
if (!relativo || relativo.startsWith('..') || path.isAbsolute(relativo)) process.exit(0);
// O NTFS não diferencia maiúsculas: .GitHub/ e Review.md são os mesmos arquivos.
const caminho = relativo.toLowerCase();

// A regra do gabarito é a mesma da guarda do CI, lida do script para não haver duas listas.
function regraDoGabarito() {
  try {
    const script = readFileSync(path.join(raiz, '.github/scripts/guarda-gabarito.sh'), 'utf8');
    const achado = script.match(/^GABARITO='([^']+)'/m);
    return achado ? new RegExp(achado[1], 'i') : null;
  } catch {
    return null;
  }
}

function motivo() {
  const gabarito = regraDoGabarito();
  if (!gabarito) {
    return 'não deu para ler a regra do gabarito em .github/scripts/guarda-gabarito.sh; confira o arquivo antes de liberar.';
  }
  if (gabarito.test(caminho)) {
    return 'gabarito ou convenção do motor. O valor esperado vem de fonte externa (cálculo homologado, contadoria do TJMG, planilha da DPMG), nunca do próprio motor.';
  }
  if (caminho.startsWith('debeatur-api/src/main/resources/db/migration/') && existsSync(path.resolve(raiz, alvo))) {
    return 'migração já existente. Migração mergeada não se edita: o Flyway confere o checksum. Crie uma nova.';
  }
  if (caminho.startsWith('.github/')) {
    return 'CI, guardas ou template de PR. Risco alto: o merge é do dono.';
  }
  if (/^\.claude\/(settings\.json|hooks\/|skills\/|agents\/)/.test(caminho) || /^(claude|review)\.md$/.test(caminho)) {
    return 'regras do próprio agente (CLAUDE.md, REVIEW.md, settings, hooks, skills ou agentes). Risco alto: o merge é do dono.';
  }
  return null;
}

const porque = motivo();
if (porque) {
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'ask',
      permissionDecisionReason: `${relativo}: ${porque}`,
    },
  }));
}
process.exit(0);
