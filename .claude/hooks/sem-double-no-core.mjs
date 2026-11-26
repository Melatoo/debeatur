// PostToolUse: depois de editar o código do motor, procura double/float e BigDecimal montado de
// literal com ponto. Achou, sai com 2 e o agente lê o aviso; a edição já aconteceu e ele corrige.
// A regra é a do CLAUDE.md: dinheiro é Dinheiro/BigDecimal, nunca double.
import { readFileSync } from 'node:fs';
import path from 'node:path';

const entrada = JSON.parse(readFileSync(0, 'utf8') || '{}');
const raiz = process.env.CLAUDE_PROJECT_DIR || entrada.cwd || process.cwd();
const alvo = entrada.tool_input?.file_path;
if (!alvo) process.exit(0);

const relativo = path.relative(raiz, path.resolve(raiz, alvo)).split(path.sep).join('/');
if (!/^debeatur-core\/src\/main\/java\/.+\.java$/.test(relativo)) process.exit(0);

let texto;
try {
  texto = readFileSync(path.resolve(raiz, alvo), 'utf8');
} catch {
  process.exit(0);
}

const proibido = /\b(double|float|Double|Float)\b|new\s+BigDecimal\s*\(\s*-?\d+\.\d/;
// Comentário no fim da linha e texto entre aspas não contam ("nunca double" não é código).
const semTexto = (linha) => linha.replace(/"(?:[^"\\]|\\.)*"/g, '""').replace(/\/\/.*$/, '');
const achados = texto
  .split(/\r?\n/)
  .map((linha, i) => ({ linha, numero: i + 1 }))
  .filter(({ linha }) => !/^\s*(\/\/|\*|\/\*)/.test(linha) && proibido.test(semTexto(linha)));

if (achados.length > 0) {
  const lista = achados.map(({ linha, numero }) => `  ${relativo}:${numero}: ${linha.trim()}`).join('\n');
  process.stderr.write(
    `O motor não usa double/float nem BigDecimal de literal com ponto (use Dinheiro, ou BigDecimal de String):\n${lista}\n`,
  );
  process.exit(2);
}
process.exit(0);
