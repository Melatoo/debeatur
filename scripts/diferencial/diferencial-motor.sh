#!/usr/bin/env bash
# Teste diferencial do motor: roda os mesmos casos sorteados no debeatur-core da base e no da
# branch e compara as saídas linha a linha. Qualquer diferença é uma mudança de valor que o PR
# precisa explicar (seção "Gabarito" ou "Diferencial" da descrição); sem explicação, é bug.
#
# Uso: scripts/diferencial/diferencial-motor.sh [ref da base, padrão origin/main] [casos, padrão 2000]
#
# Saída: 0 sem diferença, 1 com diferença (o diff completo fica em target/diferencial/), 2 quando
# não dá para comparar (o DiferencialMotor não compila contra uma das versões, ou o build falhou).
# Precisa do JDK 25, como o resto do build.
set -euo pipefail

base="${1:-origin/main}"
casos="${2:-2000}"
raiz="$(git rev-parse --show-toplevel)"
saida="$raiz/target/diferencial"
tmp="$(mktemp -d)"
trap 'git -C "$raiz" worktree remove --force "$tmp/base" >/dev/null 2>&1 || true; rm -rf "$tmp"' EXIT

mkdir -p "$saida"
git -C "$raiz" worktree add -q --detach "$tmp/base" "$base"

# O comparador vem da base quando ela já o tem: o PR não pode afrouxar o teste que o julga.
harness="$tmp/base/scripts/diferencial/DiferencialMotor.java"
if [ ! -f "$harness" ]; then
  harness="$raiz/scripts/diferencial/DiferencialMotor.java"
  echo "A base ainda não tem o DiferencialMotor; usando o da branch."
fi

jar_do_core() {
  local dir="$1"
  (cd "$dir" && ./mvnw -B -q -pl debeatur-core package -DskipTests) >"$saida/build-$2.log" 2>&1 || {
    echo "O build do core ($2) falhou; veja $saida/build-$2.log" >&2
    exit 2
  }
  ls "$dir"/debeatur-core/target/debeatur-core-*.jar | grep -v -- '-sources\|-javadoc' | head -1
}

rodar() {
  local jar="$1" nome="$2"
  java -cp "$jar" "$harness" "$casos" >"$saida/$nome.txt" 2>"$saida/$nome.err" || {
    echo "O DiferencialMotor não roda contra o core da $nome (a API pública mudou?); veja $saida/$nome.err" >&2
    exit 2
  }
}

jar_base="$(jar_do_core "$tmp/base" base)"
jar_branch="$(jar_do_core "$raiz" branch)"
rodar "$jar_base" base
rodar "$jar_branch" branch

if diff -u "$saida/base.txt" "$saida/branch.txt" >"$saida/diferencas.diff"; then
  echo "Diferencial do motor: $casos casos, $(wc -l <"$saida/base.txt") linhas, nenhuma diferença contra $base."
  exit 0
fi

mudadas=$(grep -c '^+[^+]' "$saida/diferencas.diff" || true)
afetados=$(grep '^[-+][^-+]' "$saida/diferencas.diff" | cut -d'|' -f1 | sed 's/^[-+]//' | sort -u | wc -l)
echo "Diferencial do motor: $mudadas linhas mudaram em $afetados de $casos casos contra $base."
echo "Diff completo: $saida/diferencas.diff. Primeiras linhas:"
head -40 "$saida/diferencas.diff"
exit 1
