package br.com.reciclathe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Base dos testes de integração: contexto completo + PostGIS em container. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class IntegracaoTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void limparBanco() {
        jdbc.sql("DELETE FROM evento_pontuacao").update();
        jdbc.sql("DELETE FROM identificacao").update();
        jdbc.sql("DELETE FROM ponto_coleta").update();
        jdbc.sql("DELETE FROM bairro").update();
        jdbc.sql("DELETE FROM usuario WHERE perfil = 'CIDADAO'").update();
    }

    /** Cadastra um cidadão novo e devolve o cabeçalho Authorization pronto. */
    protected String cadastrar(String nome, String zona) throws Exception {
        String email = UUID.randomUUID() + "@teste.local";
        String corpo = """
                {"nome": "%s", "email": "%s", "senha": "segredo123", "zona": %s}
                """.formatted(nome, email, zona == null ? "null" : "\"" + zona + "\"");
        String resposta = mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(resposta, "$.token");
    }

    protected Long idDoUsuario(String token) throws Exception {
        String resposta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    /** Insere um ponto já aprovado diretamente no banco. */
    protected Long criarPonto(String nome, double lat, double lon, String... materiais) {
        Long id = jdbc.sql("""
                        INSERT INTO ponto_coleta (nome, tipo, endereco, latitude, longitude, status)
                        VALUES (:nome, 'PEV', 'Endereço de teste', :lat, :lon, 'APROVADO') RETURNING id
                        """)
                .param("nome", nome).param("lat", lat).param("lon", lon)
                .query(Long.class).single();
        for (String m : materiais) {
            jdbc.sql("INSERT INTO ponto_material (ponto_id, material_codigo) VALUES (?, ?)").params(id, m).update();
        }
        return id;
    }
}
