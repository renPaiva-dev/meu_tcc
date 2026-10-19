package br.com.reciclathe;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
        mvc.perform(get("/admin/erros-modelo").param("filtro", "ACERTOS").param("comFoto", "true").with(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/erros-modelo").param("filtro", "TODOS").param("pagina", "3").with(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/pontos").param("status", "PENDENTE").param("pagina", "-1").with(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hugo")));
        mvc.perform(get("/admin/conta").with(admin)).andExpect(status().isOk());
    }

    @Test
    void erroNoPainelEhPaginaHtml() throws Exception {
        var admin = user(ADMIN).roles("ADMIN");
        mvc.perform(get("/admin/pontos/999999").with(admin))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Ponto não encontrado")));
        // A API continua respondendo problem+json
        mvc.perform(get("/api/v1/pontos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void formularioDoPontoMostraQuemSugeriuERevisao() throws Exception {
        String token = cadastrar("Kátia Sugestora", null);
        String resposta = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/pontos/sugestoes")
                        .param("nome", "PEV da Kátia").param("tipo", "PEV").param("endereco", "Rua")
                        .param("latitude", "-5.09").param("longitude", "-42.80").param("materiais", "PAPEL")
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(resposta, "$.id")).longValue();
        var admin = user(ADMIN).roles("ADMIN");
        mvc.perform(post("/admin/pontos/" + id + "/recusar").with(admin).with(csrf()).param("motivo", "Sem placa no local"));

        mvc.perform(get("/admin/pontos/" + id).with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kátia Sugestora")))
                .andExpect(content().string(containsString("Sem placa no local")))
                .andExpect(content().string(containsString("Aprovar e publicar")));
    }

    @Test
    void trocaDeSenhaDoAdmin() throws Exception {
        var admin = user(ADMIN).roles("ADMIN");
        mvc.perform(post("/admin/conta/senha").with(admin).with(csrf())
                        .param("senhaAtual", "admin-teste").param("novaSenha", "curta").param("confirmacao", "curta"))
                .andExpect(flash().attribute("erro", containsString("8 caracteres")));
        mvc.perform(post("/admin/conta/senha").with(admin).with(csrf())
                        .param("senhaAtual", "errada").param("novaSenha", "nova-senha-123").param("confirmacao", "nova-senha-123"))
                .andExpect(flash().attribute("erro", containsString("senha atual")));
        try {
            mvc.perform(post("/admin/conta/senha").with(admin).with(csrf())
                            .param("senhaAtual", "admin-teste").param("novaSenha", "nova-senha-123")
                            .param("confirmacao", "nova-senha-123"))
                    .andExpect(flash().attribute("mensagem", "Senha alterada."));
            mvc.perform(post("/login").with(csrf()).param("username", ADMIN).param("password", "nova-senha-123"))
                    .andExpect(redirectedUrl("/admin"));
        } finally {
            // Devolve a senha original: o contexto (e o banco) é compartilhado entre as classes de teste
            mvc.perform(post("/admin/conta/senha").with(admin).with(csrf())
                    .param("senhaAtual", "nova-senha-123").param("novaSenha", "admin-teste").param("confirmacao", "admin-teste"));
        }
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
