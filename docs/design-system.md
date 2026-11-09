# Design system

Este documento é a fonte de verdade do design system do Debeatur. Os valores dos tokens estão em
`frontend/src/estilo/tokens.css`; aqui ficam as regras de uso. Mudanças em um ou outro passam por
PR, como o resto do código. O artifact "Debeatur" no claude.ai é só uma vitrine e deve ser
gerado a partir daqui, nunca editado à parte.

O texto abaixo descreve o alvo. O que o código ainda não segue está listado em
[Diferenças com o código atual](#diferenças-com-o-código-atual).

O Debeatur calcula o cumprimento de sentença de alimentos e entrega um demonstrativo que vai para os autos. Quem usa é advogado ou servidor de contadoria: lê número por número, compara com outro cálculo e copia para a petição. A interface existe para que esses números sejam lidos certo e nunca confundidos com outros.

## Princípios

1. **O número é o produto.** Tudo o que não ajuda a ler, conferir ou copiar um valor sai da tela. Não há ilustração, ícone decorativo, sombra nem cor de marca.
2. **O que está desatualizado nunca parece valer.** Um demonstrativo que não corresponde mais ao formulário fica apagado (`opacity: 0.4`) com a faixa de situação por cima, na tela; no papel, cada página leva a marca de desatualizado, para nenhuma folha avulsa parecer válida.
3. **O rito tem cor e nome.** Prisão, penhora e sem rito têm cor própria, em degraus de luminosidade, mas a cor nunca aparece sem o nome do rito ao lado.
4. **A tela e o papel mostram a mesma coisa.** O demonstrativo é pensado para A4: o tema `impressao` e o PDF usam os mesmos fios, totais e ordem de colunas da tela.
5. **Quem confere acha os critérios.** O demonstrativo traz o que o art. 524 do CPC exige (índice, juros, termos, capitalização, descontos) e de onde vieram as séries, no mesmo papel dos números.

## O demonstrativo

A ordem dos blocos é fixa na tela, na impressão e no PDF:

1. `CabecalhoProcesso`: processo, juízo, partes e a marca de segredo de justiça.
2. `Parametros`: os critérios do cálculo, um por inciso do art. 524.
3. A linha do tempo, com a legenda dos ritos (`Rito`).
4. `TotalRito`: o total de cada rito, com a composição.
5. `TabelaMemoria`: a memória de cálculo de cada rito.
6. O total da execução (`.total-final`), aberto por `fio-resultado`.
7. `Procedencia`: as séries usadas, a data do cálculo e a regra de arredondamento.

A faixa de `Situacao` fica acima de todos.

## Conteúdo e voz

- Português do Brasil, em frases curtas e na voz ativa. Termos do processo como o juiz e o advogado os usam: *parcela*, *ajuizamento*, *citação*, *rito da prisão*, *memória de cálculo*, *débito atualizado*.
- O botão diz o que acontece: "Calcular", "Limpar", "Desfazer", "Adicionar parcela", "Baixar PDF", "Copiar tabela", "Tentar de novo". Durante a ação, o rótulo vira o gerúndio com reticências: "Calculando…", "Gerando PDF…".
- Um erro explica o que fazer, com exemplo: "Informe só números, com vírgula antes dos centavos (ex.: 1.234,56)." Nada de "valor inválido" sem dizer qual forma é a válida.
- A situação é dita com o fato em negrito e o motivo logo depois, em texto normal: "**Demonstrativo desatualizado.**" seguido do porquê.
- Dicas dizem para que serve o campo, não repetem o rótulo: "Opcionais: só identificam o processo no cabeçalho do PDF."
- Critérios se escrevem como na sentença, com a fonte entre parênteses: "INPC (IBGE)", "1% ao mês, simples", "desde o vencimento de cada parcela".
- Sem emoji, sem exclamação e sem tratamento ("você") quando a frase funciona sem ele.

## Números, datas e moeda

Siga `frontend/src/formato.ts`; é a única fonte desses formatos.

| O quê | Formato | Exemplo |
| --- | --- | --- |
| Valor em reais, isolado | moeda pt-BR | R$ 1.523,40 |
| Valor em reais, na tabela | duas casas, sem símbolo (cola em planilha) | 1.523,40 |
| Valor negativo ou dedução | hífen-menos antes do número, sem parênteses nem cor | -1.412,00 |
| Taxa de juros | quatro casas e % | 1,0000% |
| Fator de correção | sete casas | 1,0482371 |
| Data | dd/mm/aaaa | 05/03/2025 |
| Competência | mm/aaaa (mm/aa no eixo estreito) | 03/2025 |
| Período | "de" e "a" | de 03/2025 a 09/2025 |
| Número do processo | padrão CNJ, sempre completo | 0001234-56.2025.8.13.0024 |
| Sem data | travessão | — |

- Todo algarismo que se compara na vertical usa `font-variant-numeric: tabular-nums`: tabelas, totais, campos e eixo. A Atkinson Hyperlegible Next tem o recurso `tnum`.
- Valores ficam alinhados à direita; a primeira coluna (competência ou descrição) fica à esquerda.
- Como a tabela não repete "R$", a legenda dela diz a unidade: "Valores em R$".
- Dedução vai em linha própria, com o rótulo começando por "(−)" e o valor com hífen-menos, o mesmo sinal que o `Intl.NumberFormat` usa e que as planilhas reconhecem ao colar. Valor negativo nunca é indicado só por cor.
- Cada valor monetário é arredondado a duas casas, meio para cima, antes de entrar numa soma; a `Procedencia` diz isso no papel.
- O campo numérico aceita vírgula decimal e é de texto (`CampoDecimal`), nunca `type="number"`.

## Cor

- Fundo da página em `fundo`; tudo o que contém dado (campo, tabela, cartão) em `superficie`.
- Texto em `texto`; rótulos, dicas e cabeçalhos de tabela em `texto-suave`. Valores monetários são sempre `texto`.
- A ação principal usa `destaque`, que é a mesma tinta de `texto`. O sistema não tem cor de marca: as únicas cores são as dos ritos, as de estado e a do foco.
- `prisao`, `penhora` e `sem-rito` só identificam o rito (barra, amostra, marcador do título). Não servem para estado, ênfase nem texto. A mensagem de status das ações do resultado fica em `texto-suave`.
- Os ritos diferem em luminosidade, não só em matiz: a prisão é a de maior contraste com o fundo, a penhora fica no meio e o sem rito é a mais próxima do fundo, em degraus de 1,7:1 ou mais nos dois temas. Assim o gráfico continua legível impresso em preto e branco e para quem não distingue vermelho de verde.
- `erro` é para falha do cálculo e campo inválido; `aviso` para dado que pede atenção e para o demonstrativo desatualizado, com `aviso-fundo` na faixa.
- `foco` (azul) desenha o contorno de 2px de qualquer controle. É a única cor que não é de rito nem de estado, para o foco não ser lido como penhora; tem 5:1 ou mais sobre `fundo`, `superficie` e `aviso-fundo` nos dois temas.
- `borda-forte` contorna campos e botões secundários com 3:1 ou mais sobre todo fundo em que eles aparecem. `borda` é decorativa e não delimita controle.
- Cada cor tem valor para o tema claro e o escuro, que segue `prefers-color-scheme`. O tema `impressao` só redefine fundos, texto e bordas.

## Tipografia

- Uma família só: Atkinson Hyperlegible Next, desenhada para distinguir caracteres parecidos (1, l e I; 0 e O), o que importa num documento cheio de números.
- Pesos: 400 no texto, 600 em rótulos de botão, cabeçalhos de tabela e erros, 700 em títulos e totais, 750 só em `titulo-pagina` e `valor-total`.
- Use os estilos pelo nome: `titulo-pagina`, `titulo-resultado`, `titulo-secao`, `titulo-grupo`, `corpo`, `rotulo`, `nota`, `valor-total`, `tabela`. Nada abaixo de `nota` (12px) na tela; no papel, nada abaixo de 9pt.
- Base de 16px com entrelinha 1,5. Dicas e textos explicativos param em `largura-leitura` (72ch).

## Espaço e layout

- Use a escala `espaco-1` a `espaco-8` (4 a 80px). Os ajustes de 1 a 3px de alinhamento óptico são a única exceção.
- Até `quebra-duas-colunas` (1200px), formulário e demonstrativo ficam empilhados, com `largura-pagina`. A partir dela, o formulário fica fixo à esquerda (380 a 460px, com rolagem própria e o botão Calcular sempre visível no pé) e o demonstrativo rola à direita.
- Grades de campos respondem à largura da própria coluna (container queries `formulario` e `resultado`), não à da tela.
- A memória de cálculo nunca encolhe abaixo de `largura-coluna-tabela` por coluna: ela rola de lado dentro de `.rolagem`, nunca a página.
- Tudo o que fica fixo na rolagem (a faixa de situação, o pé do formulário) reserva o próprio espaço com `scroll-padding`, para nunca cobrir o campo que recebeu o foco (WCAG 2.4.11).

## Bordas, fios e raios

O demonstrativo usa a convenção do papel contábil:

- `fio-divisao` (1px em `borda`) separa linhas e blocos.
- `fio-subtotal` (1px em `texto`) fica acima do subtotal.
- `fio-total` (3px duplo em `texto`) fica acima do total de cada tabela.
- `fio-resultado` (2px em `texto`) abre o demonstrativo e o total da execução.
- `fio-vazio` (tracejado) indica ausência: o resultado ainda vazio, a parcela já paga no gráfico.

Os raios são três: `raio-amostra` (3px) nas amostras e barras, `raio-controle` (6px) em campos e botões, `raio-cartao` (8px) nos cartões de parcela. O sistema não usa sombra: hierarquia vem de fio e espaço.

## Estados

- **Foco:** contorno `foco` de 2px. Campos também trocam a borda para `foco`.
- **Campo inválido:** `aria-invalid="true"`, borda em `erro` e a `MensagemErro` logo abaixo, ligada por `aria-describedby`. A mensagem fica na tela até o dado ser corrigido.
- **Desabilitado:** `opacity: 0.6`. Só o botão que espera uma resposta (Calcular, Baixar PDF) mostra `cursor: progress`; os outros, `not-allowed`.
- **Situação do demonstrativo** (`data-situacao`): `atual` não mostra faixa; `atualizando` apaga os números a 0.6 e mostra "Atualizando o demonstrativo…"; `desatualizado` apaga a 0.4 e mostra a faixa em `aviso-fundo` com borda `aviso`. A faixa acompanha a rolagem e é uma região `role="status"`.
- **Hover em linha de tabela:** `borda` a 35%, para seguir a linha com o olho. Some na impressão.

## Movimento

Só um: as barras da linha do tempo crescem de baixo para cima em 0,8s (`cubic-bezier(0.16, 1, 0.3, 1)`) quando o gráfico aparece, e só com `prefers-reduced-motion: no-preference`. A transição de opacidade do demonstrativo espera 0,15s para não piscar a cada tecla.

## Iconografia

Não há ícones nem logotipo. O nome "Debeatur" aparece em texto, no estilo `titulo-pagina`. Os únicos marcadores gráficos são a amostra quadrada de 12px com `raio-amostra`, na cor do rito, e o padrão hachurado das parcelas pagas.

## Impressão e PDF

- Ctrl+P aplica o tema `impressao`: A4, margens de 14 por 12mm, tabela em 9pt, formulário e ações escondidos. Com mais de sete colunas visíveis, a memória sai em A4 paisagem.
- Toda página leva o número do processo e "Página X de Y" no rodapé, e a data e a hora do cálculo no cabeçalho. Uma folha solta sempre diz de onde veio.
- Um demonstrativo que não está atual leva a marca `.marca-pagina` em todas as páginas, não só na primeira: no navegador ela é `position: fixed`, que se repete a cada página impressa; no PDF, vai no cabeçalho.
- O total de cada tabela não se separa das últimas linhas (`break-inside: avoid` no `<tfoot>`), e o cabeçalho da tabela se repete em cada página.
- O PDF gerado pelo `debeatur-api` deve seguir os mesmos tokens e a mesma fonte (a Atkinson tem licença OFL e pode ser embutida). Hoje ele tem paleta e fonte próprias (Helvetica, texto `#1f2933`, rótulo `#5b6773`, linha `#c4cad0`), que destoam da tela.

## Sigilo

Ações de alimentos tramitam em segredo de justiça (art. 189, II, do CPC). O `CabecalhoProcesso` diz isso na tela, e o PDF, em toda página. O caso guardado no navegador fica só nele, e Limpar apaga também essa cópia.

## Acessibilidade

- Referência: WCAG 2.2, nível AA, a base do eMAG e da Resolução CNJ 401/2021.
- Texto com contraste de 4,5:1 ou mais sobre o fundo indicado em cada token, nos dois temas. O contorno dos controles (`borda-forte`), o contorno de foco e as amostras e barras dos ritos, 3:1 ou mais.
- O rito nunca é indicado só pela cor: o título da tabela e do total diz o nome, e a legenda acompanha o gráfico. As cores dos ritos também diferem em luminosidade.
- Toda tabela tem `<caption>` com o rito e a unidade, `scope="col"` no cabeçalho e `scope="row"` na primeira coluna. A `.rolagem` em volta tem `tabIndex={0}`, `role="region"` e nome, para rolar de lado pelo teclado.
- Mensagens de validação ficam escritas abaixo do campo (`MensagemErro`), ligadas por `aria-describedby`. `setCustomValidity` continua, mas sozinho o balão do navegador some e não é lido do mesmo jeito em todo leitor de tela. Com dois erros ou mais depois de Calcular, o resumo de erros abre o formulário e recebe o foco.
- Alvos de clique e toque com 24 por 24px ou mais (WCAG 2.5.8): o botão `compacto` tem 29px de altura.

## Diferenças com o código atual

O texto acima descreve coisas que o frontend ainda não implementa. Cada uma entra num PR próprio,
que atualiza este documento junto.

- **`foco`:** no código, `--foco` tem o mesmo valor de `--penhora` (`#2f6f7e` no claro, `#6fbac8`
  no escuro). O alvo é uma cor própria, azul, para o foco não ser lido como penhora.
- **Tokens que ainda não existem:** `aviso-fundo`, a escala `espaco-1` a `espaco-8`, os fios
  (`fio-divisao`, `fio-subtotal`, `fio-total`, `fio-resultado`, `fio-vazio`), os raios
  (`raio-amostra`, `raio-controle`, `raio-cartao`), `largura-leitura`, `largura-pagina`,
  `largura-coluna-tabela`, `quebra-duas-colunas` e os estilos de texto pelo nome. Hoje esses
  valores aparecem literais no `App.css`.
- **Componentes que ainda não existem:** `MensagemErro`, `CabecalhoProcesso`, `Parametros` (como
  componente), `TotalRito`, `TabelaMemoria`, `Procedencia` e `Situacao`.
- **Ritos por luminosidade e `borda-forte` com 3:1:** os valores atuais ainda não foram
  conferidos contra essas metas.
- **PDF do `debeatur-api`:** usa Helvetica e uma paleta própria, em vez dos tokens e da Atkinson.
