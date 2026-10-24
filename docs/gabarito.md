# Gabarito do motor

Testes que comparam o `MotorCalculo` com cálculos feitos fora dele (issue #2). Os valores
esperados vêm de cálculos homologados ou da contadoria do TJMG e de tabelas oficiais,
nunca do próprio motor.

## O que existe

| Teste | Fonte | Cobre |
|---|---|---|
| `GabaritoTabelaTjmgTest` | Fatores da CGJ-TJMG, DJe/TJMG de 12/05/2026 (#18) | Fator de correção pelo INPC, jan/2024 a abr/2026 |
| `GabaritoHomologadoTest` | Cálculos homologados em `CasosHomologados` | Demonstrativo completo, linha a linha e totais |

As séries do SGS ficam fixadas em `debeatur-core/src/test/resources/gabarito/series`
(INPC 188 e IPCA 433 desde 01/2015; taxa legal 29543 desde 08/2024). O teste da tabela da
CGJ lê o INPC desse arquivo, então ele confere a própria série fixada.

Sem casos cadastrados, o `GabaritoHomologadoTest` aparece como ignorado (*skipped*) no
relatório. Ele não passa em branco.

## Como incluir um cálculo homologado

1. **Reúna o documento.** Pode ser o cálculo da contadoria ou a planilha homologada por
   decisão, com o número do processo e o ID ou as folhas. Para transcrever, é preciso:
   - valor, termo inicial da correção e termo inicial dos juros de cada parcela;
   - data-base;
   - critérios fixados no título (índice, juros e data da virada da Lei 14.905/2024);
   - se houve multa e honorários do art. 523, §1º;
   - os valores do resultado: fator, valor corrigido, percentual e valor dos juros por parcela, e os totais.
2. **Transcreva** o caso em `CasosHomologados.todos()`:

   ```java
   CasoGabarito.de("<descrição curta>", "<processo, órgão, ID ou folhas>")
           .dataBase("<AAAA-MM-DD>")
           .regime(<regime fixado no título>)
           .comArt523()                       // só se o cálculo inclui os acréscimos
           .parcela(new Parcela("<verba>", Dinheiro.de("<valor>"),
                           LocalDate.parse("<termo da correção>"), LocalDate.parse("<termo dos juros>")),
                   new CasoGabarito.LinhaEsperada(numero("<fator impresso>"), dinheiro("<corrigido>"),
                           numero("<% juros>"), dinheiro("<juros>"), dinheiro("<subtotal>")))
           .totais("<débito>", "<multa>", "<honorários>", "<total>")
           .criar()
   ```

   Campos que o documento não mostra ficam `null` e não são comparados. Fator e percentual
   são comparados na precisão em que foram impressos.
3. **Não ajuste o esperado.** Se o motor divergir, o teste lista todas as diferenças do
   caso de uma vez. Registre a divergência abaixo e a convenção que a causou. Só use
   `.tolerancia(valor, justificativa)` para diferenças de centavos com causa conhecida.
4. **Série própria.** Se o cálculo usou a tabela da CGJ em vez do INPC do SGS, ou uma série
   anterior a 2015, passe-a com `.serie(...)` e cite a fonte.

## Convenções do motor

| Convenção provisória | Situação |
|---|---|
| Meses do termo inicial (inclusive) até o mês da data-base (exclusive) | Confirmada para o fator pela tabela da CGJ (#18) |
| Fator sem arredondamento, acumulando as variações do SGS | Divergência de até 2,5 × 10⁻⁷ contra a tabela (ver abaixo) |
| Taxa legal | Confirmada: usa a série 29543 do BCB, que reproduz a Res. CMN 5.171/2024 (#19) |
| Granularidade mensal, sem pro rata die | Pendente: precisa de caso com termo inicial ou data-base no meio do mês |
| Juros simples sobre o valor corrigido | Pendente: precisa de cálculo homologado com juros |
| HALF_UP em duas casas a cada valor monetário | Pendente: precisa de cálculo homologado |
| Multa e honorários de 10% sobre o débito atualizado | Pendente: precisa de cálculo homologado com art. 523 |

## Divergências registradas

1. **Fator do SGS × número-índice do IBGE (#18).** As variações do SGS vêm com duas casas,
   e a tabela da CGJ parte do número-índice. A diferença no fator chega a 2,5 × 10⁻⁷
   (cerca de R$ 0,03 em R$ 100.000,00). Fechá-la exige usar o número-índice ou importar a
   tabela da CGJ como série.
2. **Taxa legal (#19).** A fórmula anterior (SELIC − IPCA do mês) dava juros cerca de 40%
   menores que a taxa da Res. CMN 5.171/2024. Corrigido: o motor usa a taxa publicada pelo BCB.
3. **Virada da Lei 14.905/2024.** O TJMG aplica IPCA e taxa legal a partir de 30/08/2024
   (ex.: TJMG, 1.0000.23.137113-9/002: tabela da CGJ e 1% a.m. até 29/08/2024). O motor troca
   de regra em 09/2024 por causa da granularidade mensal, então perde a taxa legal de
   30 e 31/08/2024. Resolve-se com o pro rata die (#3).
4. **O critério anterior à lei varia no próprio TJMG.** Na apelação 1.0000.25.153453-3/001, a
   maioria aplicou a SELIC até 30/08/2024 (REsp 1.795.982); o voto vencido aplicou a tabela da
   CGJ e 1% a.m. Isso reforça que o regime precisa vir do título (#3), e cada caso deve
   transcrever o critério que o próprio cálculo usou.
