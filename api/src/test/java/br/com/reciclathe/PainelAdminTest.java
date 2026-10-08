package br.com.reciclathe;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PainelAdminTest extends IntegracaoTest {

    @Test
    void painelExigeAdmin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(user("cidadao").roles("CIDADAO"))).andExpect(status().isForbidden());
    }

    @Test
    void loginDoAdminConfigurado() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "admin@teste.local").param("password", "admin-teste"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void todasAsTelasRenderizam() throws Exception {
        String token = cadastrar("Hugo", "CENTRO");
        Long ponto = criarPonto("PEV Teste", -5.09, -42.80, "VIDRO");
        String resposta = mvc.perform(post("/api/v1/identificacoes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"material\": \"PLASTICO\", \"confianca\": 0.6}"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(resposta, "$.id")).longValue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v1/identificacoes/" + id + "/feedback")
                .param("correto", "false").param("materialCorreto", "VIDRO").header("Authorization", token));

        var admin = user("admin@teste.local").roles("ADMIN");
        mvc.perform(get("/admin").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Painel de Teresina")));
        mvc.perform(get("/admin/pontos").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("PEV Teste")));
        mvc.perform(get("/admin/pontos/novo").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/pontos/" + ponto).with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/materiais").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/materiais/VIDRO").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/zonas").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/erros-modelo").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Vidro")));
    }

    @Test
    void cadastroDePontoPeloPainelValidaCampos() throws Exception {
        var admin = user("admin@teste.local").roles("ADMIN");
        mvc.perform(post("/admin/pontos").with(admin).with(csrf())
                        .param("nome", "").param("tipo", "PEV").param("endereco", "Rua X")
                        .param("latitude", "-5.09").param("longitude", "-42.80"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert-danger")));

        mvc.perform(post("/admin/pontos").with(admin).with(csrf())
                        .param("nome", "PEV novo").param("tipo", "COOPERATIVA").param("endereco", "Rua X")
                        .param("latitude", "-5.09").param("longitude", "-42.80").param("materiais", "VIDRO", "PAPEL"))
                .andExpect(redirectedUrl("/admin/pontos"));
    }
}
