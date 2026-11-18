#!/usr/bin/env bash
# Roda na máquina os mesmos jobs do CI (.github/workflows/ci.yml e gabarito.yml), antes do push.
#
# Uso: scripts/ci-local/ci-local.sh [jobs...] [--descricao arquivo]
#
#   jobs: backend frontend e2e docker fonte diferencial (padrão: os cinco do CI, sem o diferencial)
#   --descricao: arquivo com a descrição do PR para a guarda do gabarito (job "fonte"). Sem ele, usa
#                a descrição do PR aberto desta branch, se houver; senão, uma descrição vazia.
#
# Cada job grava o log em target/ci-local/<job>.log; no fim sai um resumo, e o script sai com 1 se
# algum job falhou. Os jobs rodam em sequência e um vermelho não interrompe os outros, como no CI.
# Todos testam a árvore de trabalho, inclusive o que ainda não foi commitado.
#
# O que fica de fora: o review do Claude (rode o /code-review local, como manda a skill verify) e o
# cache do GitHub. O job "docker" sobe a stack num projeto separado (debeatur-ci), com imagens,
# volume e rede próprios e sem portas no host: não encosta na stack nem no banco do dia a dia.
set -uo pipefail

# Os argumentos são lidos antes do cd para a raiz: um --descricao relativo vale a partir de onde
# o script foi chamado.
jobs=()
descricao=""
while [ $# -gt 0 ]; do
  case "$1" in
    --descricao)
      [ -f "${2:-}" ] || { echo "Arquivo de descrição não encontrado: ${2:-}" >&2; exit 2; }
      descricao="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
      shift 2 ;;
    -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    backend|frontend|e2e|docker|fonte|diferencial) jobs+=("$1"); shift ;;
    *) echo "Job desconhecido: $1 (use backend, frontend, e2e, docker, fonte ou diferencial)" >&2; exit 2 ;;
  esac
done
[ ${#jobs[@]} -eq 0 ] && jobs=(backend frontend e2e docker fonte)

raiz="$(git rev-parse --show-toplevel)"
cd "$raiz"
aqui="scripts/ci-local"
saida="target/ci-local"
mkdir -p "$saida"

# Como no runner do GitHub: com CI=true, o Playwright recusa um test.only esquecido (forbidOnly) e o
# Vitest também (allowOnly), e o e2e/docker.mjs repassa a variável ao container.
export CI=true

# --- JDK 25: o da máquina, o do JAVA_HOME, o instalado pelo Temurin ou, em último caso, o container.
jdk25() { "$1/bin/java" -version 2>&1 | grep -q 'version "25'; }
if ! java -version 2>&1 | grep -q 'version "25'; then
  for candidato in "${JAVA_HOME:-}" /c/Program\ Files/Eclipse\ Adoptium/jdk-25*; do
    if [ -n "$candidato" ] && [ -x "$candidato/bin/java" ] && jdk25 "$candidato"; then
      export JAVA_HOME="$candidato" PATH="$candidato/bin:$PATH"
      break
    fi
  done
fi
if java -version 2>&1 | grep -q 'version "25'; then
  maven() { ./mvnw "$@"; }
else
  echo "Sem JDK 25 na máquina; o Maven roda no container eclipse-temurin:25-jdk."
  mkdir -p "$HOME/.m2"
  caminho() { (cd "$1" && pwd -W 2>/dev/null || pwd); }
  maven() {
    # O socket do Docker vai junto para o Testcontainers dos testes com banco.
    MSYS_NO_PATHCONV=1 docker run --rm -v "$(caminho "$raiz"):/w" -v "$(caminho "$HOME/.m2"):/root/.m2" \
      -v /var/run/docker.sock:/var/run/docker.sock -w /w -e CI eclipse-temurin:25-jdk ./mvnw "$@"
  }
fi

# --- Jobs. Cada um repete os passos do workflow correspondente.

job_backend() { maven -B verify; }

job_frontend() {
  (cd frontend && npm ci && npm run lint && npm test && npm run build)
}

job_e2e() { (cd frontend && npm run e2e); }

compose_ci=(docker compose -p debeatur-ci -f compose.yaml -f "$aqui/compose.sem-portas.yaml")
derrubar_stack_ci() { "${compose_ci[@]}" down -v >/dev/null 2>&1 || true; }

job_docker() {
  # Um Ctrl+C no meio não deixa a stack de teste no ar (ela tem restart: unless-stopped).
  trap 'derrubar_stack_ci; exit 130' INT TERM
  "${compose_ci[@]}" build &&
    "${compose_ci[@]}" up -d --no-build --wait --wait-timeout 120
  local status=$?
  [ $status -ne 0 ] && "${compose_ci[@]}" logs
  "${compose_ci[@]}" down -v
  trap - INT TERM
  return $status
}

job_fonte() {
  local guarda="$saida/guarda-gabarito.sh" arquivos="$saida/arquivos" texto="$saida/descricao"
  rm -f "$guarda" "$arquivos" "$texto"

  # Como no gabarito.yml: o script da guarda vem da main, não da branch.
  git fetch -q origin main || echo "Sem acesso ao origin; usando a origin/main local."
  git rev-parse --verify -q origin/main >/dev/null || {
    echo "Não há origin/main para comparar; a guarda não conferiu nada." >&2
    return 1
  }
  if ! git ls-tree --name-only origin/main .github/scripts/ | grep -qx '.github/scripts/guarda-gabarito.sh'; then
    echo "A main ainda não tem a guarda; nada a conferir."
    return 0
  fi
  # MSYS_NO_PATHCONV: no Git Bash, "origin/main:caminho" vira "origin\main;caminho" sem ele.
  MSYS_NO_PATHCONV=1 git show origin/main:.github/scripts/guarda-gabarito.sh >"$guarda" || return 1

  # O que o PR vai levar: os commits da branch, o que mudou e ainda não foi commitado e os arquivos
  # novos. O CI só vê o que foi commitado; aqui a guarda olha antes.
  {
    git diff --name-only "$(git merge-base origin/main HEAD)"
    git ls-files --others --exclude-standard
  } | sort -u >"$arquivos"

  if [ -n "$descricao" ]; then
    cp "$descricao" "$texto" || return 1
    echo "Descrição de $descricao."
  elif gh pr view --json body --jq .body >"$texto" 2>/dev/null; then
    echo "Descrição do PR aberto desta branch."
  else
    echo "Sem --descricao e sem PR aberto: a guarda confere uma descrição vazia."
    : >"$texto"
  fi
  bash "$guarda" "$arquivos" "$texto"
}

job_diferencial() { bash scripts/diferencial/diferencial-motor.sh; }

# --- Execução e resumo.
resumo=()
falhou=0
for job in "${jobs[@]}"; do
  log="$saida/$job.log"
  printf '\n== %s (log em %s)\n' "$job" "$log"
  inicio=$SECONDS
  "job_$job" >"$log" 2>&1
  status=$?
  duracao=$((SECONDS - inicio))
  if [ $status -eq 0 ]; then
    resumo+=("✓ $job (${duracao}s)")
    tail -3 "$log"
  else
    falhou=1
    resumo+=("✗ $job (${duracao}s, saída $status)")
    tail -40 "$log"
  fi
done

printf '\n== Resumo\n'
printf '%s\n' "${resumo[@]}"
exit $falhou
