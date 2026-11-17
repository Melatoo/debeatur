# Debeatur

Cálculo de cumprimento de sentença (art. 524 do CPC). Escopo do MVP em `docs/requisitos-mvp.md`.

## Build

- Backend: `./mvnw verify` (exige JDK 25; o enforcer falha com versões anteriores)
- Frontend: `cd frontend && npm ci && npm run lint && npm test && npm run build`
- Ponta a ponta: `cd frontend && npm run e2e` (Playwright + axe, com a API simulada pelas
  respostas de `frontend/e2e/fixtures`). Roda na imagem oficial do Playwright via Docker, onde
  ficam as referências da impressão; depois de mudança visual intencional,
  `npm run e2e -- --update-snapshots` e confira as imagens no diff. `npx playwright test` roda
  na máquina, sem a comparação visual.
- Tudo junto: `docker compose up -d --build` (http://localhost:3000)

## Preview

`.claude/launch.json` define três servidores para o navegador do app:

- `api`: instala os módulos sem testes e sobe a API na porta 8080, contra o banco do Docker
  (`docker compose up -d db` antes). A porta do banco é a do `DEBEATUR_DB_PORTA`, a mesma do
  compose, com padrão 5432. O comando usa `cmd`, então só roda no Windows.
- `frontend`: Vite na porta 5173, com proxy de `/api` para a 8080. Suba a `api` antes.
- `docker`: se conecta ao build do compose já rodando em http://localhost:3000.

O login usa o usuário do escritório que já está no banco; não crie nem altere usuários para testar.

## Regras do domínio

- **O motor é a parte crítica.** O `debeatur-core` produz valores que vão para os autos. Não
  altere as convenções do `MotorCalculo` (granularidade, fator, juros, arredondamento) sem um
  teste de gabarito que justifique a mudança.
- **Gabarito vem de fora.** Os valores esperados dos testes de gabarito saem de cálculos
  homologados ou da contadoria do TJMG, nunca do próprio motor. Não gere nem "corrija" valores
  esperados para fazer um teste passar.
- **Dinheiro:** sempre `Dinheiro`/`BigDecimal`, nunca `double`. HALF_UP em duas casas a cada
  valor monetário; fatores de correção usam precisão `DECIMAL128`, sem arredondamento a casas
  fixas.
- **O regime é um dado do cálculo.** A sentença pode fixar critérios próprios (coisa julgada),
  então nada de regra de atualização fixa no código fora de `RegimeAtualizacao`.
- **Tribunal de referência:** TJMG. Fazenda Pública e trabalhista estão fora do escopo.
- O `debeatur-core` não depende de framework; Spring fica só no `debeatur-api`.

## Design system

- A fonte de verdade é `docs/design-system.md` (regras) com `frontend/src/estilo/tokens.css`
  (valores). Cor nova vira token ali, nunca valor solto no CSS de componente.
- O artifact do design system no claude.ai é vitrine gerada do repo; não se edita lá.

## Fluxo de trabalho

- Nada de push direto na `main`: tudo passa por PR com o CI verde.
- Mudanças no `debeatur-core`, na autenticação e na persistência são revisadas linha a linha;
  mantenha esses PRs pequenos.
- Antes de abrir PR, rode a skill `verify` (`.claude/skills/verify`): ela escolhe as checagens
  pelo diff, roda o app, faz o review em contexto novo e preenche "Como verifiquei".

## Verificação e review

O dono não lê todo diff. Quem decide quanto ele precisa olhar é o risco da **mudança**:

| Risco | O que entra | Merge |
|---|---|---|
| Baixo | Docs, textos da tela, tokens do design system, testes novos que não mudam valor esperado, Dependabot patch/minor | O Claude faz o merge com o CI verde e nenhum achado Important aberto |
| Médio | Telas, API, PDF, frontend em geral, `debeatur-indices` | O Claude faz o merge com o CI verde, review sem Important aberto e a evidência no PR (comandos e screenshot do fluxo) |
| Alto | `MotorCalculo`, `Dinheiro`, `RegimeAtualizacao`, `Juros`, `alimentos/`, valores ou tolerâncias de gabarito, autenticação (`acesso/`, `sessao.ts`), migrações Flyway, `.github/`, `REVIEW.md`, este arquivo, Dependabot major | Só o dono faz o merge, depois de ler linha a linha |

- Na dúvida entre dois níveis, vale o mais alto. Um PR com arquivos de níveis diferentes fica no
  nível do arquivo mais arriscado.
- O review automático comenta mas não aprova. Todo achado Important precisa ser corrigido ou
  respondido com o motivo antes do merge.
- Pare e chame o dono quando a mesma checagem ou o review falhar depois de 3 rodadas de correção,
  ou quando a correção pedir mudança numa regra do domínio.
- Quando algo escapar (revert, correção depois do merge), o PR da correção acrescenta um item em
  `docs/checklist-confiabilidade.md` ou uma linha no `REVIEW.md`.
- Commits levam o trailer `Verificado:` com o que rodou (ou `Verificado: nenhum`).
- Código, nomes e mensagens de commit em português, seguindo o estilo existente. A exceção são
  os PRs do Dependabot, que mantêm as mensagens padrão dele.
