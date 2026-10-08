package br.com.reciclathe.zona;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Zonas e bairros de Teresina. Os polígonos dos bairros não vêm no sistema: são importados
 * pelo administrador a partir de um GeoJSON público (ex.: malha de bairros do IBGE/prefeitura).
 * Enquanto não houver bairros, o app pede que o usuário escolha a zona manualmente.
 */
@Service
public class ZonaService {

    private final ZonaRepository zonas;
    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public ZonaService(ZonaRepository zonas, JdbcClient jdbc, JsonMapper jsonMapper) {
        this.zonas = zonas;
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public List<Zona> listar() {
        return zonas.findAllByOrderByNomeAsc();
    }

    public Zona buscar(String codigo) {
        return zonas.findById(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Zona não encontrada: " + codigo));
    }

    public Optional<Localizacao> localizar(double latitude, double longitude) {
        return jdbc.sql("""
                        SELECT b.nome AS bairro, b.zona_codigo AS zona
                        FROM bairro b
                        WHERE ST_Contains(b.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326))
                        LIMIT 1
                        """)
                .param("lat", latitude)
                .param("lon", longitude)
                .query((rs, i) -> new Localizacao(buscar(rs.getString("zona")), rs.getString("bairro")))
                .optional();
    }

    public Map<String, Long> contarBairrosPorZona() {
        return jdbc.sql("SELECT zona_codigo, count(*) AS total FROM bairro GROUP BY zona_codigo")
                .query((rs, i) -> Map.entry(rs.getString("zona_codigo"), rs.getLong("total")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional
    public void atualizarTelefone(String codigo, String telefone) {
        buscar(codigo).setTelefoneColeta(telefone == null || telefone.isBlank() ? null : telefone.strip());
    }

    /**
     * Importa (ou atualiza) bairros de um FeatureCollection GeoJSON em WGS84.
     *
     * @param propriedadeNome nome da propriedade com o nome do bairro
     * @param propriedadeZona nome da propriedade com a zona (ex.: "Norte", "SUDESTE")
     * @return quantidade de bairros importados
     */
    @Transactional
    public int importarBairros(byte[] geojson, String propriedadeNome, String propriedadeZona) {
        JsonNode raiz;
        try {
            raiz = jsonMapper.readTree(geojson);
        } catch (JacksonException e) {
            throw new RegraNegocioException("Arquivo GeoJSON inválido");
        }
        JsonNode features = raiz.path("features");
        if (!features.isArray() || features.isEmpty()) {
            throw new RegraNegocioException("O GeoJSON deve ser um FeatureCollection com ao menos um bairro");
        }
        Map<String, String> codigosZona = listar().stream()
                .collect(Collectors.toMap(z -> normalizar(z.getCodigo()), Zona::getCodigo));

        int importados = 0;
        for (JsonNode feature : features) {
            String nome = feature.path("properties").path(propriedadeNome).asString("").strip();
            String zonaInformada = feature.path("properties").path(propriedadeZona).asString("");
            String zona = codigosZona.get(normalizar(zonaInformada));
            JsonNode geometria = feature.path("geometry");
            if (nome.isEmpty() || geometria.isMissingNode() || geometria.isNull()) {
                throw new RegraNegocioException("Feição sem nome de bairro ou geometria (propriedade '" + propriedadeNome + "')");
            }
            if (zona == null) {
                throw new RegraNegocioException("Zona desconhecida '" + zonaInformada + "' no bairro " + nome);
            }
            jdbc.sql("""
                            INSERT INTO bairro (nome, zona_codigo, geom)
                            VALUES (:nome, :zona, ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:geom), 4326)))
                            ON CONFLICT (nome) DO UPDATE SET zona_codigo = EXCLUDED.zona_codigo, geom = EXCLUDED.geom
                            """)
                    .param("nome", nome)
                    .param("zona", zona)
                    .param("geom", geometria.toString())
                    .update();
            importados++;
        }
        return importados;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    public record Localizacao(Zona zona, String bairro) {
    }
}
