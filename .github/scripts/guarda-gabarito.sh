#!/usr/bin/env bash
# Guarda do gabarito: o PR que mexe no que decide os valores dos autos precisa dizer de onde vêm.
#
# Uso: guarda-gabarito.sh <arquivo com os caminhos alterados, um por linha> <arquivo com a descrição do PR>
#
# - Mexeu no gabarito (valores esperados, séries fixadas) ou nas convenções do motor
#   (Dinheiro, MotorCalculo, RegimeAtualizacao)? A descrição precisa de uma seção com
#   "gabarito" no título, preenchida com a fonte externa: cálculo homologado, CADEJ, tabela da
#   CGJ, planilha da DPMG.
# - Mexeu nas referências da impressão? A descrição precisa de uma seção com "impressão" no
#   título dizendo o que mudou nas imagens e por quê.
#
# A regra é do CLAUDE.md ("Gabarito vem de fora"). Este script só faz o PR declarar a fonte; quem
# confere se a fonte é boa é o review.
set -euo pipefail

arquivos="$1"
descricao="$2"

GABARITO='^debeatur-[a-z]+/src/test/resources/gabarito/|^debeatur-[a-z]+/src/test/java/.*/gabarito/|/Gabarito[A-Za-z]*Test\.java$|^debeatur-core/src/main/java/br/com/debeatur/core/(Dinheiro|calculo/MotorCalculo|regime/RegimeAtualizacao)\.java$'
IMPRESSAO='^frontend/e2e/referencias/'

# Imprime as linhas com conteúdo das seções "## ..." cujo título contém a palavra-chave (ex.:
# "## Gabarito", "## Convenção e gabarito"), ignorando linhas em branco e comentários HTML de uma
# linha (as dicas do template).
secao() {
  awk -v chave="$1" '
    { sub(/\r$/, "") }
    /^## / { dentro = (index(tolower($0), chave) > 0); next }
    dentro && $0 !~ /^[[:space:]]*$/ && $0 !~ /^[[:space:]]*<!--.*-->[[:space:]]*$/ { print }
  ' "$descricao"
}

falhou=0

exigir() {
  local regra="$1" padrao="$2" titulo="$3" chave="$4" pede="$5"
  local tocados
  tocados=$(grep -E "$padrao" "$arquivos" || true)
  if [ -z "$tocados" ]; then
    echo "✓ $regra: o PR não mexe nesses arquivos."
    return
  fi
  if [ -n "$(secao "$chave")" ]; then
    echo "✓ $regra: a seção \"## $titulo\" está preenchida."
    return
  fi
  falhou=1
  echo "::error title=Falta a seção ## $titulo::$pede"
  echo "✗ $regra: o PR mexe em"
  echo "$tocados" | sed 's/^/    /'
  echo "  e a descrição não tem a seção \"## $titulo\" preenchida. $pede"
}

exigir "Gabarito" "$GABARITO" "Gabarito" "gabarito" \
  "Diga de onde vêm os valores esperados (cálculo homologado, CADEJ, CGJ-TJMG, planilha da DPMG), com data, ou por que a mudança no motor não altera nenhum valor do gabarito."
exigir "Impressão" "$IMPRESSAO" "Impressão" "impress" \
  "Diga o que mudou nas imagens de referência e por que a mudança é intencional."

exit "$falhou"
