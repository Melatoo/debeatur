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

Exemplo de chamada:

```bash
curl -X POST http://localhost:8080/api/calculos -H "Content-Type: application/json" -d '{
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
| `GET /api/calculos/salvos` | lista os cálculos do escritório, do mais recente para o mais antigo |
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
