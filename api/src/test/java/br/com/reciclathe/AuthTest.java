package br.com.reciclathe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthTest extends IntegracaoTest {

    @Test
    void cadastroLoginEPerfil() throws Exception {
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Maria da Silva", "email": "Maria@Teste.local", "senha": "segredo123", "zona": "SUL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.email").value("maria@teste.local"));

        String resposta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "maria@teste.local", "senha": "segredo123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.zona").value("SUL"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(resposta, "$.token");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria da Silva"))
                .andExpect(jsonPath("$.pontos").value(0));
    }

    @Test
    void emailDuplicadoESenhaErrada() throws Exception {
        cadastrar("João", null);
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "X", "email": "nao-e-email", "senha": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.email").exists())
                .andExpect(jsonPath("$.campos.senha").exists());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ninguem@teste.local", "senha": "errada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotasProtegidasExigemToken() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/materiais")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/zonas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }
}
