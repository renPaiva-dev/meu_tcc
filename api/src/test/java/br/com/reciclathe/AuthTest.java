package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class AuthTest extends IntegracaoTest {

    @Autowired
    JwtEncoder jwtEncoder;

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

    @Test
    void senhaPrecisaDeOitoCaracteres() throws Exception {
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Ivo", "email": "ivo@teste.local", "senha": "1234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha").exists());
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Ivo", "email": "ivo@teste.local", "senha": "12345678"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void emailDuplicadoDevolve409() throws Exception {
        String corpo = """
                {"nome": "Júlia", "email": "julia@teste.local", "senha": "segredo123"}
                """;
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace("julia@", "JULIA@")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Já existe uma conta com este e-mail"));
    }

    @Test
    void patchMeTrocaAZona() throws Exception {
        String token = cadastrar("Lia", "SUL");
        mvc.perform(patch("/api/v1/me").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zona": "NORTE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona").value("NORTE"));
        mvc.perform(patch("/api/v1/me").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zona": "OESTE"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/me").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zona": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zona").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void tokenDeOutroEmissorEhRecusado() throws Exception {
        Long id = idDoUsuario(cadastrar("Mel", null));
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("outro-sistema").subject(id.toString())
                .issuedAt(agora).expiresAt(agora.plus(1, ChronoUnit.HOURS))
                .build();
        String falso = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioBloqueadoNaoEntraNemUsaTokenAntigo() throws Exception {
        String token = cadastrar("Nina", null);
        Long id = idDoUsuario(token);
        String email = jdbc.sql("SELECT email FROM usuario WHERE id = ?").param(id).query(String.class).single();
        var admin = user(ADMIN).roles("ADMIN");

        mvc.perform(post("/admin/usuarios/" + id + "/bloquear").with(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/usuarios"));

        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "segredo123"}
                                """.formatted(email)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/login").with(csrf()).param("username", email).param("password", "segredo123"))
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(post("/admin/usuarios/" + id + "/desbloquear").with(admin).with(csrf()));
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void adminBloqueadoNaoEntraNoPainel() throws Exception {
        var admin = user(ADMIN).roles("ADMIN");
        mvc.perform(post("/admin/usuarios").with(admin).with(csrf())
                        .param("nome", "Outro Admin").param("email", "outro.admin@teste.local")
                        .param("senha", "senha-forte-1"))
                .andExpect(redirectedUrl("/admin/usuarios"));
        mvc.perform(post("/login").with(csrf())
                        .param("username", "outro.admin@teste.local").param("password", "senha-forte-1"))
                .andExpect(redirectedUrl("/admin"));

        Long id = jdbc.sql("SELECT id FROM usuario WHERE email = 'outro.admin@teste.local'").query(Long.class).single();
        mvc.perform(post("/admin/usuarios/" + id + "/bloquear").with(admin).with(csrf()));
        mvc.perform(post("/login").with(csrf())
                        .param("username", "outro.admin@teste.local").param("password", "senha-forte-1"))
                .andExpect(redirectedUrl("/login?error"));

        // O administrador não bloqueia a si mesmo
        Long proprio = jdbc.sql("SELECT id FROM usuario WHERE email = ?").param(ADMIN).query(Long.class).single();
        mvc.perform(post("/admin/usuarios/" + proprio + "/bloquear").with(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/usuarios"));
        assertThat(jdbc.sql("SELECT ativo FROM usuario WHERE id = ?")
                .param(proprio).query(Boolean.class).single()).isTrue();
    }

    @Test
    void actuatorExpoeSoOHealth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // Os demais endpoints não são expostos: caem no painel, que exige login
        mvc.perform(get("/actuator/env")).andExpect(status().is3xxRedirection());
    }
}
