# Fluxo de trabalho com o agente

Como uma mudança sai da ideia e chega aos autos. O CLAUDE.md tem as regras do domínio e a tabela de
risco; este documento diz em que ordem as coisas acontecem, o que roda em cada etapa e o que barra a
passagem para a seguinte.

Três princípios:

1. **O determinístico vem antes do caro.** Hook, teste e CI rodam primeiro e não gastam token. O
   review por modelo só começa com tudo verde, para não gastar cota apontando o que o lint pegaria.
2. **Cada etapa numa sessão limpa.** Planejar, implementar e revisar em contextos separados,
   ligados por artefatos (a issue com a especificação, o diff, o PR). Contexto velho custa token a
   cada mensagem e faz o revisor herdar as justificativas de quem escreveu.
3. **O esforço segue o risco da mudança**, pela tabela do CLAUDE.md.

## Que mecanismo usar

| Mecanismo | Quando | Custo |
|---|---|---|
| CI, script, hook | A regra é determinística: "não edite migração mergeada", "nada de `double` no motor" | Nenhum token |
| Skill | Procedimento repetido que pede julgamento: verificar um PR, triar uma issue | Roda na sessão que já existe |
| Subagente | Olhos novos (review e refutação) ou tarefa barulhenta (ler logs, varrer o código) | Começa do zero; gasta cota |
| Agente agendado | Trabalho recorrente com julgamento que ninguém vai lembrar de fazer | Gasta cota sem ninguém olhando |

Regra que precisa valer sempre vira hook ou `deny` em `.claude/settings.json`, não mais uma linha
no CLAUDE.md. Nada de um agente por papel (produto, QA, usuário): custa caro e produz opinião de
modelo, não evidência.

## Etapas

| # | Etapa | Quem ou o quê | Portão |
|---|---|---|---|
| 0 | Triagem | A issue, com entrevista ao dono | Tem caso real (sentença, cálculo, pedido de usuário)? Risco definido? Precisa de gabarito? Sem caso, a issue fica com o rótulo `talvez` |
| 1 | Planejar | Plan mode: o agente lê o código e propõe, sem editar | Risco alto: o dono aprova o plano |
| 2 | Implementar | Sessão nova a partir da especificação da issue, teste primeiro, commits pequenos | Os hooks de `.claude/hooks/` |
| 3 | Verificar | Skill `verify`: checagens pelo diff, app rodando, screenshot | Tudo determinístico verde |
| 4 | Revisar | Revisor e refutador em subagentes (passo 5 da `verify`); o dono, conforme o risco | Nenhum Important aberto |
| 5 | PR e CI | GitHub Actions: `ci.yml`, `gabarito.yml`, `review.yml` | CI verde e conversas resolvidas |
| 6 | Deploy | Script, sem modelo nenhum | Smoke test em produção |
| 7 | Observar | Erros com a versão do build, feedback dos usuários, sessões do beta | O que aparece vira issue e volta à etapa 0 |

### 0. Triagem

Feature só entra com um caso concreto que a peça. A triagem entrevista o dono até dar para escrever
a especificação no corpo da issue: o caso, o que é "pronto" (critérios de aceite), o risco e, se
tocar o motor, de onde vem o gabarito. A especificação é também o checklist do revisor na etapa 4.

Os dois públicos (advogado particular e Defensoria) pedem coisas diferentes. A issue diz de qual
deles veio o pedido.

### 1. Planejar

Plan mode (`Shift+Tab`): o agente lê, propõe o plano e não edita nada até a aprovação. Ler um plano
custa menos que ler um diff, por isso é aqui que o dono entra nas mudanças de risco alto.

| Risco | Plano | Review por modelo | Leitura do dono |
|---|---|---|---|
| Baixo | Dispensado | Nenhum além do CI | Não |
| Médio | Opcional | Passo 5 da `verify` | A evidência no PR |
| Alto | Aprovado pelo dono | `verify` e `review.yml` | Linha a linha |

### 2. Implementar

- Sessão nova, que começa lendo a especificação da issue.
- Teste primeiro: um teste que falha e mostra o caso; depois o código. No motor, o valor esperado do
  gabarito é o que o dono trouxe de fora, nunca o que o motor calculou.
- Contexto limpo entre tarefas (`/clear`). Depois de duas correções que não funcionaram, recomece
  com um pedido melhor em vez de tentar a terceira.
- Trabalho em paralelo vai em worktrees separadas.

Os hooks em `.claude/settings.json` valem em todo modo de permissão:

- **`protege-arquivos.mjs`** (antes de editar): pede a confirmação do dono para gabarito e
  convenções do motor (a mesma regra da `guarda-gabarito.sh`, lida do script), migração já
  existente, `.github/`, CLAUDE.md, REVIEW.md e a própria configuração do agente (settings, hooks,
  skills e agentes).
- **`sem-double-no-core.mjs`** (depois de editar): acusa `double`, `float` e `BigDecimal` de
  literal com ponto no código do motor, e o agente corrige na hora.
- **`deny`**: nega a leitura de `.env` pela ferramenta de leitura e as formas comuns de push na
  `main`.

Nada disso é cerca completa: os hooks cobrem as ferramentas de edição, não um `sed` pelo shell, e o
`deny` casa o comando como escrito (`cat .env` ou um push de outro jeito passam). São para evitar
descuido; a guarda que vale está na proteção da `main` no GitHub, no CI e no review.

Dados reais de clientes (sentença, nome das partes, cálculo de um processo) não entram no
repositório nem numa conversa com o agente. Caso real vira gabarito ou fixture só depois de
anonimizado.

### 3 e 4. Verificar e revisar

É a skill `verify`, que já descreve as checagens, a evidência e o review em dois subagentes
(revisor em Sonnet, refutador em Opus). O que este fluxo acrescenta: o revisor recebe a
especificação da issue além do `REVIEW.md`, para conferir se cada critério de aceite foi atendido.

### 5. PR e CI

O `review.yml` roda ao abrir o PR, não a cada push, e uma rodada nova se pede à mão. No risco médio
ele repete o review da `verify`; a intenção é que passe a rodar só quando o PR toca arquivo de
risco alto (ver "Próximos passos").

### 6. Deploy

Sem modelo no caminho: deploy por tag a partir do CI, um smoke test em produção (login e um caso
do gabarito pela API, comparado com o valor esperado) e rollback se falhar. Um passo com modelo no
deploy transformaria cada falha em dúvida.

### 7. Observar

O erro em produção leva a versão do build, para chegar ao PR que o causou. O feedback dos usuários
vira issue, e a issue volta à triagem. Medir impacto, não volume: tempo para montar um cálculo,
erros que o usuário encontrou, divergências contra o gabarito. Número de PRs não diz nada.

## Custo

- Sonnet nas sessões do dia a dia; Opus no motor e nas decisões de risco alto.
- `/clear` em vez de `/compact` quando dá para recomeçar de um bom ponto: compactar lê a conversa
  inteira.
- Subagente só para review e tarefa barulhenta. O que ele gasta sai da mesma cota.
- `/usage` uma vez por semana, para ver para onde a cota está indo.

## Próximos passos

O que este documento descreve e ainda não existe, um PR por item:

1. Triagem como skill, com a entrevista e o modelo da especificação na issue.
2. `review.yml` só nos PRs de risco alto, com `--max-turns`.
3. Revisor da `verify` recebendo a especificação da issue.
4. Deploy por tag com smoke test e rollback.
5. Versão do build nos logs e no demonstrativo; canal de feedback no app.
