-- Pontos importados do OpenStreetMap guardam o elemento de origem (node/way/relation + id),
-- para que uma nova importação atualize o ponto em vez de duplicá-lo.
ALTER TABLE ponto_coleta ADD COLUMN osm_tipo VARCHAR(10);
ALTER TABLE ponto_coleta ADD COLUMN osm_id BIGINT;
ALTER TABLE ponto_coleta ADD CONSTRAINT uq_ponto_osm UNIQUE (osm_tipo, osm_id);
ALTER TABLE ponto_coleta ADD CONSTRAINT ck_ponto_osm
    CHECK ((osm_tipo IS NULL) = (osm_id IS NULL));
