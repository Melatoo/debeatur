import { describe, expect, it } from 'vitest'
import { lerMes, lerPagamentosColados } from './pagamentosColados'

const semLimites = { primeiroMes: '', ultimoMes: '', jaInformados: [] }

describe('lerMes', () => {
  it.each([
    ['04/2024', '2024-04'],
    ['4/2024', '2024-04'],
    ['10/04/2024', '2024-04'],
    ['2024-04', '2024-04'],
    ['2024-04-10', '2024-04'],
    ['abr/24', '2024-04'],
    ['Abr/2024', '2024-04'],
    ['abr-24', '2024-04'],
    ['abr. 2024', '2024-04'],
    ['abril de 2024', '2024-04'],
    ['março/2025', '2025-03'],
    ['marco/2025', '2025-03'],
    ['dez/25', '2025-12'],
  ])('lê %j como %s', (texto, esperado) => {
    expect(lerMes(texto)).toBe(esperado)
  })

  it.each(['04/24', '13/2024', '00/2024', '32/04/2024', 'abrigo/2024', 'abr', '2024', '', 'R$ 350,00'])(
    'recusa %j',
    (texto) => {
      expect(lerMes(texto)).toBeNull()
    },
  )
})

describe('lerPagamentosColados', () => {
  it('lê mês e valor separados por tabulação, como sai do Excel', () => {
    const colado = '04/2024\t350,50\r\n05/2024\tR$ 1.200,00\r\n'
    expect(lerPagamentosColados(colado, semLimites)).toEqual({
      ok: true,
      pagamentos: [
        { mes: '2024-04', valor: '350,50' },
        { mes: '2024-05', valor: '1200,00' },
      ],
    })
  })

  it('aceita ponto e vírgula, pula o cabeçalho, as linhas em branco e as colunas vazias nas pontas', () => {
    const colado = 'Mês\tValor pago\n\n\tabr/24\t200\t\n mai/24 ; 150,5 \n'
    expect(lerPagamentosColados(colado, semLimites)).toEqual({
      ok: true,
      pagamentos: [
        { mes: '2024-04', valor: '200' },
        { mes: '2024-05', valor: '150,5' },
      ],
    })
  })

  it('não entra nada se uma linha não se lê, e diz o que há em cada uma', () => {
    const colado = ['04/2024\t350,00', '13/2024\t100,00', '06/2024\t1.500', '07/2024\t10,999', '08/2024\t0', '09/2024'].join('\n')
    expect(lerPagamentosColados(colado, semLimites)).toEqual({
      ok: false,
      erros: [
        'Linha 2: "13/2024" não é um mês (ex.: 04/2024 ou abr/2024).',
        'Linha 3: em "1.500" o ponto é ambíguo; escreva com os centavos (ex.: 1.500,00).',
        'Linha 4: "10,999" tem mais de duas casas decimais.',
        'Linha 5: o valor pago deve ser maior que zero.',
        'Linha 6: esperava duas colunas, o mês e o valor; vieram 1.',
      ],
    })
  })

  it('só trata como cabeçalho a primeira linha', () => {
    expect(lerPagamentosColados('04/2024\t350,00\nTotal\tx', semLimites)).toEqual({
      ok: false,
      erros: ['Linha 2: "Total" não é um mês (ex.: 04/2024 ou abr/2024).'],
    })
  })

  it('recusa mês fora do período da pensão', () => {
    const limites = { primeiroMes: '2024-04', ultimoMes: '2024-12', jaInformados: [] }
    expect(lerPagamentosColados('03/2024\t10,00\n01/2025\t10,00', limites)).toEqual({
      ok: false,
      erros: ['Linha 1: 03/2024 está fora do período da pensão.', 'Linha 2: 01/2025 está fora do período da pensão.'],
    })
  })

  it('recusa mês repetido, na colagem ou já no formulário, porque o cálculo aceita um pagamento por mês', () => {
    const limites = { primeiroMes: '', ultimoMes: '', jaInformados: ['2024-04', ''] }
    expect(lerPagamentosColados('04/2024\t10,00\n05/2024\t10,00\nmai/2024\t5,00', limites)).toEqual({
      ok: false,
      erros: [
        'Linha 1: 04/2024 já tem pagamento no formulário.',
        'Linha 3: 05/2024 repete a linha 2; some os valores do mês numa linha só.',
      ],
    })
  })

  it('avisa quando não há pagamento no texto', () => {
    expect(lerPagamentosColados('Mês\tValor\n\n', semLimites)).toEqual({
      ok: false,
      erros: ['Nenhum pagamento encontrado no texto colado.'],
    })
  })
})
