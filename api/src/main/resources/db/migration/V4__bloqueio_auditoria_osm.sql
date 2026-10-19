-- Usuários bloqueados pelo administrador não entram no app nem no painel
ALTER TABLE usuario ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;

-- E-mail único sem diferenciar maiúsculas (a aplicação já grava em minúsculas; o índice cobre a corrida
-- entre dois cadastros simultâneos com o mesmo e-mail)
CREATE UNIQUE INDEX uq_usuario_email_minusculo ON usuario (lower(email));

-- Auditoria da revisão: quem aprovou ou recusou o ponto, e quando
ALTER TABLE ponto_coleta ADD COLUMN revisado_por BIGINT REFERENCES usuario (id) ON DELETE SET NULL;
ALTER TABLE ponto_coleta ADD COLUMN revisado_em TIMESTAMPTZ;
CREATE INDEX idx_ponto_sugerido_por ON ponto_coleta (sugerido_por);

-- Elementos do OpenStreetMap cujo ponto foi excluído pelo administrador: a reimportação não os recria
CREATE TABLE osm_ignorado (
    osm_tipo    VARCHAR(10) NOT NULL,
    osm_id      BIGINT      NOT NULL,
    ignorado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (osm_tipo, osm_id)
);
