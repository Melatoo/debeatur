# Requisitos do MVP

Decisões de produto que orientam as escolhas técnicas do primeiro release (issue #1).

## Usuário-alvo

Advogado autônomo ou escritório pequeno que hoje monta o demonstrativo do art. 524 do CPC
em planilha.

- Um escritório é um tenant. O MVP pode ter um único usuário por escritório, mas o modelo
  de dados já nasce com `escritorio_id` (é barato agora e caro de adicionar depois).
- Não há perfis ou permissões no MVP.

## Recorte jurídico

**Cumprimento de sentença cível entre particulares:** condenações em quantia certa (danos
materiais, danos morais, cobrança).

- Termos iniciais distintos para correção e juros por parcela (ex.: Súmulas 43, 54 e 362
  do STJ), o que o modelo `Parcela` já suporta.
- Acréscimos do art. 523, §1º (multa de 10% e honorários de 10%).

## Tribunal de referência

**TJMG.** Os cálculos de gabarito virão da contadoria do TJMG ou de processos homologados lá.

- Correção até 07/2024: fatores da tabela da Corregedoria-Geral de Justiça do TJMG (base
  INPC). A edição vigente em 29/08/2024 só tem INPC até julho, então agosto/2024 já é
  corrigido pelo IPCA (#24). Juros de 1% a.m. até 29/08/2024; em agosto/2024, 29/30 de 1%
  mais 2/31 da taxa legal de agosto, como a planilha da DPMG (#3).
  A confirmar: se os fatores publicados batem com o INPC puro do SGS ou se a tabela precisa
  entrar como série própria.
- A partir de 09/2024: IPCA + taxa legal (Lei 14.905/2024). A confirmar: como o TJMG
  operacionalizou a mudança (se publica tabela própria e se trata 30 e 31/08/2024 como a DPMG).

## Definição de pronto

O MVP está pronto para uso em processo real quando:

1. **Bate com o gabarito:** diferença zero (ou de centavos, justificada) contra os casos
   da issue #2.
2. **Pro rata die:** termos iniciais e data-base no meio do mês (issue #3).
3. **Regime básico configurável:** informar os períodos, o índice de correção e os juros
   fixados na sentença, com o regime padrão do TJMG como default (issue #3).
4. **Salva cálculos:** reabrir e atualizar um cálculo, com snapshot dos índices usados (issue #4).
5. **Exporta PDF:** demonstrativo com memória de cálculo pronto para juntar aos autos (issue #6).
6. **Autenticação mínima:** para salvar cálculos em uma aplicação hospedada é preciso login,
   então entra um usuário por escritório (issue #7).

## Fora do escopo

- Fazenda Pública (EC 113/2021, Tema 810 do STF, RPV/precatório)
- Trabalhista (ADC 58)
- Tabelas e regimes pré-definidos de outros tribunais
- Pagamentos parciais e imputação (art. 354 do CC)
- Exportação em Excel
- Múltiplos usuários por escritório e controle de permissões

Os itens fora do escopo estão na issue #5.
