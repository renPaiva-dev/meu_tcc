package br.com.reciclathe;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Contexto próprio, com limite baixo: nos demais testes o limite é altíssimo (todos vêm do mesmo IP). */
@TestPropertySource(properties = "reciclathe.limites.tentativas-por-minuto=3")
class LimiteTentativasTest extends IntegracaoTest {

    private static RequestPostProcessor ip(String endereco) {
        return requisicao -> {
            requisicao.setRemoteAddr(endereco);
            return requisicao;
        };
    }

    @Test
    void loginDaApiPassaDoLimiteDevolve429() throws Exception {
        String corpo = """
                {"email": "ninguem@teste.local", "senha": "errada"}
                """;
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.1")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.1")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));

        // Outro IP não é afetado
        mvc.perform(post("/api/v1/auth/login").with(ip("10.0.0.2")).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDoPainelPassaDoLimiteVoltaComAviso() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/login").with(ip("10.0.0.3")).with(csrf()).param("username", "x@teste.local").param("password", "x"))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(ip("10.0.0.3")).with(csrf()).param("username", ADMIN).param("password", "admin-teste"))
                .andExpect(redirectedUrl("/login?limite"));
    }
}
