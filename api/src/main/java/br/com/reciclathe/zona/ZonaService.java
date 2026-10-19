package br.com.reciclathe.zona;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
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

    /** Tamanho da coluna telefone_coleta. */
    public static final int TELEFONE_MAXIMO = 30;

    private static final Set<String> GEOMETRIAS_ACEITAS = Set.of("Polygon", "MultiPolygon");

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

    public List<Bairro> listarBairros() {
        return jdbc.sql("SELECT id, nome, zona_codigo FROM bairro ORDER BY zona_codigo, nome")
                .query((rs, i) -> new Bairro(rs.getLong("id"), rs.getString("nome"), rs.getString("zona_codigo")))
                .list();
    }

    @Transactional
    public void excluirBairro(Long id) {
        if (jdbc.sql("DELETE FROM bairro WHERE id = :id").param("id", id).update() == 0) {
            throw new RecursoNaoEncontradoException("Bairro não encontrado");
        }
    }

    @Transactional
    public void atualizarTelefone(String codigo, String telefone) {
        String valor = telefone == null || telefone.isBlank() ? null : telefone.strip();
        if (valor != null && valor.length() > TELEFONE_MAXIMO) {
            throw new RegraNegocioException("O telefone deve ter no máximo %d caracteres".formatted(TELEFONE_MAXIMO));
        }
        buscar(codigo).setTelefoneColeta(valor);
    }

    /**
     * Importa (ou atualiza) bairros de um FeatureCollection GeoJSON em WGS84. É tudo ou nada: qualquer
     * feição inválida desfaz a importação inteira, com uma mensagem que aponta o bairro problemático.
     * Polígonos com defeitos comuns (auto-interseção, anel mal fechado) são corrigidos com ST_MakeValid.
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
            if (nome.length() > 120) {
                throw new RegraNegocioException("Nome de bairro com mais de 120 caracteres: " + nome.substring(0, 40) + "…");
            }
            if (zona == null) {
                throw new RegraNegocioException("Zona desconhecida '" + zonaInformada + "' no bairro " + nome);
            }
            String tipo = geometria.path("type").asString("");
            if (!GEOMETRIAS_ACEITAS.contains(tipo)) {
                throw new RegraNegocioException("A geometria do bairro " + nome
                        + " deve ser Polygon ou MultiPolygon (veio '" + tipo + "')");
            }
            int gravados;
            try {
                // ST_MakeValid pode devolver uma coleção mista; ST_CollectionExtract(..., 3) fica só com os polígonos
                gravados = jdbc.sql("""
                                WITH g AS (
                                    SELECT ST_Multi(ST_CollectionExtract(
                                               ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(:geom), 4326)), 3)) AS geom)
                                INSERT INTO bairro (nome, zona_codigo, geom)
                                SELECT :nome, :zona, g.geom FROM g WHERE NOT ST_IsEmpty(g.geom)
                                ON CONFLICT (nome) DO UPDATE SET zona_codigo = EXCLUDED.zona_codigo, geom = EXCLUDED.geom
                                """)
                        .param("nome", nome)
                        .param("zona", zona)
                        .param("geom", geometria.toString())
                        .update();
            } catch (DataAccessException e) {
                throw new RegraNegocioException("Geometria inválida no bairro " + nome
                        + ": confira as coordenadas (WGS84, anéis fechados com ao menos 4 posições)");
            }
            if (gravados == 0) {
                throw new RegraNegocioException("A geometria do bairro " + nome + " é vazia ou não forma um polígono");
            }
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

    public record Bairro(Long id, String nome, String zona) {
    }
}
