# Gabarito do motor

Testes que comparam o `MotorCalculo` com cálculos feitos fora dele (issue #2). Os valores
esperados vêm de cálculos homologados ou da contadoria do TJMG e de tabelas oficiais,
nunca do próprio motor.

## O que existe

| Teste | Fonte | Cobre |
|---|---|---|
| `GabaritoTabelaTjmgTest` | Fatores da CGJ-TJMG, DJe/TJMG de 12/05/2026 (#18) | Fator de correção pelo INPC, jan/2024 a abr/2026 |
| `GabaritoHomologadoTest` | Cálculos homologados em `CasosHomologados` | Demonstrativo completo, linha a linha e totais |
| `GabaritoHomologadoTest` | Cinco cálculos do CADEJ/TJMG em `CasosCadej` | Demonstrativo completo, com ICGJ ou IPCA e juros de taxa fixa |

### CADEJ

O [CADEJ](https://www8.tjmg.jus.br/cadej/pages/web/calculoSimples.xhtml) é o Sistema de Cálculo
de Débitos Judiciais do TJMG. A Portaria 5.295/CGJ/2018 manda as contadorias fazerem os cálculos
nele. O módulo "Cálculo Judicial Simplificado" é público, e os casos de `CasosCadej` foram
calculados nele em 24/10/2026 (versão 1.9.12S). São simulações na ferramenta da contadoria, não
cálculos homologados nos autos.

Limites do módulo simplificado:

- um único índice por cálculo: não cobre a virada da Lei 14.905/2024;
- juros só de taxa fixa: não cobre a taxa legal;
- uma única data de início dos juros para todas as parcelas; sem data, não aplica juros;
- não tem pro rata: entram os meses até o anterior ao da data-base, mesmo com data-base no
  meio do mês.

Para reproduzir um caso: informe o valor com vírgula (`10000,00`), confirme as datas pelo
calendário e selecione o índice antes de adicionar as parcelas.

As séries do SGS ficam fixadas em `debeatur-core/src/test/resources/gabarito/series`
(INPC 188 desde 02/2003, IPCA 433 desde 01/2015 e taxa legal 29543 desde 08/2024). Os testes
da tabela da CGJ e da planilha de alimentos da DPMG leem as séries desses arquivos, então eles
conferem as próprias séries fixadas.

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
| Meses do termo inicial (inclusive) até o mês da data-base (exclusive) | Confirmada para correção e juros pela tabela da CGJ (#18) e pelos cinco casos do CADEJ |
| Fator sem arredondamento, acumulando as variações do SGS | Divergência de até 3 × 10⁻⁷ contra a CGJ e o CADEJ; os valores corrigidos batem (ver abaixo) |
| Taxa legal | Confirmada: usa a série 29543 do BCB, que reproduz a Res. CMN 5.171/2024 (#19) |
| Granularidade mensal, sem pro rata die | Confirmada pelo CADEJ, que também ignora a fração do mês (caso 3). Ainda falta cálculo homologado; o pro rata da virada da Lei 14.905/2024 segue na #3 |
| Juros simples sobre o valor corrigido | Confirmada pelo CADEJ (casos 1, 3, 4 e 5) |
| HALF_UP em duas casas a cada valor monetário | Confirmada pelo CADEJ em todas as linhas dos cinco casos |
| Multa de 10% sobre o débito atualizado | Confirmada pelo CADEJ (casos 1, 3 e 4) |
| Honorários de 10% sobre o débito atualizado | **Diverge** do CADEJ (ver divergência 5) |
| Virada da Lei 14.905/2024 e taxa legal no demonstrativo | Pendente: o CADEJ simplificado não cobre; precisa de cálculo homologado |

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
5. **Base dos honorários do art. 523, §1º.** O CADEJ calcula os honorários de 10% sobre o
   débito já acrescido da multa (casos 1 e 4: R$ 1.771,65 sobre R$ 17.716,53). O motor calcula
   sobre o débito atualizado (R$ 1.610,59 sobre R$ 16.105,94). O motor **não** foi alterado:
   a base dos honorários é uma questão jurídica e precisa de decisão de quem responde pelo
   domínio, de preferência confirmada por um cálculo homologado. Enquanto isso, os casos do
   CADEJ registram a divergência nos honorários e no total.
