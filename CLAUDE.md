# Debeatur

Cálculo de cumprimento de sentença (art. 524 do CPC). Escopo do MVP em `docs/requisitos-mvp.md`.

## Build

- Backend: `mvn verify` (exige JDK 25; o enforcer falha com versões anteriores)
- Frontend: `cd frontend && npm ci && npm run lint && npm run build`
- Tudo junto: `docker compose up -d --build` (http://localhost:3000)

## Regras do domínio

- **O motor é a parte crítica.** O `debeatur-core` produz valores que vão para os autos. Não
  altere as convenções do `MotorCalculo` (granularidade, fator, juros, arredondamento) sem um
  teste de gabarito que justifique a mudança.
- **Gabarito vem de fora.** Os valores esperados dos testes de gabarito saem de cálculos
  homologados ou da contadoria do TJMG, nunca do próprio motor. Não gere nem "corrija" valores
  esperados para fazer um teste passar.
- **Dinheiro:** sempre `Dinheiro`/`BigDecimal`, nunca `double`. HALF_UP em duas casas a cada
  valor monetário; fatores de correção não são arredondados.
- **O regime é um dado do cálculo.** A sentença pode fixar critérios próprios (coisa julgada),
  então nada de regra de atualização fixa no código fora de `RegimeAtualizacao`.
- **Tribunal de referência:** TJMG. Fazenda Pública e trabalhista estão fora do escopo.
- O `debeatur-core` não depende de framework; Spring fica só no `debeatur-api`.

## Fluxo de trabalho

- Nada de push direto na `main`: tudo passa por PR com o CI verde.
- Mudanças no `debeatur-core`, na autenticação e na persistência são revisadas linha a linha;
  mantenha esses PRs pequenos.
- Código, nomes e mensagens de commit em português, seguindo o estilo existente.
