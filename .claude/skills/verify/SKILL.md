---
name: verify
description: Verifica uma mudança no Debeatur antes do commit ou do PR. Escolhe as checagens pelo diff, roda o app, faz um review em contexto novo e junta a evidência para o PR. Use antes de todo commit que vai para PR, e quando pedirem para verificar, testar ou conferir uma mudança.
---

# Verificar uma mudança no Debeatur

O objetivo é produzir **evidência** de que a mudança funciona: saída de comando, screenshot,
contagem de testes. Dizer "testes passando" não basta. A evidência vai para a seção
"Como verifiquei" do PR, e é ela que permite fazer o merge sem uma leitura humana linha a linha.

## 1. Ler o diff e classificar

```bash
git fetch -q origin && git diff --name-only origin/main...HEAD && git status --short
```

- Defina o **risco** pela tabela "Verificação e review" do CLAUDE.md. Na dúvida, o nível mais alto.
- Abra `docs/checklist-confiabilidade.md` e separe os itens cujo gatilho aparece no diff.

## 2. Checagens baratas primeiro

Rode só o que o diff toca, nesta ordem, e pare no primeiro vermelho para corrigir:

| Diff toca | Comando |
|---|---|
| `debeatur-core` | `./mvnw -B -q verify -pl debeatur-core` |
| `debeatur-indices` ou `debeatur-api` | `./mvnw -B verify` (os testes com banco precisam do Docker; sem ele são pulados, e isso vai escrito no PR) |
| `frontend/` | `cd frontend && npm run lint && npm test && npm run build` |
| tela, CSS, impressão ou PDF | `cd frontend && npm run e2e` (Docker). Mudança visual intencional: `npm run e2e -- --update-snapshots`, e **abra as imagens** antes e depois |

## 3. Rodar a mudança de verdade

Testes passando não mostram que a tela faz o que o PR promete. Se a mudança é visível ao usuário:

- Suba pelo `.claude/launch.json`: `docker compose up -d db`, depois `api`, depois `frontend`.
  Ou use `docker` se o compose já está no ar.
- Faça no navegador o fluxo que o PR mudou, do jeito que o usuário faria, e tire um screenshot do
  resultado. Use o usuário do escritório que já está no banco; não crie nem altere usuários.
- Em mudança no motor, confira na tela um caso do gabarito: o total mostrado tem de ser o do teste.

## 4. Conferir os itens do checklist

Para cada item separado no passo 1, faça a checagem descrita e anote o resultado em uma linha.
Item que não deu para conferir (sem Docker, sem dado antigo) vai como **não conferido**, com o
motivo.

## 5. Review em contexto novo

Rode `/code-review` sobre o diff, ou um subagente sem o histórico desta sessão, com o
`REVIEW.md` como instrução. Quem escreveu a mudança não é quem revisa. Corrija os Important e
repita no máximo 3 vezes.

## 6. Quando parar e chamar o dono

Pare e explique o que tentou, sem fazer o merge, quando:

- a mesma checagem continuar vermelha depois de **3 rodadas** de correção;
- o review ainda tiver Important depois de 3 rodadas;
- um teste de gabarito falhar. Nunca ajuste o valor esperado para passar;
- o risco for **alto** (o merge é sempre do dono);
- a correção exigir mudar uma convenção do motor, uma regra do domínio ou o próprio CLAUDE.md.

## Proibido

- Pular um teste instável, pôr `sleep` ou rodar de novo até ficar verde. Ache a causa. Se não
  achar, pare e chame o dono.
- Afrouxar uma tolerância de gabarito ou regravar referências da impressão sem olhar as imagens.
- Editar `REVIEW.md`, o checklist, `.github/scripts` ou os workflows no mesmo PR que eles julgariam
  sem dizer isso em "Para revisar".

## 7. Registrar

No PR (template em `.github/pull_request_template.md`):

- **Risco:** o nível e o motivo.
- **Como verifiquei:** os comandos com o resultado (ex.: `./mvnw verify`: 214 testes, 0 falhas),
  o screenshot, os itens do checklist com ✓ ou "não conferido: motivo", e o resultado do review.
- **Gabarito / Impressão:** quando a guarda do CI pedir.

No commit, acrescente o trailer `Verificado:` com o que rodou, por exemplo
`Verificado: mvnw verify, npm test, e2e, preview, code-review` ou `Verificado: nenhum (só docs)`.
Assim dá para achar depois os commits que entraram sem verificação.
