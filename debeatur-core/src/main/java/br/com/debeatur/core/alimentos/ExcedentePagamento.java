package br.com.debeatur.core.alimentos;

/**
 * O que fazer com o pagamento maior que a parcela do mês. As fontes divergem (docs/gabarito.md,
 * divergência 10), então a escolha é do cálculo.
 */
public enum ExcedentePagamento {

    /**
     * Recusa o pagamento e orienta lançar o excedente como {@link Abatimento}, que sai do débito
     * atualizado pelo valor nominal. É o padrão dos cálculos salvos antes da opção.
     */
    RECUSAR,

    /**
     * Como a planilha da DPMG: o saldo do mês fica negativo, e o excedente vira um crédito
     * corrigido e com juros desde o próprio mês, pelos mesmos índices da parcela, que abate o
     * débito das demais. Não vai para outras parcelas nem segue a imputação do art. 354 do CC.
     */
    CREDITO_NO_MES
}
