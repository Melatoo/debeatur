import { describe, expect, it } from 'vitest'
import type { CalculoRequest, Regime, ResumoCalculo } from './api'
import { paraPedido, rascunhoVazio } from './caso'
import { atualizarRascunho, filtrarCalculos, novaDataBase, paraRascunho, regimeSalvo, tituloPadrao } from './calculoSalvo'
import { chaveDoPedido } from './recalculo'
import type { Rascunho } from './rascunho'

const padrao: Regime = {
  nome: 'Regime cível padrão do TJMG',
  regras: [
    { inicio: '1964-01', fim: '2024-07', correcao: 'INPC', juros: 'TAXA_FIXA', percentualJurosMensal: 1, ultimoDiaTaxaFixa: null },
    { inicio: '2024-08', fim: null, correcao: 'IPCA', juros: 'TAXA_LEGAL', percentualJurosMensal: null, ultimoDiaTaxaFixa: null },
  ],
}

const tema: Regime = {
  nome: 'Tema 1368/STJ (SELIC até 07/2024; Lei 14.905/2024)',
  regras: [
    { inicio: '2003-01', fim: '2024-07', correcao: 'SELIC', juros: 'SEM_JUROS', percentualJurosMensal: null, ultimoDiaTaxaFixa: null },
    { inicio: '2024-08', fim: null, correcao: 'IPCA', juros: 'TAXA_LEGAL', percentualJurosMensal: null, ultimoDiaTaxaFixa: null },
  ],
}

/** Como o servidor devolve: decimais em número JSON, regime resolvido e com a descrição dos juros. */
const salvo = {
  dataBase: '2025-09-30',
  aplicarArt523: true,
  parcelas: [{ descricao: 'Despesa escolar', valor: 350.5, inicioCorrecao: '2025-02-10', inicioJuros: null }],
  pensao: {
    percentual: 30,
    diaVencimento: 10,
    primeiroMes: '2024-04',
    ultimoMes: '2025-09',
    inicioJuros: 'VENCIMENTO',
    citacao: null,
    pagamentos: [{ mes: '2024-06', valor: 200 }],
    ajuizamento: '2025-03-15',
  },
  regime: {
    nome: padrao.nome,
    regras: padrao.regras.map((r) => ({ ...r, descricaoJuros: 'descrição do servidor' })),
  },
  processo: { numero: '5001234-56.2025.8.13.0024', juizo: null, exequente: 'Fulana', executado: null },
} as unknown as CalculoRequest

describe('paraRascunho', () => {
  it('devolve ao formulário os campos do pedido salvo, com os decimais à brasileira', () => {
    const r = paraRascunho(salvo, padrao)
    expect(r.dataBase).toBe('2025-09-30')
    expect(r.processo).toEqual({ numero: '5001234-56.2025.8.13.0024', juizo: '', exequente: 'Fulana', executado: '' })
    expect(r.comPensao).toBe(true)
    expect(r.separarRitos).toBe(true)
    expect(r.pensao).toEqual({
      base: 'SALARIO_MINIMO',
      percentual: '30',
      rendimentos: '',
      valoresFixos: [{ aPartirDe: '', valor: '' }],
      decimoTerceiro: false,
      diaVencimento: '10',
      primeiroMes: '2024-04',
      ultimoMes: '2025-09',
      inicioJuros: 'VENCIMENTO',
      citacao: '',
      pagamentos: [{ mes: '2024-06', valor: '200,00' }],
      abatimentos: [],
      ajuizamento: '2025-03-15',
    })
    expect(r.parcelas).toEqual([{ descricao: 'Despesa escolar', valor: '350,50', inicioCorrecao: '2025-02-10', inicioJuros: '' }])
    expect(r.outrasAbertas).toBe(true)
  })

  it('o formulário reaberto gera o mesmo pedido que foi salvo', () => {
    const pedido = paraPedido(paraRascunho(salvo, padrao))
    expect(pedido).toEqual({
      dataBase: '2025-09-30',
      aplicarArt523: true,
      parcelas: [{ descricao: 'Despesa escolar', valor: '350.50', inicioCorrecao: '2025-02-10', inicioJuros: null }],
      pensao: {
        percentual: '30',
        diaVencimento: 10,
        primeiroMes: '2024-04',
        ultimoMes: '2025-09',
        inicioJuros: 'VENCIMENTO',
        citacao: null,
        pagamentos: [{ mes: '2024-06', valor: '200.00' }],
        ajuizamento: '2025-03-15',
      },
      regime: undefined,
    })
  })

  it('reabre a base dos honorários escolhida, e o padrão não vai no pedido', () => {
    expect(paraRascunho(salvo, padrao).honorariosSobreAMulta).toBe(false)

    const comMulta = { ...salvo, baseHonorarios: 'DEBITO_COM_MULTA' } as CalculoRequest
    const r = paraRascunho(comMulta, padrao)
    expect(r.honorariosSobreAMulta).toBe(true)
    expect(paraPedido(r).baseHonorarios).toBe('DEBITO_COM_MULTA')
    // sem o art. 523, a base não importa e fica fora do pedido
    expect(paraPedido({ ...r, aplicarArt523: false })).not.toHaveProperty('baseHonorarios')
  })

  it('reabre a pensão em valor fixo com 13º e abatimentos e gera o mesmo pedido', () => {
    const comNovidades = {
      ...salvo,
      pensao: {
        ...salvo.pensao,
        base: 'VALOR_FIXO',
        percentual: null,
        valoresFixos: [
          { aPartirDe: '2024-04', valor: 500 },
          { aPartirDe: '2025-01', valor: 650.5 },
        ],
        decimoTerceiro: true,
        abatimentos: [{ data: '2025-10-02', valor: 300, origem: 'Bloqueio' }],
      },
    } as unknown as CalculoRequest

    const r = paraRascunho(comNovidades, padrao)
    expect(r.pensao.valoresFixos).toEqual([
      { aPartirDe: '2024-04', valor: '500,00' },
      { aPartirDe: '2025-01', valor: '650,50' },
    ])
    expect(r.pensao.abatimentos).toEqual([{ data: '2025-10-02', valor: '300,00', origem: 'Bloqueio' }])
    expect(paraPedido(r).pensao).toEqual({
      base: 'VALOR_FIXO',
      percentual: null,
      valoresFixos: [
        { aPartirDe: '2024-04', valor: '500.00' },
        { aPartirDe: '2025-01', valor: '650.50' },
      ],
      decimoTerceiro: true,
      diaVencimento: 10,
      primeiroMes: '2024-04',
      ultimoMes: '2025-09',
      inicioJuros: 'VENCIMENTO',
      citacao: null,
      pagamentos: [{ mes: '2024-06', valor: '200.00' }],
      abatimentos: [{ data: '2025-10-02', valor: '300.00', origem: 'Bloqueio' }],
      ajuizamento: '2025-03-15',
    })
  })

  it('reconhece o regime padrão salvo por extenso e volta ao "Regime padrão do TJMG"', () => {
    const r = paraRascunho(salvo, padrao)
    expect(r.regimeProprio).toBe(false)
    expect(r.regras).toEqual([])
  })

  it('se o padrão mudou desde o salvamento, mantém as regras salvas como critérios do título', () => {
    const novoPadrao: Regime = { ...padrao, regras: [{ ...padrao.regras[1], inicio: '1964-01' }] }
    const r = paraRascunho(salvo, novoPadrao)
    expect(r.regimeProprio).toBe(true)
    expect(r.nomeRegime).toBe(padrao.nome)
    expect(r.regras.map((g) => [g.inicio, g.fim, g.correcao, g.juros, g.percentual])).toEqual([
      ['1964-01', '2024-07', 'INPC', 'TAXA_FIXA', '1'],
      ['2024-08', '', 'IPCA', 'TAXA_LEGAL', ''],
    ])
    // Reenviado, o regime é o mesmo que foi salvo.
    expect(paraPedido(r).regime).toEqual({ nome: padrao.nome, regras: padrao.regras })
  })

  it('sem o padrão em mãos, mantém as regras salvas, para o regime nunca mudar ao reabrir', () => {
    expect(paraRascunho(salvo, null).regimeProprio).toBe(true)
  })

  it('reconhece o regime do Tema 1368 de hoje e volta à opção dele, com o regime salvo no pedido', () => {
    const comTema = { ...salvo, regime: tema }
    const r = paraRascunho(comTema, padrao, tema)
    expect([r.tema1368, r.regimeProprio, r.regras, r.nomeRegime]).toEqual([true, false, [], ''])
    expect(paraPedido(r).regime).toEqual(tema)
    // Sem o regime carregado, o pedido não sai: iria ao padrão do TJMG.
    expect(() => paraPedido({ ...r, regimeTema1368: null })).toThrow('Tema 1368')
  })

  it('com o Tema 1368, recusa termo inicial anterior ao começo do regime e aponta os outros critérios', () => {
    const r = paraRascunho({ ...salvo, regime: tema }, padrao, tema)
    const avulsa = { descricao: 'Antiga', valor: '100,00', inicioCorrecao: '2002-12-10', inicioJuros: null }
    expect(() => paraPedido({ ...r, parcelas: [...r.parcelas, avulsa] })).toThrow(
      'O Tema 1368 vale desde 01/2003, com o CC/2002, e o cálculo começa em 12/2002.',
    )
    expect(() => paraPedido({ ...r, pensao: { ...r.pensao, primeiroMes: '2002-11' } })).toThrow('começa em 11/2002')
    expect(paraPedido({ ...r, parcelas: [{ ...avulsa, inicioCorrecao: '2003-01-05' }] }).regime).toEqual(tema)
  })

  it('se o regime do Tema 1368 mudou, ou não veio, as regras salvas entram como critérios do título', () => {
    const comTema = { ...salvo, regime: tema }
    const temaDeHoje: Regime = { ...tema, regras: [{ ...tema.regras[0], fim: '2024-08' }, { ...tema.regras[1], inicio: '2024-09' }] }
    for (const hoje of [temaDeHoje, null]) {
      const r = paraRascunho(comTema, padrao, hoje)
      expect([r.tema1368, r.regimeProprio, r.nomeRegime]).toEqual([false, true, tema.nome])
      expect(paraPedido(r).regime).toEqual(tema)
    }
  })

  it('critérios do título sem nome voltam sem o nome que o servidor deu', () => {
    const proprio = { ...salvo, regime: { nome: 'Critérios fixados no título', regras: [padrao.regras[1]] } }
    const r = paraRascunho(proprio, padrao)
    expect(r.regimeProprio).toBe(true)
    expect(r.nomeRegime).toBe('')
  })

  it('sem ajuizamento, os ritos voltam sem separação; com juros desde a citação, a citação volta', () => {
    const pensao = { ...salvo.pensao!, ajuizamento: null, inicioJuros: 'CITACAO' as const, citacao: '2024-05-20' }
    const r = paraRascunho({ ...salvo, pensao }, padrao)
    expect(r.separarRitos).toBe(false)
    expect(r.pensao.citacao).toBe('2024-05-20')
    expect(paraPedido(r).pensao?.ajuizamento).toBeNull()
  })

  it('sem pensão, abre só com as parcelas avulsas', () => {
    const r = paraRascunho({ ...salvo, pensao: undefined, processo: undefined }, padrao)
    expect(r.comPensao).toBe(false)
    expect(r.outrasAbertas).toBe(false)
    expect(r.parcelas).toHaveLength(1)
    expect(r.processo).toEqual({ numero: '', juizo: '', exequente: '', executado: '' })
  })

  it('a chave do formulário reaberto não depende da ordem das chaves do JSON salvo', () => {
    const embaralhado = JSON.parse(JSON.stringify({ processo: salvo.processo, regime: salvo.regime, ...salvo }))
    expect(chaveDoPedido(paraPedido(paraRascunho(embaralhado, padrao)))).toBe(
      chaveDoPedido(paraPedido(paraRascunho(salvo, padrao))),
    )
  })
})

describe('regimeSalvo', () => {
  it('reconhece o padrão de hoje, salvo por extenso', () => {
    expect(regimeSalvo(salvo, padrao)).toBe('padrao')
  })

  it('diz quando o padrão mudou desde o salvamento', () => {
    const novoPadrao: Regime = { ...padrao, regras: [...padrao.regras, { ...padrao.regras[1], inicio: '2026-01' }] }
    expect(regimeSalvo(salvo, novoPadrao)).toBe('padrao-anterior')
  })

  it('sem o padrão de hoje, não dá para saber', () => {
    expect(regimeSalvo(salvo, null)).toBe('desconhecido')
  })

  it('critérios fixados no título não são o padrão', () => {
    const proprio = { ...salvo, regime: { nome: 'Critérios fixados no título', regras: [padrao.regras[1]] } }
    expect(regimeSalvo(proprio, padrao)).toBe('proprio')
  })
})

describe('tituloPadrao', () => {
  it('usa o número do processo ou, sem ele, a data-base, como o servidor', () => {
    expect(tituloPadrao({ dataBase: '2025-09-30', processo: { numero: ' 123 ', juizo: '', exequente: '', executado: '' } })).toBe(
      'Processo 123',
    )
    expect(tituloPadrao({ dataBase: '2025-09-30' })).toBe('Cálculo com data-base em 30/09/2025')
  })
})

describe('novaDataBase', () => {
  const hoje = new Date(2026, 10, 9)

  it('avança ao fim do mês anterior', () => {
    expect(novaDataBase('2025-09-30', hoje)).toBe('2026-10-31')
  })

  it('não há o que atualizar num cálculo que já vai até lá', () => {
    expect(novaDataBase('2026-10-31', hoje)).toBeNull()
    expect(novaDataBase('2026-11-05', hoje)).toBeNull()
  })
})

describe('atualizarRascunho', () => {
  const hoje = new Date(2026, 10, 21)
  const caso = (dataBase: string, ultimoMes: string, comPensao = true): Rascunho => {
    const r = rascunhoVazio()
    return { ...r, dataBase, comPensao, pensao: { ...r.pensao, primeiroMes: '2024-06', ultimoMes } }
  }

  it('estende até o mês da nova data-base a pensão que ainda corria', () => {
    const { rascunho, ultimaParcela } = atualizarRascunho(caso('2026-08-21', '2026-08'), hoje)
    expect(rascunho.dataBase).toBe('2026-10-31')
    expect(rascunho.pensao.ultimoMes).toBe('2026-10')
    expect(ultimaParcela).toEqual({ de: '2026-08', para: '2026-10' })
  })

  it('não mexe na pensão que terminou antes da data-base antiga', () => {
    const { rascunho, ultimaParcela } = atualizarRascunho(caso('2026-08-21', '2026-05'), hoje)
    expect(rascunho.dataBase).toBe('2026-10-31')
    expect(rascunho.pensao.ultimoMes).toBe('2026-05')
    expect(ultimaParcela).toBeNull()
  })

  it('não estende sem pensão, nem quando não há o que atualizar', () => {
    expect(atualizarRascunho(caso('2026-08-21', '2026-08', false), hoje).ultimaParcela).toBeNull()
    const atual = caso('2026-10-31', '2026-10')
    expect(atualizarRascunho(atual, hoje)).toEqual({ rascunho: atual, ultimaParcela: null })
  })

  it('não recua a última parcela que já ia além da nova data-base', () => {
    const { rascunho, ultimaParcela } = atualizarRascunho(caso('2026-08-21', '2027-02'), hoje)
    expect(rascunho.pensao.ultimoMes).toBe('2027-02')
    expect(ultimaParcela).toBeNull()
  })

  it('respeita o fim fixado depois da data-base antiga, mesmo antes da nova', () => {
    const { rascunho, ultimaParcela } = atualizarRascunho(caso('2026-08-21', '2026-09'), hoje)
    expect(rascunho.pensao.ultimoMes).toBe('2026-09')
    expect(ultimaParcela).toBeNull()
  })
})

describe('filtrarCalculos', () => {
  const resumo = (titulo: string, processo: string | null, regime = 'Regime cível padrão do TJMG'): ResumoCalculo => ({
    id: titulo,
    titulo,
    processo,
    dataBase: '2024-10-31',
    regime,
    total: 1000,
    criadoEm: '2024-11-01T12:00:00Z',
  })
  const lista = [
    resumo('Execução da Maria', '5001234-56.2024.8.13.0024'),
    resumo('Pensão do João', null, 'Critérios fixados no título'),
    resumo('Processo 0009876-12.2023.8.13.0702', '0009876-12.2023.8.13.0702'),
  ]
  const titulos = (busca: string) => filtrarCalculos(lista, busca).map((r) => r.titulo)

  it('sem busca devolve todos, na mesma ordem', () => {
    expect(titulos('')).toEqual(lista.map((r) => r.titulo))
    expect(titulos('   ')).toEqual(lista.map((r) => r.titulo))
  })

  it('acha pelo título sem diferença de acento nem de maiúsculas', () => {
    expect(titulos('execucao')).toEqual(['Execução da Maria'])
    expect(titulos('PENSÃO')).toEqual(['Pensão do João'])
  })

  it('exige todas as palavras', () => {
    expect(titulos('maria execução')).toEqual(['Execução da Maria'])
    expect(titulos('maria joão')).toEqual([])
  })

  it('acha pelo regime', () => {
    expect(titulos('fixados')).toEqual(['Pensão do João'])
  })

  it('acha o processo com a máscara do CNJ ou só pelos dígitos', () => {
    expect(titulos('5001234-56.2024')).toEqual(['Execução da Maria'])
    expect(titulos('500123456')).toEqual(['Execução da Maria'])
    expect(titulos('8.13.0702')).toEqual(['Processo 0009876-12.2023.8.13.0702'])
  })

  it('um número que não está em processo nenhum não acha nada', () => {
    expect(titulos('7777')).toEqual([])
  })

  it('um número não procura no regime, que tem datas e leis no nome', () => {
    const comRegimePadrao = [
      resumo('Execução da Maria', '5001234-56.2024.8.13.0024', 'Cível padrão TJMG (tabela CGJ: INPC até 07/2024; Lei 14.905/2024)'),
      resumo('Pensão do João', '0009876-12.2023.8.13.0702', 'Cível padrão TJMG (tabela CGJ: INPC até 07/2024; Lei 14.905/2024)'),
    ]
    const achados = (busca: string) => filtrarCalculos(comRegimePadrao, busca).map((r) => r.titulo)
    expect(achados('2024')).toEqual(['Execução da Maria'])
    expect(achados('14.905')).toEqual([])
    expect(achados('07/2024')).toEqual([])
    // Palavra com letras continua procurando no regime.
    expect(achados('inpc')).toEqual(['Execução da Maria', 'Pensão do João'])
  })
})
