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
| Granularidade mensal, sem pro rata die | Confirmada pelo CADEJ, que também ignora a fração do mês (caso 3). Ainda falta cálculo homologado. A exceção é o mês da virada no regime padrão, dividido como na planilha da DPMG (#3). No critério do Tema 1368, agosto/2024 fica inteiro com a SELIC, como no CADEJ e no Manual da Justiça Federal (divergência 4) |
| Juros simples sobre o valor corrigido | Confirmada pelo CADEJ (casos 1, 3, 4 e 5) |
| HALF_UP em duas casas a cada valor monetário | Confirmada pelo CADEJ em todas as linhas dos cinco casos |
| Multa de 10% sobre o débito atualizado | Confirmada pelo CADEJ (casos 1, 3 e 4) |
| Honorários de 10% sobre o débito atualizado, sem a multa | Segue o STJ (REsp 1.757.033/DF); **diverge** do CADEJ (ver divergência 5) |
| Virada da Lei 14.905/2024 e taxa legal no demonstrativo | Pendente: o CADEJ simplificado não cobre; precisa de cálculo homologado |

## Divergências registradas

1. **Fator do SGS × número-índice do IBGE (#18).** As variações do SGS vêm com duas casas,
   e a tabela da CGJ parte do número-índice. A diferença no fator chega a 2,5 × 10⁻⁷
   (cerca de R$ 0,03 em R$ 100.000,00). Fechá-la exige usar o número-índice ou importar a
   tabela da CGJ como série.
2. **Taxa legal (#19).** A fórmula anterior (SELIC − IPCA do mês) dava juros cerca de 40%
   menores que a taxa da Res. CMN 5.171/2024. Corrigido: o motor usa a taxa publicada pelo BCB.
3. **Virada da Lei 14.905/2024.** O TJMG aplica IPCA e taxa legal a partir de 30/08/2024
   (ex.: TJMG, 1.0000.23.137113-9/002: tabela da CGJ e 1% a.m. até 29/08/2024). A tabela da
   CGJ vigente em 29/08/2024 só tem INPC até julho, então o regime padrão corrige agosto/2024
   pelo IPCA, como a planilha da DPMG (#24). Nos juros, agosto/2024 se divide como na planilha
   da DPMG (#3): 29/30 de 1% (0,9667%, arredondado a quatro casas) mais 2/31 da taxa legal de
   agosto (Res. CMN 5.171/2024, arts. 6º e 8º, parágrafo único), total de 1,005752%. Os 2/31 da
   taxa legal são norma; os 29/30 do mês comercial são convenção da planilha.
4. **Critério anterior à lei: SELIC pelo Tema 1368, salvo coisa julgada (#28).** O TJMG
   divergia: na apelação 1.0000.25.153453-3/001, a maioria aplicou a SELIC até 30/08/2024
   (REsp 1.795.982), e o voto vencido aplicou a tabela da CGJ e 1% a.m. A Corte Especial do STJ
   fixou em repetitivo que a taxa do art. 406 do CC, antes da Lei 14.905/2024, é a SELIC
   (Tema 1368, REsp 2.199.164/PR, julgado em 15/10/2025, sem modulação). A SELIC engloba
   correção e juros e não se cumula com a tabela da CGJ ou com o INPC. Daí:
   - **título omisso ou "juros legais":** SELIC até 29/08/2024; a partir de 30/08/2024, IPCA +
     taxa legal;
   - **título que fixou a tabela da CGJ + 1% a.m.:** a coisa julgada prevalece, e vale o
     `civelPadrao()`, igual à planilha da DPMG.

   O regime continua vindo do título (#3), e cada caso deve transcrever o critério que o
   próprio cálculo usou.

   **Agosto/2024 no critério do Tema 1368 não se divide (#32).** O regime informado fica com
   SELIC até 08/2024 e IPCA + taxa legal desde 09/2024, porque é assim que as fontes de fora
   fazem:
   - o Manual de Cálculos da Justiça Federal (ed. 2026, item 4.2, devedor não enquadrado como
     Fazenda Pública) aplica a SELIC até ago./2024 e a taxa legal desde set./2024, sem fração
     de mês, e o Projef Web segue a mesma regra;
   - o CADEJ conta meses inteiros. Com a SELIC, uma parcela de 15/01/2024 atualizada até
     29/08/2024 leva os meses de janeiro a julho. Com a taxa legal (índice TXLGLJ), uma parcela
     de 30/08/2024 atualizada até 30/09/2024 leva agosto inteiro (1,0060531), e não os 2/31 da
     Res. CMN 5.171/2024. As duas simulações foram feitas em 11/11/2026, na versão 1.9.12S;
   - os acórdãos do TJMG fixam o corte em 29 ou 30/08/2024, mas não dizem como se calcula o
     mês.

   A divisão por dias do regime padrão (29/30 de 1% + 2/31 da taxa legal) continua, porque
   reproduz a planilha da DPMG, que é o gabarito dele. São convenções diferentes para critérios
   diferentes.
5. **Base dos honorários do art. 523, §1º.** O CADEJ calcula os honorários de 10% sobre o
   débito já acrescido da multa (casos 1 e 4: R$ 1.771,65 sobre R$ 17.716,53). O motor calcula
   sobre o débito atualizado (R$ 1.610,59 sobre R$ 16.105,94), como o STJ: a multa não integra a
   base dos honorários, porque as duas incidem sobre o débito (REsp 1.757.033/DF, 3ª Turma,
   rel. Min. Ricardo Villas Bôas Cueva, julgado em 09/10/2018, DJe 15/10/2018, unânime). O
   precedente foi conferido pela ementa e pelos informativos, e falta ler o inteiro teor. É
   acórdão de turma, não repetitivo, então o motor fica como está e os casos do CADEJ seguem
   registrando a divergência nos honorários e no total. Um cálculo homologado do TJMG ainda
   confirmaria a prática local.
6. **SELIC: série mensal do SGS × SELIC diária do CADEJ (#28).** O CADEJ multiplica a SELIC
   diária (SGS 11) dos dias úteis. A parcela de 15/01/2024 atualizada até 29/08/2024 dá fator
   1,0617101, que é o produto exato dos 147 dias úteis de 01/01 a 31/07/2024. O motor multiplica
   a SELIC mensal (SGS 4390), publicada com duas casas, e chega a 1,0617743: R$ 0,64 a mais em
   R$ 10.000,00. A forma de acumular é a mesma (produto de fatores); a diferença vem só do
   arredondamento da série mensal. O Manual de Cálculos da Justiça Federal, ao contrário,
   **soma** as taxas mensais ("capitalizada de forma simples") e conta do mês seguinte ao
   termo inicial até o mês anterior ao pagamento, mais 1% no mês do pagamento, como nos
   tributos federais. Como o tribunal de referência é o TJMG, o caminho é seguir o CADEJ:
   montar o fator mensal a partir da SGS 11 e transcrever esse caso em `CasosCadej`.
