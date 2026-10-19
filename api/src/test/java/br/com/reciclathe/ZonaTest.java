package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.zona.ZonaService;

class ZonaTest extends IntegracaoTest {

    @Autowired
    ZonaService zonas;

    // Quadrado de teste (não é um bairro real) em torno do centro de Teresina
    static final String GEOJSON = """
            {"type": "FeatureCollection", "features": [{
              "type": "Feature",
              "properties": {"NM_BAIRRO": "Bairro Teste", "ZONA": "Sudeste"},
              "geometry": {"type": "Polygon", "coordinates": [[
                [-42.81, -5.10], [-42.79, -5.10], [-42.79, -5.08], [-42.81, -5.08], [-42.81, -5.10]
              ]]}
            }]}
            """;

    @Test
    void importaBairrosELocalizaZona() throws Exception {
        int total = zonas.importarBairros(GEOJSON.getBytes(StandardCharsets.UTF_8), "NM_BAIRRO", "ZONA");
        assertThat(total).isEqualTo(1);
        // Reimportar atualiza em vez de duplicar
        zonas.importarBairros(GEOJSON.getBytes(StandardCharsets.UTF_8), "NM_BAIRRO", "ZONA");
        assertThat(zonas.contarBairrosPorZona()).containsEntry("SUDESTE", 1L);

        mvc.perform(get("/api/v1/zonas/localizar").param("lat", "-5.09").param("lon", "-42.80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona.codigo").value("SUDESTE"))
                .andExpect(jsonPath("$.bairro").value("Bairro Teste"));

        mvc.perform(get("/api/v1/zonas/localizar").param("lat", "-5.20").param("lon", "-42.80"))
                .andExpect(status().isNotFound());
    }

    @Test
    void zonaDesconhecidaNoGeoJsonEhRejeitada() {
        String invalido = GEOJSON.replace("Sudeste", "Zona Oeste");
        assertThatThrownBy(() -> zonas.importarBairros(invalido.getBytes(StandardCharsets.UTF_8), "NM_BAIRRO", "ZONA"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Zona Oeste");
    }

    /** FeatureCollection com um único bairro (zona Sul) e a geometria informada. */
    private static byte[] umBairro(String geometria) {
        return """
                {"type": "FeatureCollection", "features": [{"type": "Feature",
                  "properties": {"NM_BAIRRO": "Bairro Teste", "ZONA": "Sul"}, "geometry": %s}]}
                """.formatted(geometria).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void geometriaQueNaoEhPoligonoTemMensagemAmigavel() {
        byte[] ponto = umBairro("""
                {"type": "Point", "coordinates": [-42.80, -5.09]}""");
        assertThatThrownBy(() -> zonas.importarBairros(ponto, "NM_BAIRRO", "ZONA"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Polygon ou MultiPolygon");
    }

    @Test
    void poligonoMalformadoTemMensagemAmigavelEDesfazAImportacao() {
        // Segundo bairro com anel de só duas posições: a importação inteira é desfeita
        String doisBairros = """
                {"type": "FeatureCollection", "features": [
                  {"type": "Feature", "properties": {"NM_BAIRRO": "Bom", "ZONA": "Sul"},
                   "geometry": {"type": "Polygon", "coordinates": [[[-42.81, -5.10], [-42.79, -5.10], [-42.79, -5.08], [-42.81, -5.10]]]}},
                  {"type": "Feature", "properties": {"NM_BAIRRO": "Ruim", "ZONA": "Sul"},
                   "geometry": {"type": "Polygon", "coordinates": [[[-42.81, -5.10], [-42.79, -5.10]]]}}
                ]}
                """;
        assertThatThrownBy(() -> zonas.importarBairros(doisBairros.getBytes(StandardCharsets.UTF_8), "NM_BAIRRO", "ZONA"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Ruim");
        assertThat(zonas.listarBairros()).isEmpty();
    }

    @Test
    void poligonoComAutoIntersecaoEhCorrigido() {
        // "Gravata borboleta": inválido para o PostGIS, corrigido por ST_MakeValid
        byte[] borboleta = umBairro("""
                {"type": "Polygon", "coordinates": [[[-42.81, -5.10], [-42.79, -5.08], [-42.79, -5.10], [-42.81, -5.08], [-42.81, -5.10]]]}""");
        assertThat(zonas.importarBairros(borboleta, "NM_BAIRRO", "ZONA")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT ST_IsValid(geom) FROM bairro").query(Boolean.class).single()).isTrue();
    }

    @Test
    void painelMostraErroDeGeoJsonEExcluiBairro() throws Exception {
        var admin = user(ADMIN).roles("ADMIN");
        MockMultipartFile invalido = new MockMultipartFile("arquivo", "bairros.geojson", "application/geo+json",
                "{\"type\": \"FeatureCollection\", \"features\": [{\"type\": \"Feature\", \"properties\": {\"nome\": \"X\", \"zona\": \"Sul\"}, \"geometry\": {\"type\": \"LineString\", \"coordinates\": [[-42.8, -5.1], [-42.7, -5.0]]}}]}"
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/zonas/importar").file(invalido).with(admin).with(csrf())
                        .param("propriedadeNome", "nome").param("propriedadeZona", "zona"))
                .andExpect(redirectedUrl("/admin/zonas"))
                .andExpect(flash().attribute("erro", containsString("Polygon")));

        zonas.importarBairros(GEOJSON.getBytes(StandardCharsets.UTF_8), "NM_BAIRRO", "ZONA");
        Long id = zonas.listarBairros().get(0).id();
        mvc.perform(get("/admin/zonas").with(admin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("Bairro Teste")));
        mvc.perform(post("/admin/zonas/bairros/" + id + "/excluir").with(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/zonas"));
        assertThat(zonas.listarBairros()).isEmpty();
    }

    @Test
    void telefoneLongoMostraErroNaTela() throws Exception {
        mvc.perform(post("/admin/zonas/SUL").with(user(ADMIN).roles("ADMIN")).with(csrf())
                        .param("telefone", "9".repeat(31)))
                .andExpect(redirectedUrl("/admin/zonas"))
                .andExpect(flash().attribute("erro", containsString("30")));
    }
}
