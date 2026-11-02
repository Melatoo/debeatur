-- Cache local das séries do SGS: o cálculo lê daqui, e não do BCB a cada pedido.

-- Valor de cada mês, como o SGS publicou na última consulta. A escala do NUMERIC fica em
-- aberto para guardar o valor exatamente como veio (ex.: 0.56, 1412.00).
CREATE TABLE serie_mensal (
    serie         VARCHAR(32)  NOT NULL,
    mes           DATE         NOT NULL CHECK (EXTRACT(DAY FROM mes) = 1),
    valor         NUMERIC      NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (serie, mes)
);

-- Intervalo já consultado no SGS para cada série. Um mês dentro do intervalo e sem valor em
-- serie_mensal não foi publicado (ainda, ou nunca, como a taxa legal antes de 08/2024).
CREATE TABLE serie_consulta (
    serie         VARCHAR(32)  PRIMARY KEY,
    inicio        DATE         NOT NULL CHECK (EXTRACT(DAY FROM inicio) = 1),
    fim           DATE         NOT NULL CHECK (EXTRACT(DAY FROM fim) = 1),
    consultado_em TIMESTAMPTZ  NOT NULL,
    CHECK (inicio <= fim)
);
