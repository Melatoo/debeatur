# Review

Instruções para quem revisa PRs do Debeatur, seja o review automático no PR, o `/code-review`
local ou um subagente. O CLAUDE.md tem as regras do domínio. Este arquivo diz o que procurar.

## O que é falha aqui

- **No `debeatur-core`:** um valor errado que vai para os autos. Arredondamento fora do HALF_UP
  em duas casas, fator arredondado, `double`, mês a mais ou a menos na contagem, regra de
  atualização fixa no código fora de `RegimeAtualizacao`. Qualquer valor esperado de teste de
  gabarito que pareça tirado do próprio motor é **Important**.
- **Na API e na persistência:** um cálculo salvo que não abre mais, um snapshot ignorado em favor
  da série atual, uma migração já mergeada editada, uma rota que não exige login.
- **No frontend:** um valor mostrado diferente do calculado, um rascunho antigo que quebra a tela,
  uma impressão ou PDF sem o total ou sem o aviso de segredo de justiça, uma regressão de
  acessibilidade.
- **Na documentação:** a doc mente. Uma convenção descrita em `docs/gabarito.md`, no README ou
  num Javadoc que o código não segue mais.

Confira os itens de `docs/checklist-confiabilidade.md` cujo gatilho aparece no diff.

## Como reportar

- Comece o resumo pela contagem: "N achados (X Important)". Sem nada a corrigir, diga isso numa
  linha.
- Cada achado traz arquivo e linha e o caso concreto que dá errado (entrada → saída errada). Sem
  caso concreto, não é achado.
- Marque **Important** (precisa corrigir antes do merge), **Nit** (opcional) ou **Pre-existing**
  (já estava na main; não é culpa deste PR).
- No máximo 3 Nits por review.
- Se o mesmo erro aparece em vários lugares, aponte a classe uma vez e liste os lugares.
- Não repita um achado já postado neste PR.
- Depois da terceira rodada no mesmo PR, só o diff novo, e se ainda houver Important, diga que
  vale repensar a abordagem em vez de remendar.

## O que não comentar

- Formatação, nomes e estilo que o lint não pega, a menos que contrariem o CLAUDE.md.
- Cobertura de testes por si só. Teste faltando só é achado quando um caso concreto quebraria
  sem ninguém ver.
- `frontend/package-lock.json`, `frontend/e2e/referencias/*.png` (a guarda do CI cuida das
  imagens), `mvnw`, `mvnw.cmd`, `.mvn/`.
- PRs do Dependabot.
