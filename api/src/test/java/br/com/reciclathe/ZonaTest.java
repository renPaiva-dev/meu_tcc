package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
}
