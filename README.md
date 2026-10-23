# Debeatur

Cálculo de cumprimento de sentença, do índice ao demonstrativo.

Gera o demonstrativo discriminado e atualizado do crédito (art. 524 do CPC): correção
monetária, juros de mora e acréscimos do art. 523, §1º, com memória de cálculo linha a linha.

## Estrutura

| Módulo | Responsabilidade |
|---|---|
| `debeatur-core` | Motor de cálculo puro (sem framework): `Dinheiro`, índices, regimes, `MotorCalculo` |
| `debeatur-indices` | Cliente do SGS/Banco Central (IPCA 433, INPC 188, IPCA-E 10764, SELIC 4390) |
| `debeatur-api` | API REST Spring Boot 4 |
| `frontend` | React + TypeScript (Vite) |

## Requisitos

- JDK 25
- Maven 3.9+
- Node.js 22+

## Executando

### Com Docker

```bash
docker compose up -d --build   # http://localhost:3000
docker compose down
```

O nginx do frontend serve o build do React e encaminha `/api` para a API, que não é exposta
diretamente.

### Localmente

```bash
# backend (porta 8080)
mvn verify
java -jar debeatur-api/target/debeatur-api-0.1.0-SNAPSHOT.jar

# frontend (porta 5173, com proxy de /api para o backend)
cd frontend
npm install
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

## Regras de cálculo

### Regime cível padrão

| Período | Correção | Juros de mora |
|---|---|---|
| até 08/2024 | INPC | 1% a.m. (art. 406 CC, redação anterior) |
| a partir de 09/2024 | IPCA | taxa legal: SELIC − IPCA, mínimo zero (Lei 14.905/2024) |

A Lei 14.905/2024 vigora desde 30/08/2024; com granularidade mensal, a nova regra é aplicada
a partir de setembro/2024.

### Convenções provisórias (a validar contra cálculos homologados)

- **Granularidade mensal:** entram os meses do termo inicial (inclusive) até o mês da data-base (exclusive), sem pro rata die.
- **Correção:** produto de (1 + variação mensal), sem arredondar o fator.
- **Juros:** simples, somando os percentuais mensais, aplicados sobre o valor corrigido.
- **Taxa legal:** subtração direta SELIC − IPCA por mês; conferir com a metodologia da Res. CMN 5.171/2024.
- **Arredondamento:** HALF_UP em duas casas a cada valor monetário.

## Próximos passos

- [ ] Testes de gabarito com cálculos reais homologados
- [ ] Pro rata die na correção e nos juros
- [ ] Regime configurável por cálculo (critérios fixados na sentença)
- [ ] Pagamentos parciais (imputação: art. 354 CC)
- [ ] Persistência (PostgreSQL + Flyway), cache das séries e snapshot dos índices por cálculo
- [ ] Exportação em PDF e Excel
- [ ] Autenticação e multi-tenancy (escritório → usuários → processos)
