package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.reciclathe.ponto.PontoService;

class PontoTest extends IntegracaoTest {

    // Referência: Praça Pedro II, centro de Teresina (aprox.)
    static final double LAT = -5.0892;
    static final double LON = -42.8016;

    @Autowired
    PontoService pontoService;

    @Test
    void buscaPorProximidadeOrdenaEFiltraPorMaterial() throws Exception {
        criarPonto("Longe", LAT - 0.05, LON, "VIDRO", "PAPEL");      // ~5,5 km
        criarPonto("Perto", LAT - 0.005, LON, "PAPEL");              // ~550 m
        criarPonto("Fora do raio", LAT - 0.2, LON, "VIDRO");         // ~22 km

        mvc.perform(get("/api/v1/pontos").param("lat", "" + LAT).param("lon", "" + LON).param("raioKm", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Perto"))
                .andExpect(jsonPath("$[1].nome").value("Longe"));

        mvc.perform(get("/api/v1/pontos").param("lat", "" + LAT).param("lon", "" + LON).param("material", "VIDRO"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Longe"))
                .andExpect(jsonPath("$[0].distanciaMetros").value(org.hamcrest.Matchers.closeTo(5530, 50)));

        // Sem coordenadas: lista completa para o cache offline
        mvc.perform(get("/api/v1/pontos")).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void sugestaoSoApareceDepoisDeAprovadaEPontua() throws Exception {
        String token = cadastrar("Ana Souza", "CENTRO");
        MockMultipartFile foto = new MockMultipartFile("foto", "pev.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String resposta = mvc.perform(multipart("/api/v1/pontos/sugestoes").file(foto)
                        .param("nome", "PEV do supermercado")
                        .param("tipo", "PEV")
                        .param("endereco", "Av. Frei Serafim")
                        .param("latitude", "" + LAT)
                        .param("longitude", "" + LON)
                        .param("materiais", "PLASTICO", "METAL")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(resposta, "$.id")).longValue();

        mvc.perform(get("/api/v1/pontos")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/pontos/" + id)).andExpect(status().isNotFound());

        pontoService.aprovar(id);
        pontoService.aprovar(id); // aprovar de novo não pontua de novo

        mvc.perform(get("/api/v1/pontos")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(jsonPath("$.pontos").value(20));
    }

    @Test
    void sugestaoForaDeTeresinaEhRecusada() throws Exception {
        String token = cadastrar("Ana", null);
        mvc.perform(multipart("/api/v1/pontos/sugestoes")
                        .param("nome", "Ponto em São Paulo").param("tipo", "PEV").param("endereco", "SP")
                        .param("latitude", "-23.55").param("longitude", "-46.63").param("materiais", "PAPEL")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.latitude").exists());
    }

    @Test
    void checkinExigeProximidadeEUmPorDia() throws Exception {
        String token = cadastrar("Carlos Lima", "NORTE");
        Long ponto = criarPonto("PEV", LAT, LON, "VIDRO");

        mvc.perform(post("/api/v1/pontos/" + ponto + "/checkin").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT - 0.01, LON)))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(post("/api/v1/pontos/" + ponto + "/checkin").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT + 0.0005, LON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pontosGanhos").value(5));

        mvc.perform(post("/api/v1/pontos/" + ponto + "/checkin").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT, LON)))
                .andExpect(status().isUnprocessableContent());

        assertThat(jdbc.sql("SELECT count(*) FROM evento_pontuacao").query(Long.class).single()).isEqualTo(1);
    }
}
