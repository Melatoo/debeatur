import { describe, expect, it } from 'vitest'
import type { CalculoRequest, Regime, ResumoCalculo } from './api'
import { paraPedido } from './caso'
import { filtrarCalculos, novaDataBase, paraRascunho, regimeSalvo, tituloPadrao } from './calculoSalvo'
import { chaveDoPedido } from './recalculo'

const padrao: Regime = {
  nome: 'Regime cível padrão do TJMG',
  regras: [
    { inicio: '1964-01', fim: '2024-07', correcao: 'INPC', juros: 'TAXA_FIXA', percentualJurosMensal: 1, ultimoDiaTaxaFixa: null },
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
})
