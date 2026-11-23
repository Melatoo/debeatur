# Checklist de confiabilidade

Coisas que já quebraram, quase quebraram ou custaram uma decisão difícil, cada uma com um gatilho
que se lê no diff. A skill `verify` confere só os itens cujo gatilho aparece na mudança (em geral
de 2 a 6), e o PR lista em "Como verifiquei" os que se aplicaram.

**Como o checklist cresce:** quando algo escapa (revert, correção depois do merge, achado do
review que se repete em dois PRs), o PR da correção acrescenta um item aqui ou uma linha no
`REVIEW.md`. Se o problema dá para pegar com teste ou lint, prefira o teste: o item vira só o
ponteiro para ele.

**Como ele encolhe:** item que um teste já pega sozinho, ou cujo gatilho não aparece há meses,
sai. Uma lista longa demais deixa de ser lida.

| # | Gatilho no diff | O que conferir | Origem |
|---|---|---|---|
| 1 | `RegimeAtualizacao`, `RegraPeriodo`, `Juros` | O regime padrão continua com INPC até 07/2024, IPCA desde 08/2024 e agosto/2024 com juros divididos entre 1% e a taxa legal. O `MotorCalculoTest` tem um INPC de agosto/2024 na série de propósito: ele precisa continuar falhando se o INPC voltar. | #27, #29 |
| 2 | `Dinheiro`, `MotorCalculo`, qualquer `setScale`/`RoundingMode`/`MathContext` no core | HALF_UP em duas casas a cada valor monetário, fator sem arredondar (DECIMAL128), nenhum `double`. Todo o gabarito e os testes de propriedade passam sem afrouxar nenhuma tolerância. | CLAUDE.md, `docs/gabarito.md` |
| 3 | Valor esperado ou tolerância em teste de gabarito; arquivos em `src/test/resources/gabarito` | O valor vem de fonte externa citada no PR, e a divergência nova está em `docs/gabarito.md`. Nunca copiar do motor. A guarda do CI exige a seção "Gabarito". | CLAUDE.md, #18, #20, #26 |
| 4 | `SnapshotSeries`, `CalculosSalvos`, `CalculoSalvoDtos`, `calculoSalvo.ts` | Um cálculo salvo **antes** da mudança ainda abre, recalcula com o snapshot de quando foi salvo (não com a série atual) e gera o mesmo PDF. Campo novo precisa de valor padrão para os registros antigos. | #45, #48, #57 |
| 5 | `rascunho.ts`, `armazenamento.ts`, `caso.ts` (formato do caso no navegador) | Um rascunho gravado pela versão anterior carrega ou é descartado sem quebrar a tela. Nada de erro em branco para quem volta no dia seguinte. | #49 |
| 6 | `db/migration/` | Migração já mergeada nunca é editada: mudança é um `V<n+1>__` novo. A migração roda num banco com dados das versões anteriores, não só num banco vazio. | #44, #45, #67 |
| 7 | `DemonstrativoPdf`, `formato.ts` | O total e o aviso de segredo de justiça aparecem em toda página do PDF. Percentuais de juros com as casas da regra (taxa legal com 6). A tela e o PDF usam a mesma formatação. | #43, #74 |
| 8 | `frontend/e2e/referencias/` | As imagens novas foram abertas e comparadas com as antigas, e a diferença é só a pretendida. A guarda do CI exige a seção "Impressão". | #65, #74, #78, #81 |
| 9 | `acesso/`, `Seguranca`, `sessao.ts`, `Acesso.tsx`, `V3__usuarios.sql`, `V5__usuarios_do_escritorio.sql` | Toda rota `/api` exige login, menos o próprio login e a consulta e o aceite de convite. A gestão de usuários e convites exige admin. O membro não enxerga cálculo de outro usuário, nem pelo id. O limite de tentativas continua valendo. Com a sessão expirada, a tela volta ao login. Nenhum usuário criado ou alterado para testar no banco do dia a dia. | #67, #70, #71, #5 |
| 10 | `SgsClient`, `CacheSeries`, `AtualizacaoSeries`, `serieIndisponivel.ts` | Um mês cujo índice ainda não saiu gera o aviso, nunca variação zero nem erro genérico. | #44, #60 |
| 11 | `alimentos/`, `pensao.ts`, `EditorPensao.tsx` | O abatimento maior que o débito é recusado. O excedente da penhora vai para a prisão. Dezembro com 13º dobra o valor já arredondado. Tudo bate com a planilha da DPMG. | #35, #37, #80 |
| 12 | `prescricao.ts`, datas de vencimento | O aviso de parcelas possivelmente prescritas continua aparecendo e não bloqueia o cálculo. | #75 |
| 13 | `numero.ts`, `CampoDecimal.tsx`, `validacao.ts` | Vírgula decimal e ponto de milhar continuam aceitos. Os erros aparecem no campo e no resumo. | #42, #73 |
| 14 | CSS de componente com cor, espaçamento ou fonte literal | Valor novo vira token em `tokens.css` e regra em `docs/design-system.md`. | #53, #55, #56 |
| 15 | `.github/workflows/`, `.github/scripts/`, `compose.yaml`, `Dockerfile` | Nenhum job some ou vira opcional sem dizer por quê. Nenhum secret aparece em log. A guarda e o review leem da base, não do PR. | #65, este checklist |
| 16 | `docs/requisitos-mvp.md`, `docs/gabarito.md`, `README.md`, Javadoc de convenção | A doc diz o que o código faz hoje. Doc que mente é bug. | #27, #77 |
| 17 | `*Request.java`, `*Response.java`, `*Dto*.java`, controllers, `frontend/e2e/fixtures/` | O `ContratoFixturesE2eTest` passa: as fixtures do e2e têm a forma da resposta real. Campo novo na API entra na fixture (regravada de uma resposta real) e o e2e roda de novo. Nunca afrouxar a comparação. | `demonstrativo.json` e `calculo-salvo.json` ficaram sem os campos do #81 |
| 18 | `debeatur-core/src/main/` | O `scripts/diferencial/diferencial-motor.sh` não mostra diferença contra a `main`, ou cada diferença está explicada no PR com a fonte. Regra nova com invariante (soma que fecha, valor que não pode crescer) ganha uma propriedade no `*PropriedadesTest` do pacote. | Teste diferencial e propriedades da pensão |
| 19 | `frontend/e2e/*.spec.ts` | Depois de um clique que busca algo no servidor, o teste espera a resposta por um sinal da tela ou por `waitForResponse`, nunca pelo sumiço de um aviso, que pode sair quando a busca começa. Para testar o meio da busca, segure a rota com uma promessa, como em `regime.spec.ts`. Nada de `waitForTimeout` para esperar resposta. | `regime.spec.ts` calculava com o Tema 1368 a caminho |
| 20 | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`, qualquer arquivo de texto vindo do Dependabot | `git add --renormalize . && git status --short` não lista nada: o arquivo está no repositório com o fim de linha que o `.gitattributes` manda. Senão ele aparece modificado em todo checkout e trava troca de branch e merge na máquina. | O #92 commitou o `mvnw.cmd` com CRLF |
