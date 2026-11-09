# Debeatur

Cálculo de cumprimento de sentença (art. 524 do CPC). Escopo do MVP em `docs/requisitos-mvp.md`.

## Build

- Backend: `./mvnw verify` (exige JDK 25; o enforcer falha com versões anteriores)
- Frontend: `cd frontend && npm ci && npm run lint && npm test && npm run build`
- Tudo junto: `docker compose up -d --build` (http://localhost:3000)

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
- Código, nomes e mensagens de commit em português, seguindo o estilo existente. A exceção são
  os PRs do Dependabot, que mantêm as mensagens padrão dele.
