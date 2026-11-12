# Debeatur

Cálculo de cumprimento de sentença, do índice ao demonstrativo.

Gera o demonstrativo discriminado e atualizado do crédito (art. 524 do CPC): correção
monetária, juros de mora e acréscimos do art. 523, §1º, com memória de cálculo linha a linha.

## Estrutura

| Módulo | Responsabilidade |
|---|---|
| `debeatur-core` | Motor de cálculo puro (sem framework): `Dinheiro`, índices, regimes, `MotorCalculo` |
| `debeatur-indices` | Cliente do SGS/Banco Central (IPCA 433, INPC 188, IPCA-E 10764, SELIC 4390) |
| `debeatur-api` | API REST Spring Boot 4, com PostgreSQL + Flyway e o cache local das séries do SGS |
| `frontend` | React + TypeScript (Vite) |

## Requisitos

- JDK 25
- Node.js 24
- Docker, para o PostgreSQL e para os testes que usam o banco (sem Docker, esses testes são pulados)

O Maven vem pelo wrapper (`./mvnw`), fixado na versão usada pelo CI.

## Executando

### Com Docker

```bash
export DEBEATUR_USUARIO_EMAIL=voce@escritorio.adv.br DEBEATUR_USUARIO_SENHA='uma senha longa'
docker compose up -d --build   # http://localhost:3000
docker compose down
```

O nginx do frontend serve o build do React e encaminha `/api` para a API, que não é exposta
diretamente. O PostgreSQL fica em `127.0.0.1:5432` (`DEBEATUR_DB_PORTA` muda a porta), com os
dados no volume `dados`.

### Localmente

```bash
# banco (PostgreSQL 18; a API aplica as migrações da Flyway ao subir)
docker compose up -d db

# backend (porta 8080)
./mvnw verify
java -jar debeatur-api/target/debeatur-api-0.1.0-SNAPSHOT.jar

# frontend (porta 5173, com proxy de /api para o backend)
cd frontend
npm ci
npm run dev
```

### Login

Toda a API exige login, menos `POST`/`GET /api/sessao` e `/actuator/health`. No MVP cada
escritório tem um usuário. Na subida, a API cria o usuário do escritório padrão (o dono dos
cálculos salvos antes do login) a partir de `DEBEATUR_USUARIO_EMAIL` e `DEBEATUR_USUARIO_SENHA`
(de 8 caracteres a 72 bytes em UTF-8, o limite do bcrypt; letra acentuada conta 2), se o
escritório ainda não tiver um. Depois disso, as variáveis não mudam a senha de quem já existe.

| Endpoint | O que faz |
|---|---|
| `GET /api/sessao` | `200` com `{"email"}` se há sessão, `401` se não; sempre grava o cookie `XSRF-TOKEN` |
| `POST /api/sessao` | `{"email", "senha"}`: entra; `401` com a mesma mensagem para e-mail ou senha errados, `429` com `Retry-After` (segundos) depois de muitas falhas, `503` se a consulta ao usuário falhar |
| `DELETE /api/sessao` | sai (`204`, com ou sem sessão) e apaga o cookie `XSRF-TOKEN` |

A sessão vai num cookie HttpOnly (`JSESSIONID`, `SameSite=Lax`) e expira após 4 horas sem uso.
Todo `POST` e `DELETE` leva no cabeçalho `X-XSRF-TOKEN` o valor do cookie `XSRF-TOKEN`, renovado
a cada login; depois de sair, um novo `GET /api/sessao` traz outro.

Os cookies saem com `Secure` quando o acesso é por HTTPS. O nginx do frontend repassa à API o
`X-Forwarded-Proto` do proxy que terminar a TLS à frente dele (sem proxy, vale o dele mesmo). Se
o proxy for outro, ele precisa mandar esse cabeçalho.

Cinco falhas de login em 15 minutos bloqueiam novas tentativas por 15 minutos, contadas por
e-mail (exista a conta ou não) e por IP (IPv6 pelo prefixo /64). A contagem fica na memória da
API: zera ao reiniciar e não vale com mais de uma instância. Quem sabe o e-mail de um escritório
consegue bloquear o login dele assim; é o preço de não deixar testar senhas sem parar. O IP é o
que o nginx acrescenta ao `X-Forwarded-For`. Com um proxy à frente do nginx, ele precisa
acrescentar o IP do cliente a esse cabeçalho e estar numa rede interna (ou em
`server.tomcat.remoteip.internal-proxies`); senão todos os usuários aparecem com o IP do proxy e
dividem o mesmo limite.

Exemplo de chamada:

```bash
curl -c sessao -b sessao http://localhost:8080/api/sessao
token=$(awk '$6 == "XSRF-TOKEN" {print $7}' sessao)
curl -c sessao -b sessao -X POST http://localhost:8080/api/sessao -H "X-XSRF-TOKEN: $token" \
  -H "Content-Type: application/json" -d '{"email": "voce@escritorio.adv.br", "senha": "uma senha longa"}'
token=$(awk '$6 == "XSRF-TOKEN" {print $7}' sessao)
curl -b sessao -X POST http://localhost:8080/api/calculos -H "X-XSRF-TOKEN: $token" \
  -H "Content-Type: application/json" -d '{
  "dataBase": "2026-10-01",
  "aplicarArt523": true,
  "parcelas": [
    { "descricao": "Danos materiais", "valor": 10000, "inicioCorrecao": "2023-03-15", "inicioJuros": "2023-06-10" }
  ]
}'
```

### Cálculos salvos

| Endpoint | O que faz |
|---|---|
| `POST /api/calculos/salvos` | `{"titulo": "...", "calculo": <pedido de POST /api/calculos>}`: calcula, salva e devolve `201` com o id |
| `GET /api/calculos/salvos` | lista os cálculos do escritório logado, do mais recente para o mais antigo |
| `GET /api/calculos/salvos/{id}` | devolve o pedido salvo e o demonstrativo refeito com o snapshot |

Cada cálculo salvo guarda o pedido, com o regime usado já resolvido, e o snapshot dos valores
das séries que usou. Reabrir refaz o demonstrativo com esse snapshot, e não com o cache: o
resultado é o mesmo do dia em que foi salvo, mesmo que o BCB tenha revisado uma série ou o SGS
esteja fora do ar. Para atualizar um cálculo, reenvie o pedido salvo com a nova data-base.

A conexão vem de `DEBEATUR_DB_URL`, `DEBEATUR_DB_USUARIO` e `DEBEATUR_DB_SENHA`, com padrão
`jdbc:postgresql://localhost:5432/debeatur`, usuário e senha `debeatur`.

### Cache das séries

O cálculo lê os índices e o salário mínimo do banco, e não do SGS a cada pedido. O SGS só é
consultado quando o cache não cobre o período pedido, ou quando falta um mês recente e a última
consulta tem mais de uma hora (`debeatur.series.reconsulta`). Todo dia às 6h (horário de
Brasília, `debeatur.series.atualizacao`) os últimos 24 meses de cada série são relidos
(`debeatur.series.meses-revisao`), para acompanhar as revisões do BCB. Se o SGS estiver fora do
ar, valem os meses que o cache já tem.

## Regras de cálculo

### Regime cível padrão

| Período | Correção | Juros de mora |
|---|---|---|
| até 07/2024 | INPC (tabela da CGJ-TJMG) | 1% a.m. (art. 406 CC, redação anterior) |
| 08/2024 | IPCA | 1% a.m. |
| a partir de 09/2024 | IPCA | taxa legal divulgada pelo BCB (Lei 14.905/2024, Res. CMN 5.171/2024) |

A Lei 14.905/2024 vigora desde 30/08/2024. A tabela da CGJ vigente em 29/08/2024 só tem INPC
até julho, então agosto/2024 já é corrigido pelo IPCA. Com granularidade mensal, a taxa legal
é aplicada a partir de setembro/2024.

### Convenções provisórias (a validar contra cálculos homologados)

- **Granularidade mensal:** entram os meses do termo inicial (inclusive) até o mês da data-base (exclusive), sem pro rata die.
- **Correção:** produto de (1 + variação mensal), com precisão `DECIMAL128` e sem arredondar o fator a casas fixas.
- **Juros:** simples, somando os percentuais mensais, aplicados sobre o valor corrigido.
- **Taxa legal:** subtração direta SELIC − IPCA por mês; conferir com a metodologia da Res. CMN 5.171/2024.
- **Arredondamento:** HALF_UP em duas casas a cada valor monetário.

## Próximos passos

O escopo do MVP está em `docs/requisitos-mvp.md`, inclusive o que fica de fora.

- [ ] Testes de gabarito com cálculos reais homologados
- [ ] Pro rata die na correção e nos juros
- [ ] Regime configurável por cálculo (critérios fixados na sentença)
- [x] PostgreSQL + Flyway e cache das séries do SGS
- [x] Cálculos salvos, com snapshot dos índices usados
- [ ] Exportação do demonstrativo em PDF
- [ ] Autenticação mínima (um usuário por escritório)
