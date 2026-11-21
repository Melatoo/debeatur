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
| `GabaritoRendimentosDecimoTerceiroDpmgTest` | Planilha da DPMG gerada no iadefensor em 15/11/2026 | Pensão em rendimentos, 13º, abatimento nominal e art. 523 sobre o saldo |
| `GabaritoSelicDiariaTest` (`debeatur-indices`) | Planilha "SELIC Acumulada" da DPMG (18/11/2026) e uma simulação no CADEJ (11/11/2026) | SELIC do mês pela SGS 11 e fator pela SELIC, 01/2024 a 10/2026 (divergência 6) |

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
| Granularidade mensal, sem pro rata die | Confirmada pelo CADEJ, que também ignora a fração do mês (caso 3). Ainda falta cálculo homologado. A exceção é o mês da virada no regime padrão, dividido como na planilha da DPMG (#3). No critério do Tema 1368, agosto/2024 vai inteiro para um critério só, e o Manual da Justiça Federal e o CADEJ divergem sobre qual; o Debeatur segue o CADEJ, com a SELIC até 07/2024 (divergência 4, decisão de 19/11/2026) |
| Juros simples sobre o valor corrigido | Confirmada pelo CADEJ (casos 1, 3, 4 e 5) |
| HALF_UP em duas casas a cada valor monetário | Confirmada pelo CADEJ em todas as linhas dos cinco casos |
| Multa de 10% sobre o débito atualizado | Confirmada pelo CADEJ (casos 1, 3 e 4) |
| Honorários de 10% sobre o débito atualizado, sem a multa | Segue o STJ pela ementa do REsp 1.757.033/DF (inteiro teor pendente); **diverge** do CADEJ (ver divergência 5) |
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

   As datas acima são o critério jurídico, em dias. Como o motor trabalha por mês inteiro, o
   mês de agosto/2024 é uma convenção à parte, tratada logo abaixo. O regime continua vindo do
   título (#3), e cada caso deve transcrever o critério que o próprio cálculo usou.

   **Agosto/2024 no critério do Tema 1368: as fontes de fora divergem (#32).** Nenhuma delas
   divide o mês, mas cada uma o entrega a um critério diferente:
   - o Manual de Cálculos da Justiça Federal (ed. 2026, item 4.2, devedor não enquadrado como
     Fazenda Pública) aplica a SELIC até ago./2024 e a taxa legal desde set./2024, sem fração
     de mês, e o Projef Web segue a mesma regra. No editor, é a SELIC até 08/2024;
   - o CADEJ conta meses inteiros, do mês do termo inicial até o mês anterior ao da data-base.
     Com a SELIC, uma parcela de 15/01/2024 atualizada até 29/08/2024 leva os meses de janeiro
     a julho, **sem agosto** (o fator da divergência 6 é o produto dos dias úteis até 31/07).
     Com a taxa legal (índice TXLGLJ), uma parcela de 30/08/2024 atualizada até 30/09/2024 leva
     **agosto inteiro** (1,0060531), e não os 2/31 da Res. CMN 5.171/2024. Encadeando as duas
     simulações (SELIC até 29/08, taxa legal desde 30/08), agosto fica com a taxa legal. No
     editor, é a SELIC até 07/2024. As duas simulações foram feitas em 11/11/2026, na versão
     1.9.12S;
   - os acórdãos do TJMG fixam o corte em 29 ou 30/08/2024, mas não dizem como se calcula o
     mês.

   **O Debeatur segue o CADEJ: SELIC até 07/2024 (decisão de 19/11/2026).** Agosto/2024 vai
   inteiro para a regra seguinte, com IPCA e taxa legal do mês inteiro. O CADEJ mostra a taxa
   legal de agosto inteira; o IPCA de agosto vem da regra seguinte, como no regime padrão
   (divergência 3), porque o CADEJ simplificado só aplica um índice por cálculo. A SELIC de
   agosto não se divide por dias: nenhuma fonte de fora faz isso, e a SELIC de 29 dias somada
   ao IPCA do mês corrigiria agosto duas vezes.

   **A decisão se afasta da Res. CMN 5.171/2024, como o CADEJ.** Pelo art. 8º, parágrafo
   único, a taxa legal de agosto/2024 vale só em 2/31 do mês (0,039052%), e o regime padrão
   segue isso. Aqui agosto leva a taxa legal inteira (0,605306%), e os dias 1 a 29/08 ficam sem
   a SELIC (cerca de 0,87% no mês). Em R$ 10.000,00, são cerca de R$ 60,53 de juros em agosto,
   contra uns R$ 83 a R$ 85 se o mês se dividisse por dias (úteis ou corridos). Dividir exigiria a SELIC pro rata, que é
   convenção sem fonte, e mudar o motor sem gabarito (#32).

   O regime pronto do título omisso é o `RegimeAtualizacao.tema1368()` (`GET
   /api/regimes/tema-1368`): SELIC sem outra taxa de 01/2003 a 07/2024 e IPCA + taxa legal desde
   08/2024. Ele começa no mês em que o CC/2002 entrou em vigor (11/01/2003); antes disso a taxa
   do art. 406 não era a SELIC, e um termo inicial anterior a 01/2003 é recusado para que os
   critérios venham do título, pelo editor de regime. Pela granularidade mensal, um termo de 1º a
   10/01/2003 leva a SELIC de janeiro inteiro.

   A planilha da DPMG não tem modelo com a virada da SELIC, então não decide o mês. Ela
   concorda na correção (o modelo "Taxa legal (Bacen)" corrige agosto/2024 inteiro pelo IPCA),
   mas divide os juros de agosto. O editor recomenda a SELIC até 07/2024 e avisa quando ela vai
   até 08/2024 (a regra do Manual da Justiça Federal). O motor não muda: a regra por mês
   inteiro já faz isso. Falta um cálculo homologado ou da contadoria do TJMG que confirme o
   mês.

   A divisão por dias do regime padrão (29/30 de 1% + 2/31 da taxa legal) continua, porque
   reproduz a planilha da DPMG, que é o gabarito dele. Os 2/31 da taxa legal são norma nos dois
   critérios; o que muda é a parte de convenção: os 29/30 de 1% no regime padrão têm fonte (a
   planilha), e a SELIC pro rata no Tema 1368 não tem.
5. **Base dos honorários do art. 523, §1º.** O CADEJ calcula os honorários de 10% sobre o
   débito já acrescido da multa (casos 1 e 4: R$ 1.771,65 sobre R$ 17.716,53). O motor calcula
   sobre o débito atualizado (R$ 1.610,59 sobre R$ 16.105,94), como o STJ: a multa não integra a
   base dos honorários, porque as duas incidem sobre o débito (REsp 1.757.033/DF, 3ª Turma,
   rel. Min. Ricardo Villas Bôas Cueva, julgado em 09/10/2018, DJe 15/10/2018, unânime). O
   precedente foi conferido pela ementa e pelos informativos, e falta ler o inteiro teor. É
   acórdão de turma, não repetitivo, então o motor fica como está e os casos do CADEJ seguem
   registrando a divergência nos honorários e no total. A planilha da DPMG faz como o motor:
   multa e honorários são, cada um, 10% do saldo do débito (conferido em 14/11/2026 numa
   planilha gerada no [iadefensor](https://www.iadefensor.com.br/planilha/planilha_sem_menu),
   que produz o mesmo modelo de outubro/2026). Um cálculo homologado do TJMG ainda confirmaria
   a prática local.
6. **SELIC: série mensal do SGS × SELIC diária do CADEJ (#28). Resolvida: a SELIC vem da SGS
   11.** O CADEJ multiplica a SELIC diária (SGS 11) dos dias úteis. A parcela de 15/01/2024
   atualizada até 29/08/2024 dá fator 1,0617101, que é o produto exato dos 147 dias úteis de
   01/01 a 31/07/2024. O motor multiplicava a SELIC mensal (SGS 4390), publicada com duas
   casas, e chegava a 1,0617743: R$ 0,64 a mais em R$ 10.000,00. A forma de acumular é a mesma
   (produto de fatores); a diferença vinha só do arredondamento da série mensal. O Manual de
   Cálculos da Justiça Federal, ao contrário, **soma** as taxas mensais ("capitalizada de forma
   simples") e conta do mês seguinte ao termo inicial até o mês anterior ao pagamento, mais 1%
   no mês do pagamento, como nos tributos federais. O tribunal de referência é o TJMG, então o
   Debeatur segue o CADEJ e a DPMG: o `SgsClient` monta a SELIC de cada mês pelo produto da SGS
   11 nos dias do mês, sem arredondar. Um mês só entra quando a SGS 11 já tem um dia do mês
   seguinte; a SGS 4390 não serve para isso, porque publica o mês corrente pela metade (em
   19/11/2026, novembro com 0,66). Os cálculos salvos antes guardam a SELIC da 4390 no
   snapshot, e o demonstrativo deles continua citando a 4390. O motor não muda. O `GabaritoSelicDiariaTest` (`debeatur-indices`) confere esse caso do
   CADEJ em sete casas.

   A planilha "SELIC Acumulada" da DPMG faz como o CADEJ. Num caso gerado no iadefensor em
   18/11/2026 (R$ 1.000,00 por mês de 01/2024 a 12/2024, fim do cálculo em 11/2026), o fator de
   cada parcela é o **produto** de (1 + taxa do mês), do mês da parcela (inclusive) até o mês
   anterior ao fim do cálculo. A razão entre os fatores de duas parcelas seguidas devolve a
   taxa do mês, e as de 01/2024 a 11/2024 batem, em seis casas, com o produto da SGS 11 nos
   dias úteis do mês (janeiro/2024: 0,966690%, contra 0,97% da SGS 4390). A aba "TJMG" da
   planilha traz as taxas de 01/1995 a 05/2025; de 01/2024 a 05/2025, batem com a SGS 11, salvo
   março/2024, que vem com cinco casas (0,83167, contra 0,831674 no fator). O fator segue até
   10/2026, embora a aba pare em 05/2025: o trecho de 06/2025 a 10/2026 também bate com a
   SGS 11, com diferença de 2 × 10⁻⁷. O valor corrigido é (pago − valor) × fator, sem
   arredondamento (como na divergência 9). O modelo aplica a SELIC no período todo, sem a
   virada da Lei 14.905/2024, então não serve de gabarito para o Tema 1368 (#28, #32), só para
   o trecho em SELIC. O caso e as taxas estão em `debeatur-indices/src/test/resources/gabarito`
   (`selic-acumulada-dpmg-caso-2026-11.csv` e `selic-acumulada-dpmg-taxas-2026-11.csv`), e o
   `GabaritoSelicDiariaTest` os confere contra uma resposta gravada da SGS 11 de 01/2024 a
   10/2026: as taxas de 01/2024 a 05/2025 com tolerância de 5 × 10⁻⁶ ponto percentual (por
   causa de março/2024), e o fator das doze parcelas com 5 × 10⁻⁷. A diferença do fator é de
   2,4 a 2,8 × 10⁻⁷ (cerca de R$ 0,0003 por R$ 1.000,00), e a causa não é conhecida: não é o
   arredondamento das taxas mensais a seis casas, que deixa a mesma diferença. Vale como a
   divergência 1, da mesma ordem. A resposta gravada da SGS 11 é a de
   `https://api.bcb.gov.br/dados/serie/bcdata.sgs.11/dados?formato=json&dataInicial=01/01/2024&dataFinal=31/10/2026`,
   consultada em 19/11/2026, e fica em `debeatur-indices/src/test/resources/sgs`.
7. **Ritos: os três últimos meses do cálculo (DPMG) × a regra do art. 528, §7º.** Ao separar os
   ritos, a planilha da DPMG põe na prisão os três últimos meses do cálculo. O motor segue a
   lei e a Súmula 309 do STJ: as três parcelas anteriores ao ajuizamento e as que vencerem no
   curso do processo. As duas regras coincidem quando o cálculo termina no ajuizamento, que é
   o uso da planilha na petição inicial. Num cálculo atualizado no curso do processo, a regra
   da planilha mandaria para a penhora as parcelas vencidas depois do ajuizamento, que a lei
   põe na prisão. A diferença é intencional (decisão de 14/11/2026).
8. **Juros de 0,5% a.m. até 10/01/2003 (CC/1916).** As notas da planilha da DPMG citam 6% a.a.
   antes de 11/01/2003, mas ela começa em 02/2003, mesmo quando se pede início em 01/2003. Sem
   fonte que mostre como dividir janeiro/2003, o motor fica como está (decisão de 14/11/2026).
9. **Multa e honorários sem arredondar (DPMG).** Na planilha da DPMG, multa e honorários são
   `saldo × 0,1` sem arredondamento, e o total soma esses valores cheios. O motor arredonda cada
   valor monetário a duas casas antes de somar. No caso de `GabaritoRendimentosDecimoTerceiroDpmgTest`,
   cada um vale 1.217,147: a planilha mostra total de R$ 14.605,76, e o motor, R$ 14.605,77. A
   multa e os honorários são lançados nos autos em reais e centavos, então o motor fica como está.

## Planilha da DPMG

O gerador público da planilha da DPMG ([iadefensor](https://www.iadefensor.com.br/planilha/planilha_sem_menu),
modelo "Taxa legal (Bacen)") produz o modelo de outubro/2026 que está em
`planilha-dpmg-alimentos-2026-10.csv`: os fatores e os percentuais de juros vêm gravados como
números, idênticos aos do CSV, e o Excel só multiplica. Gerar outros casos nesse modelo não
traz fatores novos, mas mostra as convenções dos recursos opcionais; o modelo "SELIC
Acumulada" traz fatores próprios. Em 14/11/2026, salvo onde indicado:

- **13º salário:** o valor de dezembro dobra. Não há 13º proporcional: a pensão que começa
  no meio do ano dobra dezembro inteiro, e a que termina antes de dezembro fica sem 13º.
- **Abatimentos** (bloqueio em conta, depósito), em qualquer data: entram no total pelo valor
  nominal, sem correção; a data aparece só na descrição. Multa e honorários incidem sobre o
  saldo já abatido.
- **Percentual dos rendimentos:** o percentual incide sobre um valor de rendimentos
  informado, igual em todos os meses.
- **Valor fixo:** só nos modelos cíveis, em períodos com valor próprio e sem reajuste.
- **SELIC Acumulada** (modelo cível, conferido em 18/11/2026): fator pelo produto das taxas
  mensais tiradas da SELIC diária, sem juros à parte e sem a virada da Lei 14.905/2024. A
  coluna "DIFERENÇA" é `pago − valor`, e o valor corrigido é diferença × fator; sem
  pagamentos, as duas colunas saem negativas. A multa de 2% que o gerador anuncia para os modelos cíveis não aparece no
  arquivo. Ver a divergência 6.

O Debeatur segue essas convenções (decisão de 14/11/2026). `GabaritoRendimentosDecimoTerceiroDpmgTest`
transcreve um caso gerado no iadefensor em 15/11/2026: 30% de rendimentos de R$ 3.000,00 de
10/2025 a 09/2026, com 13º, multa, honorários e um bloqueio de R$ 500,00. O .xlsx não guarda
valores calculados, só os números de cada mês e as fórmulas; os valores esperados são essas
fórmulas, como o Excel as mostra. O total diverge em um centavo (item 9 das divergências).

O valor fixo só existe nos modelos cíveis do gerador, com multa de 2% em cada mês, que não se
aplica a alimentos. No Debeatur ele só define o valor da parcela de cada mês, e a correção e os
juros são os da pensão.

Com os ritos separados, o gerador põe os abatimentos na planilha da penhora. O Debeatur faz o
mesmo e, se eles passarem do débito da penhora, abate o resto da prisão, que o gerador deixaria
negativo.
