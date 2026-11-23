-- Vários usuários por escritório, com perfis (#5). O admin convida e gerencia os usuários do
-- escritório e vê todos os cálculos dele; o membro vê só os que ele mesmo salvou.

-- o escritório deixa de ter um único usuário
ALTER TABLE usuario DROP CONSTRAINT usuario_escritorio_id_key;
CREATE INDEX usuario_escritorio ON usuario (escritorio_id);

-- quem já existia era o único usuário do escritório, e vira o admin dele
ALTER TABLE usuario ADD COLUMN papel VARCHAR(16) NOT NULL DEFAULT 'MEMBRO'
    CHECK (papel IN ('ADMIN', 'MEMBRO'));
UPDATE usuario SET papel = 'ADMIN';

-- o usuário removido fica desativado, e não apagado, para os cálculos dele continuarem com autor
ALTER TABLE usuario ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;

-- Quem salvou o cálculo. Os já salvos ficam com o único usuário do escritório; os salvos antes
-- de o escritório ter usuário ficam sem autor, e só o admin os vê.
ALTER TABLE calculo ADD COLUMN autor_id UUID REFERENCES usuario (id);
UPDATE calculo c SET autor_id = u.id FROM usuario u WHERE u.escritorio_id = c.escritorio_id;
CREATE INDEX calculo_autor_criado_em ON calculo (autor_id, criado_em DESC);

-- Convite por e-mail. O link leva o token; o banco guarda só o SHA-256 dele, para que quem lê o
-- banco não consiga aceitar o convite. Um convite novo para o mesmo e-mail substitui o pendente.
CREATE TABLE convite (
    id            UUID         PRIMARY KEY,
    escritorio_id UUID         NOT NULL REFERENCES escritorio (id),
    email         VARCHAR(320) NOT NULL,
    papel         VARCHAR(16)  NOT NULL CHECK (papel IN ('ADMIN', 'MEMBRO')),
    token_hash    BYTEA        NOT NULL UNIQUE,
    convidado_por UUID         NOT NULL REFERENCES usuario (id),
    criado_em     TIMESTAMPTZ  NOT NULL,
    expira_em     TIMESTAMPTZ  NOT NULL,
    aceito_em     TIMESTAMPTZ
);

CREATE UNIQUE INDEX convite_pendente ON convite (escritorio_id, lower(email)) WHERE aceito_em IS NULL;
