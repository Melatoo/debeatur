# Requisitos do MVP

Decisões de produto que orientam as escolhas técnicas do primeiro release (issue #1). Em
27/10/2026 o recorte passou do cumprimento de sentença cível em quantia certa para a execução
de alimentos; o motor de correção e juros é o mesmo.

## Usuário-alvo

Advogado de família, defensor ou servidor de contadoria que hoje monta o demonstrativo da
execução de alimentos em planilha, como a planilha modelo da Defensoria Pública de MG.

- Um escritório é um tenant, com um único usuário no MVP. O modelo de dados já nasce com
  `escritorio_id` (é barato agora e caro de adicionar depois).
- Não há perfis ou permissões no MVP.
- Depois do MVP (#5), o escritório passa a ter vários usuários, que entram por convite por
  e-mail, cada um com um perfil: o admin gerencia os usuários e vê todos os cálculos do
  escritório; o membro vê só os cálculos que ele mesmo salvou.

## Recorte jurídico

**Execução de alimentos** (arts. 528 a 533 do CPC), com o demonstrativo que acompanha o
pedido.

- **Pensão em percentual do salário mínimo:** uma parcela por mês, no dia de vencimento do
  título, pelo salário mínimo vigente em cada vencimento (Súmula 490 do STF), com correção e
  juros desde cada vencimento (#21).
- **Outras parcelas:** valores avulsos com vencimento próprio, além da pensão.
- **Pensão em percentual dos rendimentos:** o percentual sobre um valor de rendimentos
  informado, igual em todos os meses, como na planilha da DPMG. Se os rendimentos mudaram
  (reajuste, novo emprego), quem calcula informa o novo valor a partir de cada mês, que vale
  até a próxima mudança, sem reajuste automático.
- **Pensão em valor fixo:** períodos com valor próprio, sem reajuste automático, como na
  planilha da DPMG. Cada valor vale do mês informado até o próximo.
- **13º salário:** opcional; o valor de dezembro dobra, como na planilha da DPMG.
- **Termo inicial dos juros:** configurável entre o vencimento de cada parcela e a citação.
- **Pagamentos parciais:** abatidos da parcela do mesmo mês, sem imputação do art. 354 do CC,
  como na planilha da DPMG.
- **Abatimentos:** valores recebidos em qualquer data, mesmo depois do período cobrado
  (bloqueio em conta, depósito), saem do total pelo valor nominal, sem correção, e multa e
  honorários incidem sobre o saldo, como na planilha da DPMG. Com os ritos separados, saem do
  débito da penhora, onde a DPMG os põe; o que passar dele sai do da prisão.
- **Dois ritos:** com a data do ajuizamento, as parcelas se dividem entre o rito da prisão e o
  da penhora, com subtotais separados. Vão para a prisão os três últimos meses do cálculo, como
  a planilha da DPMG (padrão), ou as três parcelas anteriores ao ajuizamento e as que vencerem
  no curso do processo, como a lei (art. 528, §7º, do CPC) (`docs/gabarito.md`, divergência 7).
  Os acréscimos do art. 523, §1º (multa e honorários de 10%), incidem só sobre a penhora.
- **Base dos honorários do art. 523:** sobre o débito, como a planilha da DPMG (padrão), ou
  sobre o débito com a multa, como o CADEJ (`docs/gabarito.md`, divergência 5).
- **Prescrição:** aviso das parcelas vencidas mais de dois anos antes do ajuizamento (art. 206,
  §2º, do CC), lembrando que ela não corre contra absolutamente incapaz (art. 198, I) nem entre
  ascendentes e descendentes durante o poder familiar (art. 197, II). O cálculo não as exclui.

## Tribunal de referência

**TJMG.** Os cálculos de gabarito vêm da contadoria do TJMG, de processos homologados lá e da
planilha da DPMG (`docs/gabarito.md`).

- **Regime padrão:** tabela da Corregedoria-Geral de Justiça do TJMG (base INPC) + 1% a.m. até
  29/08/2024 e, depois, IPCA + taxa legal (Lei 14.905/2024). A edição da tabela vigente em
  29/08/2024 só tem INPC até julho, então agosto/2024 já é corrigido pelo IPCA (#24). Em
  agosto/2024, os juros são 1% até o dia 29 e taxa legal pro rata nos dias 30 e 31.
- **Regime pelo título:** a sentença pode fixar critérios próprios (coisa julgada), então o
  regime é um dado do cálculo. Quem calcula informa índice e juros por período no regime
  próprio.
- **Tema 1368 do STJ:** para título omisso, a taxa do art. 406 do CC antes da Lei 14.905/2024 é
  a SELIC. O padrão continua sendo a tabela da CGJ + 1% a.m.; a SELIC entra como critério que
  quem calcula informa (#28).

## Definição de pronto

O MVP está pronto para uso em processo real quando:

1. **Bate com o gabarito:** diferença zero (ou de centavos, justificada) contra o gabarito de
   `docs/gabarito.md`. Em alimentos, a referência é a planilha da DPMG: o modelo de
   outubro/2026 (`planilha-dpmg-alimentos-2026-10.csv`) e os casos gerados no iadefensor. A
   tabela da CGJ e o CADEJ conferem o regime padrão.
2. **Alimentos:** pensão em salário mínimo, outras parcelas, pagamentos parciais e os dois
   ritos. *Feito (#34 a #41).* Percentual dos rendimentos, valor fixo, 13º salário e
   abatimentos, decididos em 14/11/2026, também.
3. **Regime configurável:** o padrão do TJMG e um regime próprio por cálculo. *Feito, com o
   regime do Tema 1368 para o título omisso (#28).*
4. **Pro rata die:** termos iniciais e data-base no meio do mês (#3). *Em parte: só a virada de
   agosto/2024 é proporcional; o resto do cálculo é mensal.*
5. **Salva cálculos:** reabrir e atualizar um cálculo, com snapshot dos índices usados.
   *Feito (#4).*
6. **Exporta PDF:** demonstrativo com memória de cálculo, total e marca de segredo de justiça
   em toda página, pronto para juntar aos autos. *Feito (#6).*
7. **Autenticação mínima:** login de um usuário por escritório, com limite de tentativas.
   *Feito (#7).*

## Fora do escopo

- Exclusão das parcelas prescritas do cálculo, que mexe no motor e precisa de gabarito
- Imputação do pagamento (art. 354 do CC)
- Fazenda Pública (EC 113/2021, Tema 810 do STF, RPV/precatório)
- Trabalhista (ADC 58)
- Tabelas e regimes pré-definidos de outros tribunais
- Múltiplos usuários por escritório e controle de permissões (feitos depois do MVP, na #5)

O cálculo cível em quantia certa, recorte original do MVP, continua no motor e na API, mas não
é o foco do produto. Os itens fora do escopo estão na issue #5.
