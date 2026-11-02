-- Cálculos salvos, com o snapshot das séries que cada um usou.

-- Um escritório é um tenant (docs/requisitos-mvp.md). Até a autenticação (#7), todo cálculo é
-- do escritório padrão.
CREATE TABLE escritorio (
    id        UUID         PRIMARY KEY,
    nome      VARCHAR(200) NOT NULL,
    criado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO escritorio (id, nome) VALUES ('00000000-0000-0000-0000-000000000001', 'Escritório padrão');

-- pedido: o CalculoRequest como foi calculado (parcelas avulsas, pensão, pagamentos, regime e
-- processo), com o regime já resolvido, para que uma mudança no regime padrão não altere um
-- cálculo salvo. data_base, regime e total repetem o pedido e o resultado para a listagem.
CREATE TABLE calculo (
    id            UUID          PRIMARY KEY,
    escritorio_id UUID          NOT NULL REFERENCES escritorio (id),
    titulo        VARCHAR(200)  NOT NULL,
    data_base     DATE          NOT NULL,
    regime        VARCHAR(200)  NOT NULL,
    total         NUMERIC(19,2) NOT NULL,
    pedido        JSONB         NOT NULL,
    criado_em     TIMESTAMPTZ   NOT NULL
);

CREATE INDEX calculo_escritorio_criado_em ON calculo (escritorio_id, criado_em DESC);

-- Snapshot: o valor de cada série em cada mês que o cálculo usou, como estava no cache.
CREATE TABLE calculo_serie (
    calculo_id UUID        NOT NULL REFERENCES calculo (id) ON DELETE CASCADE,
    serie      VARCHAR(32) NOT NULL,
    mes        DATE        NOT NULL CHECK (EXTRACT(DAY FROM mes) = 1),
    valor      NUMERIC     NOT NULL,
    PRIMARY KEY (calculo_id, serie, mes)
);
