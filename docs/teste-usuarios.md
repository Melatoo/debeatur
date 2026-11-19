# Roteiro do teste com usuários

Sessões de observação com quem calcula execução de alimentos, previstas na #72. O objetivo é
decidir com evidência o que hoje é palpite: a ordem do formulário, o que precisa estar à vista
no demonstrativo e quem é o usuário (só o advogado, ou também a contadoria).

## Participantes

- **Cinco advogados de família** que já fizeram cálculo de execução de alimentos, de
  preferência com a planilha da DPMG, o CADEJ ou a calculadora da Debit.
- **Um servidor da contadoria do TJMG**, para a tarefa de conferir um cálculo de terceiro.

Ninguém do projeto participa como usuário. Cinco sessões bastam para achar a maior parte dos
problemas de uso; mais que isso repete o que já apareceu.

## Preparação

- App rodando com `docker compose up -d --build`, num computador com a tela que o participante
  usa no dia a dia (notebook, se for o caso). O participante entra com o usuário do escritório.
- Um cálculo salvo com o nome "Silva × Silva", montado a partir do caso da tarefa 1, mas com
  **um erro plantado**: o dia do vencimento 5 em vez de 10. A tarefa 2 mede se o erro aparece
  na conferência.
- Um segundo cálculo salvo, "Souza × Souza", com data-base de três meses atrás, para a tarefa 3.
- A sentença fictícia abaixo impressa em papel, como chegaria nos autos.
- Gravação de tela e áudio, com autorização por escrito do participante.

### Sentença fictícia (tarefa 1)

> Fixo os alimentos em 30% do salário mínimo, devidos todo dia 10, a partir de março de 2023.
> [...] O executado pagou R$ 200,00 em 15/06/2023 e R$ 300,00 em 12/01/2024. A execução foi
> ajuizada em 20/03/2025, cobrando pelo rito da prisão as três prestações anteriores ao
> ajuizamento e as que vencerem no curso do processo (art. 528, §7º, do CPC), e as demais pelo
> rito da penhora. Houve bloqueio de R$ 1.000,00 em conta em 10/07/2025.

O facilitador não diz como o app chama cada coisa. As tarefas usam as palavras dos autos.

## Roteiro (cerca de 50 minutos)

**Abertura (5 min).** Explique que o teste é do app, não da pessoa, e peça que ela pense em voz
alta. Pergunte como calcula hoje, com que ferramenta e quanto tempo leva um cálculo.

**Tarefa 1: montar o cálculo da sentença (15 min).** "Com esta sentença em mãos, faça o cálculo
para juntar à execução, atualizado até hoje, e gere o arquivo para os autos."

**Tarefa 2: conferir um cálculo pronto (10 min).** "Um colega deixou salvo o cálculo Silva ×
Silva, feito a partir da mesma sentença. Confira se ele está certo antes de protocolar."

**Tarefa 3: atualizar um cálculo antigo (5 min).** "O cálculo Souza × Souza foi feito há três
meses. Atualize-o para hoje e diga quanto o devedor deve agora."

**Perguntas finais (10 min).**

1. O que você conferiria antes de juntar este demonstrativo aos autos? Ele tem o que precisa?
2. Em que ordem você lê a sentença para montar o cálculo? (Compare com a ordem do formulário.)
3. Já calculou pensão em valor fixo ou em percentual dos rendimentos? Como foi?
4. Você usaria isto no celular, por exemplo em audiência?
5. Para o servidor da contadoria: conferir cálculo de terceiro é parte do seu trabalho? O que
   faltou aqui para isso?

**Fechamento (5 min).** Agradeça e pergunte se há algo que não perguntamos.

## O que observar

| Pergunta em aberto (#72) | Onde aparece | Sinal |
|---|---|---|
| Ordem do formulário | Tarefa 1, pergunta 2 | Idas e voltas entre blocos; procurar a data de ajuizamento ou a data-base fora de Processo |
| Total à vista | Tarefas 1 e 3 | Quanto tempo até dizer o total; se rola a página para achá-lo |
| Conferência | Tarefa 2 | Se acha o vencimento errado, e por onde: parâmetros, memória de cálculo ou PDF |
| Ritos e abatimentos | Tarefa 1 | Se separa os ritos sem ajuda; onde lança o bloqueio em conta |
| Contadoria como usuária | Tarefa 2, pergunta 5 | O que o servidor procura e não encontra |
| Celular | Pergunta 4 | Se existe o caso de uso, e em que momento |

Anote também cada vez que a pessoa pedir ajuda, o que ela esperava encontrar e o que encontrou.

## Registro

Uma nota por sessão, no mesmo dia, com: perfil do participante, tempo de cada tarefa, se
concluiu (sozinho, com ajuda, não concluiu), problemas observados com o trecho da gravação e
frases marcantes. Depois da última sessão, junte os problemas que apareceram em duas ou mais
sessões num comentário na #72, com a proposta de mudança de cada um. A reordenação do
formulário só entra se o padrão aparecer.

Não guarde no repositório nome de participante, gravação nem dado de processo real.
