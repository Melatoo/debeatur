#!/usr/bin/env bash
# Teste diferencial do motor: roda os mesmos casos sorteados no debeatur-core da base e no da
# branch e compara as saídas linha a linha. Qualquer diferença é uma mudança de valor que o PR
# precisa explicar (seção "Gabarito" ou "Diferencial" da descrição); sem explicação, é bug.
#
# Uso: scripts/diferencial/diferencial-motor.sh [ref da base, padrão origin/main] [casos, padrão 2000]
#
# Saída: 0 sem diferença, 1 com diferença (o diff completo fica em target/diferencial/), 2 quando
# não dá para comparar (o DiferencialMotor não compila contra uma das versões, ou o build falhou).
# Usa o JDK 25 da máquina; sem ele, roda o Maven e o java no container eclipse-temurin:25-jdk.
set -euo pipefail

base="${1:-origin/main}"
casos="${2:-2000}"
raiz="$(git rev-parse --show-toplevel)"
cd "$raiz"

# Tudo fica dentro de target/diferencial, para um único volume levar a base, a branch e a saída ao
# container. Os caminhos abaixo são relativos à raiz.
saida="target/diferencial"
worktree_base="$saida/base"
git worktree remove --force "$worktree_base" >/dev/null 2>&1 || true
rm -rf "$saida"
mkdir -p "$saida"
trap 'git worktree remove --force "$worktree_base" >/dev/null 2>&1 || true' EXIT

# Uma origin/main velha compararia a branch com uma main antiga, e o que outros PRs já mudaram
# apareceria como diferença desta branch.
if [[ "$base" == origin/* ]]; then
  git fetch -q origin "${base#origin/}" || echo "Sem acesso ao origin; comparando com a $base local." >&2
fi
git worktree add -q --detach "$worktree_base" "$base"

if java -version 2>&1 | grep -q 'version "25'; then
  jdk() { "$@"; }
else
  echo "Sem JDK 25 na máquina; usando o container eclipse-temurin:25-jdk."
  mkdir -p "$HOME/.m2"
  caminho() { (cd "$1" && pwd -W 2>/dev/null || pwd); }
  volume_raiz="$(caminho "$raiz")"
  volume_m2="$(caminho "$HOME/.m2")"
  jdk() {
    MSYS_NO_PATHCONV=1 docker run --rm -v "$volume_raiz:/w" -v "$volume_m2:/root/.m2" -w /w \
      eclipse-temurin:25-jdk "$@"
  }
fi

# O comparador vem da base quando ela já o tem: o PR não pode afrouxar o teste que o julga.
harness="$worktree_base/scripts/diferencial/DiferencialMotor.java"
if [ ! -f "$harness" ]; then
  harness="scripts/diferencial/DiferencialMotor.java"
  echo "A base ainda não tem o DiferencialMotor; usando o da branch."
fi

construir() {
  local dir="$1" nome="$2"
  jdk bash -c "cd '$dir' && ./mvnw -B -q -pl debeatur-core package -DskipTests" >"$saida/build-$nome.log" 2>&1 || {
    echo "O build do core ($nome) falhou; veja $saida/build-$nome.log" >&2
    exit 2
  }
}

rodar() {
  local dir="$1" nome="$2" jar
  jar="$(ls "$dir"/debeatur-core/target/debeatur-core-*.jar | grep -v -- '-sources\|-javadoc' | head -1)"
  jdk java -cp "$jar" "$harness" "$casos" >"$saida/$nome.txt" 2>"$saida/$nome.err" || {
    echo "O DiferencialMotor não roda contra o core da $nome (a API pública mudou?); veja $saida/$nome.err" >&2
    exit 2
  }
}

construir "$worktree_base" base
construir . branch
rodar "$worktree_base" base
rodar . branch

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
