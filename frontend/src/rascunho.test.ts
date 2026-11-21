import { describe, expect, it } from 'vitest'
import { armazenamentoEmMemoria } from './armazenamentoEmMemoria'
import { pensaoVazia } from './pensao'
import { CHAVE_RASCUNHO, apagarRascunho, ehRascunhoVazio, lerRascunho, salvarRascunho, type Rascunho } from './rascunho'

const vazio: Rascunho = {
  processo: { numero: '', juizo: '', exequente: '', executado: '' },
  dataBase: '2026-10-31',
  aplicarArt523: true,
  comPensao: true,
  separarRitos: true,
  pensao: pensaoVazia(),
  parcelas: [],
  outrasAbertas: false,
  regimeProprio: false,
  nomeRegime: '',
  regras: [],
  tema1368: false,
  regimeTema1368: null,
}

const preenchido: Rascunho = {
  ...vazio,
  processo: { ...vazio.processo, exequente: 'Fulana' },
  pensao: { ...pensaoVazia(), percentual: '30', primeiroMes: '2025-01', ultimoMes: '2026-10' },
  parcelas: [{ descricao: 'Despesa escolar', valor: '350,00', inicioCorrecao: '2025-02-10', inicioJuros: null }],
}

const comoTexto = (rascunho: unknown, versao = 2) => JSON.stringify({ versao, salvoEm: '', aba: 'a', rascunho })

describe('rascunho', () => {
  it('devolve o que foi salvo, com o momento do salvamento e a aba que o salvou', () => {
    const armazenamento = armazenamentoEmMemoria()
    const agora = new Date('2026-11-02T14:30:00Z')
    salvarRascunho(armazenamento, preenchido, 'a', agora)
    expect(lerRascunho(armazenamento)).toEqual({ rascunho: preenchido, salvoEm: agora, aba: 'a' })
  })

  it('descarta, e apaga, rascunho corrompido, de outra versão ou com forma inesperada em qualquer nível', () => {
    const armazenamento = armazenamentoEmMemoria()
    for (const texto of [
      '{',
      'null',
      comoTexto(preenchido, 0),
      comoTexto({ ...preenchido, parcelas: 'x' }),
      comoTexto({ ...preenchido, parcelas: [null] }),
      comoTexto({ ...preenchido, parcelas: [{ descricao: 'x' }] }),
      comoTexto({ ...preenchido, processo: {} }),
      comoTexto({ ...preenchido, pensao: { ...preenchido.pensao, pagamentos: null } }),
      comoTexto({ ...preenchido, pensao: { ...preenchido.pensao, pagamentos: [{ mes: '2025-01' }] } }),
      comoTexto({ ...preenchido, pensao: { pagamentos: [] } }),
      comoTexto({ ...preenchido, regras: [{}] }),
      comoTexto({ ...preenchido, tema1368: 'sim' }),
      comoTexto({ ...preenchido, tema1368: true, regimeTema1368: { nome: 'x', regras: [{}] } }),
    ]) {
      armazenamento.setItem(CHAVE_RASCUNHO, texto)
      expect(lerRascunho(armazenamento)).toBeNull()
      expect(armazenamento.dados.has(CHAVE_RASCUNHO)).toBe(false)
    }
  })

  it('adapta o rascunho da versão 1, que só tinha a pensão em salário mínimo', () => {
    const armazenamento = armazenamentoEmMemoria()
    const { base, rendimentos, valoresFixos, decimoTerceiro, abatimentos, ...pensaoV1 } = preenchido.pensao
    expect([base, rendimentos, decimoTerceiro, abatimentos, valoresFixos]).toEqual(['SALARIO_MINIMO', '', false, [], [{ aPartirDe: '', valor: '' }]])
    armazenamento.setItem(CHAVE_RASCUNHO, comoTexto({ ...preenchido, pensao: pensaoV1 }, 1))
    expect(lerRascunho(armazenamento)?.rascunho).toEqual(preenchido)

    armazenamento.setItem(CHAVE_RASCUNHO, comoTexto({ ...preenchido, pensao: { ...pensaoV1, pagamentos: null } }, 1))
    expect(lerRascunho(armazenamento)).toBeNull()
  })

  it('guarda a escolha do Tema 1368 com o regime que veio do servidor', () => {
    const armazenamento = armazenamentoEmMemoria()
    const regime = {
      nome: 'Tema 1368/STJ (SELIC até 07/2024; Lei 14.905/2024)',
      regras: [
        { inicio: '2003-01', fim: '2024-07', correcao: 'SELIC' as const, juros: 'SEM_JUROS' as const, percentualJurosMensal: null, ultimoDiaTaxaFixa: null },
        { inicio: '2024-08', fim: null, correcao: 'IPCA' as const, juros: 'TAXA_LEGAL' as const, percentualJurosMensal: null, ultimoDiaTaxaFixa: null },
      ],
    }
    const comTema = { ...preenchido, tema1368: true, regimeTema1368: regime }
    salvarRascunho(armazenamento, comTema, 'a')
    expect(lerRascunho(armazenamento)?.rascunho).toEqual(comTema)
  })

  it('abre o rascunho de antes do Tema 1368 com a opção desmarcada', () => {
    const armazenamento = armazenamentoEmMemoria()
    const { tema1368, regimeTema1368, ...anterior } = { ...preenchido, regimeProprio: true, regras: [] }
    expect([tema1368, regimeTema1368]).toEqual([false, null])
    armazenamento.setItem(CHAVE_RASCUNHO, comoTexto(anterior))
    expect(lerRascunho(armazenamento)?.rascunho).toEqual({ ...anterior, tema1368: false, regimeTema1368: null })
  })

  it('segue sem armazenamento, ou com um que lança exceção', () => {
    const quebrado = {
      getItem: () => {
        throw new Error('bloqueado')
      },
      setItem: () => {
        throw new Error('cota')
      },
      removeItem: () => {
        throw new Error('bloqueado')
      },
    }
    expect(lerRascunho(null)).toBeNull()
    expect(lerRascunho(quebrado)).toBeNull()
    expect(() => salvarRascunho(quebrado, preenchido, 'a')).not.toThrow()
    expect(() => apagarRascunho(quebrado, 'a')).not.toThrow()
  })

  it('só a aba que salvou por último apaga o rascunho', () => {
    const armazenamento = armazenamentoEmMemoria()
    salvarRascunho(armazenamento, preenchido, 'b')
    apagarRascunho(armazenamento, 'a')
    expect(lerRascunho(armazenamento)?.aba).toBe('b')
    apagarRascunho(armazenamento, 'b')
    expect(lerRascunho(armazenamento)).toBeNull()
  })

  it('reconhece o formulário em branco sem depender da data-base nem da ordem dos campos', () => {
    const invertido = Object.fromEntries(Object.entries(vazio).reverse()) as unknown as Rascunho
    const pensaoInvertida = Object.fromEntries(Object.entries(vazio.pensao).reverse()) as unknown as Rascunho['pensao']
    expect(ehRascunhoVazio({ ...invertido, dataBase: '2026-11-30', pensao: pensaoInvertida }, vazio)).toBe(true)
    expect(ehRascunhoVazio(preenchido, vazio)).toBe(false)
    expect(ehRascunhoVazio({ ...vazio, parcelas: [preenchido.parcelas[0]] }, vazio)).toBe(false)
  })
})
