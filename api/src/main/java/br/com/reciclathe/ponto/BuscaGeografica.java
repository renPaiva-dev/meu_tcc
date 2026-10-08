package br.com.reciclathe.ponto;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas espaciais (PostGIS) sobre os pontos de coleta. Distâncias em metros. */
@Repository
public class BuscaGeografica {

    private final JdbcClient jdbc;

    public BuscaGeografica(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Pontos aprovados dentro do raio, do mais próximo ao mais distante. */
    public List<Resultado> proximos(double latitude, double longitude, double raioMetros, String material, int limite) {
        return jdbc.sql("""
                        SELECT p.id, ST_Distance(p.localizacao, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) AS distancia
                        FROM ponto_coleta p
                        WHERE p.status = 'APROVADO'
                          AND ST_DWithin(p.localizacao, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :raio)
                          AND (CAST(:material AS VARCHAR) IS NULL OR EXISTS (
                                SELECT 1 FROM ponto_material pm
                                WHERE pm.ponto_id = p.id AND pm.material_codigo = CAST(:material AS VARCHAR)))
                        ORDER BY distancia
                        LIMIT :limite
                        """)
                .param("lat", latitude)
                .param("lon", longitude)
                .param("raio", raioMetros)
                .param("material", material)
                .param("limite", limite)
                .query((rs, i) -> new Resultado(rs.getLong("id"), rs.getDouble("distancia")))
                .list();
    }

    public double distancia(Long pontoId, double latitude, double longitude) {
        return jdbc.sql("""
                        SELECT ST_Distance(localizacao, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
                        FROM ponto_coleta WHERE id = :id
                        """)
                .param("id", pontoId)
                .param("lat", latitude)
                .param("lon", longitude)
                .query(Double.class)
                .single();
    }

    public record Resultado(Long id, double distanciaMetros) {
    }
}
