CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE zona (
    codigo          VARCHAR(20) PRIMARY KEY,
    nome            VARCHAR(60) NOT NULL,
    telefone_coleta VARCHAR(30),
    fonte           TEXT
);

CREATE TABLE bairro (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL UNIQUE,
    zona_codigo VARCHAR(20)  NOT NULL REFERENCES zona (codigo),
    geom        geometry(MultiPolygon, 4326) NOT NULL
);
CREATE INDEX idx_bairro_geom ON bairro USING GIST (geom);

CREATE TABLE material (
    codigo         VARCHAR(20) PRIMARY KEY,
    nome           VARCHAR(60) NOT NULL,
    cor_hex        VARCHAR(7)  NOT NULL,
    cor_nome       VARCHAR(30) NOT NULL,
    reciclavel     BOOLEAN     NOT NULL,
    ordem          INT         NOT NULL,
    como_preparar  TEXT        NOT NULL,
    onde_descartar TEXT        NOT NULL,
    observacoes    TEXT,
    fonte          TEXT        NOT NULL,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL UNIQUE,
    senha_hash  VARCHAR(255) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL,
    zona_codigo VARCHAR(20) REFERENCES zona (codigo),
    criado_em   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ponto_coleta (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(160)     NOT NULL,
    tipo          VARCHAR(30)      NOT NULL,
    endereco      VARCHAR(255)     NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    -- Coluna derivada usada nas consultas de proximidade; a aplicação só grava latitude/longitude
    localizacao   geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    horario       VARCHAR(255),
    telefone      VARCHAR(30),
    fonte         TEXT,
    status        VARCHAR(20)      NOT NULL,
    motivo_recusa VARCHAR(255),
    foto_arquivo  VARCHAR(255),
    sugerido_por  BIGINT REFERENCES usuario (id) ON DELETE SET NULL,
    criado_em     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_ponto_localizacao ON ponto_coleta USING GIST (localizacao);
CREATE INDEX idx_ponto_status ON ponto_coleta (status);

CREATE TABLE ponto_material (
    ponto_id        BIGINT      NOT NULL REFERENCES ponto_coleta (id) ON DELETE CASCADE,
    material_codigo VARCHAR(20) NOT NULL REFERENCES material (codigo),
    PRIMARY KEY (ponto_id, material_codigo)
);

-- Usuário é anulado (e não apagado) na exclusão de conta: os dados agregados do painel continuam válidos
CREATE TABLE identificacao (
    id                 BIGSERIAL PRIMARY KEY,
    usuario_id         BIGINT REFERENCES usuario (id) ON DELETE SET NULL,
    material_previsto  VARCHAR(20) NOT NULL REFERENCES material (codigo),
    confianca          REAL        NOT NULL,
    zona_codigo        VARCHAR(20) REFERENCES zona (codigo),
    versao_modelo      VARCHAR(40),
    feedback_correto   BOOLEAN,
    material_corrigido VARCHAR(20) REFERENCES material (codigo),
    foto_arquivo       VARCHAR(255),
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT now(),
    feedback_em        TIMESTAMPTZ
);
CREATE INDEX idx_identificacao_usuario ON identificacao (usuario_id);
CREATE INDEX idx_identificacao_criado_em ON identificacao (criado_em);

CREATE TABLE evento_pontuacao (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo          VARCHAR(30) NOT NULL,
    pontos        INT         NOT NULL,
    referencia_id BIGINT,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_evento_usuario ON evento_pontuacao (usuario_id, tipo, criado_em);
