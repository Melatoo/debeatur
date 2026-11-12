-- Login (#7). No MVP cada escritório tem um único usuário; a restrição UNIQUE em escritorio_id
-- cai quando vierem vários usuários por escritório (#5).
CREATE TABLE usuario (
    id            UUID         PRIMARY KEY,
    escritorio_id UUID         NOT NULL UNIQUE REFERENCES escritorio (id),
    email         VARCHAR(320) NOT NULL,
    -- hash com o prefixo do algoritmo, como o DelegatingPasswordEncoder grava ({bcrypt}...)
    senha         VARCHAR(500) NOT NULL,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- o e-mail identifica o usuário sem diferenciar maiúsculas
CREATE UNIQUE INDEX usuario_email ON usuario (lower(email));
