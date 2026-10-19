package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.reciclathe.config.ReciclatheProperties;
import br.com.reciclathe.ponto.PontoService;

class PontoTest extends IntegracaoTest {

    // Referência: Praça Pedro II, centro de Teresina (aprox.)
    static final double LAT = -5.0892;
    static final double LON = -42.8016;

    @Autowired
    PontoService pontoService;

    @Autowired
    ReciclatheProperties props;

    /** Envia uma sugestão válida e devolve o id do ponto criado. */
    private Long sugerir(String token, MockMultipartFile foto) throws Exception {
        var requisicao = multipart("/api/v1/pontos/sugestoes");
        if (foto != null) {
            requisicao.file(foto);
        }
        String resposta = mvc.perform(requisicao
                        .param("nome", "PEV sugerido").param("tipo", "PEV").param("endereco", "Rua Teste")
                        .param("latitude", "" + LAT).param("longitude", "" + LON).param("materiais", "PAPEL")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(resposta, "$.id")).longValue();
    }

    private Path arquivoDaFoto(Long ponto) {
        String nome = jdbc.sql("SELECT foto_arquivo FROM ponto_coleta WHERE id = ?").param(ponto).query(String.class).single();
        return props.fotos().diretorio().toAbsolutePath().normalize().resolve(nome);
    }

    private org.springframework.test.web.servlet.ResultActions checkin(String token, Long ponto) throws Exception {
        return mvc.perform(post("/api/v1/pontos/" + ponto + "/checkin").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\": %s, \"longitude\": %s}".formatted(LAT, LON)));
    }

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
        MockMultipartFile foto = new MockMultipartFile("foto", "pev.jpg", "image/jpeg", JPEG);

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

        pontoService.aprovar(id, ADMIN);
        pontoService.aprovar(id, ADMIN); // aprovar de novo não pontua de novo

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

    @Test
    void limiteGlobalDeCheckinsPorDia() throws Exception {
        String token = cadastrar("Davi", null);
        int limite = props.limites().checkinsPorDia();
        for (int i = 0; i < limite; i++) {
            Long ponto = criarPonto("PEV " + i, LAT, LON, "PAPEL");
            checkin(token, ponto).andExpect(status().isOk());
        }
        Long excedente = criarPonto("PEV extra", LAT, LON, "PAPEL");
        checkin(token, excedente)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("limite diário")));
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(jsonPath("$.pontos").value(limite * 5));
    }

    @Test
    void limiteNegativoNaoQuebraABusca() throws Exception {
        criarPonto("Perto", LAT, LON, "PAPEL");
        mvc.perform(get("/api/v1/pontos").param("lat", "" + LAT).param("lon", "" + LON)
                        .param("limite", "-5").param("raioKm", "-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/pontos").param("lat", "" + LAT).param("lon", "" + LON).param("limite", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sugestaoRecusadaEDepoisAprovadaPontuaUmaVez() throws Exception {
        String token = cadastrar("Elisa", null);
        Long id = sugerir(token, null);
        var admin = user(ADMIN).roles("ADMIN");

        mvc.perform(post("/admin/pontos/" + id + "/recusar").with(admin).with(csrf()).param("motivo", "Endereço incompleto"));
        assertThat(jdbc.sql("SELECT status FROM ponto_coleta WHERE id = ?").param(id).query(String.class).single())
                .isEqualTo("RECUSADO");

        mvc.perform(post("/admin/pontos/" + id + "/aprovar").with(admin).with(csrf()));
        mvc.perform(post("/admin/pontos/" + id + "/recusar").with(admin).with(csrf()).param("motivo", "Fechou"));
        mvc.perform(post("/admin/pontos/" + id + "/aprovar").with(admin).with(csrf()));

        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(jsonPath("$.pontos").value(20));
        // Auditoria: quem revisou por último
        assertThat(jdbc.sql("""
                        SELECT u.email FROM ponto_coleta p JOIN usuario u ON u.id = p.revisado_por WHERE p.id = ?
                        """).param(id).query(String.class).single()).isEqualTo(ADMIN);
    }

    @Test
    void recusaComMotivoLongoMostraErroEExclusaoApagaAFoto() throws Exception {
        String token = cadastrar("Fábio", null);
        Long id = sugerir(token, new MockMultipartFile("foto", "pev.jpg", "image/jpeg", JPEG));
        Path foto = arquivoDaFoto(id);
        assertThat(foto).exists();
        var admin = user(ADMIN).roles("ADMIN");

        mvc.perform(post("/admin/pontos/" + id + "/recusar").with(admin).with(csrf()).param("motivo", "x".repeat(300)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", containsString("255")));
        assertThat(jdbc.sql("SELECT status FROM ponto_coleta WHERE id = ?").param(id).query(String.class).single())
                .isEqualTo("PENDENTE");

        mvc.perform(post("/admin/pontos/" + id + "/recusar").with(admin).with(csrf()).param("motivo", "Duplicado"))
                .andExpect(flash().attribute("mensagem", "Ponto recusado."));
        mvc.perform(post("/admin/pontos/" + id + "/excluir").with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.sql("SELECT count(*) FROM ponto_coleta WHERE id = ?").param(id).query(Long.class).single()).isZero();
        assertThat(Files.exists(foto)).isFalse();
    }

    @Test
    void fotoComContentTypeFalsoEhRecusada() throws Exception {
        String token = cadastrar("Gil", null);
        MockMultipartFile falsa = new MockMultipartFile("foto", "foto.jpg", "image/jpeg",
                "<?php echo 'não sou uma imagem'; ?>".getBytes());
        mvc.perform(multipart("/api/v1/pontos/sugestoes").file(falsa)
                        .param("nome", "PEV").param("tipo", "PEV").param("endereco", "Rua")
                        .param("latitude", "" + LAT).param("longitude", "" + LON).param("materiais", "PAPEL")
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("JPEG, PNG ou WebP")));
        assertThat(jdbc.sql("SELECT count(*) FROM ponto_coleta").query(Long.class).single()).isZero();
    }

    @Test
    void limiteDeSugestoesPendentesPorUsuario() throws Exception {
        String token = cadastrar("Hana", null);
        for (int i = 0; i < props.limites().sugestoesPendentes(); i++) {
            sugerir(token, null);
        }
        mvc.perform(multipart("/api/v1/pontos/sugestoes")
                        .param("nome", "Mais um").param("tipo", "PEV").param("endereco", "Rua")
                        .param("latitude", "" + LAT).param("longitude", "" + LON).param("materiais", "PAPEL")
                        .header("Authorization", token))
                .andExpect(status().isUnprocessableContent());
    }
}
