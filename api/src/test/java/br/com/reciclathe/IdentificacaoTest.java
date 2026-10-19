package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.reciclathe.config.ReciclatheProperties;
import br.com.reciclathe.pontuacao.PontuacaoService;

class IdentificacaoTest extends IntegracaoTest {

    @Autowired
    ReciclatheProperties props;

    private Long identificar(String token, String material) throws Exception {
        String resposta = mvc.perform(post("/api/v1/identificacoes").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"material": "%s", "confianca": 0.87, "versaoModelo": "mobilenetv3-v1"}
                                """.formatted(material)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    @Test
    void identificacaoUsaZonaDoUsuarioQuandoNaoInformada() throws Exception {
        String token = cadastrar("Bia", "LESTE");
        identificar(token, "VIDRO");
        mvc.perform(get("/api/v1/me/historico").header("Authorization", token))
                .andExpect(jsonPath("$.identificacoes[0].material").value("VIDRO"))
                .andExpect(jsonPath("$.identificacoes[0].zona").value("LESTE"));
    }

    @Test
    void feedbackSoGuardaFotoComConsentimento() throws Exception {
        String token = cadastrar("Bia", "LESTE");
        MockMultipartFile foto = new MockMultipartFile("foto", "item.jpg", "image/jpeg", JPEG);

        Long semConsentimento = identificar(token, "PLASTICO");
        mvc.perform(multipart("/api/v1/identificacoes/" + semConsentimento + "/feedback").file(foto)
                        .param("correto", "false").param("materialCorreto", "METAL")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pontosGanhos").value(2));

        Long comConsentimento = identificar(token, "PLASTICO");
        mvc.perform(multipart("/api/v1/identificacoes/" + comConsentimento + "/feedback").file(foto)
                        .param("correto", "false").param("materialCorreto", "VIDRO")
                        .param("consentimentoFoto", "true")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT foto_arquivo FROM identificacao WHERE id = ?").param(semConsentimento)
                .query(String.class).optional()).isEmpty();
        assertThat(jdbc.sql("SELECT foto_arquivo FROM identificacao WHERE id = ?").param(comConsentimento)
                .query(String.class).single()).endsWith(".jpg");

        // Feedback repetido é recusado
        mvc.perform(multipart("/api/v1/identificacoes/" + comConsentimento + "/feedback")
                        .param("correto", "true").header("Authorization", token))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void feedbackTemLimiteDiarioDePontos() throws Exception {
        String token = cadastrar("Duda", null);
        int total = 0;
        for (int i = 0; i < PontuacaoService.LIMITE_FEEDBACK_DIARIO + 2; i++) {
            Long id = identificar(token, "PAPEL");
            String resposta = mvc.perform(multipart("/api/v1/identificacoes/" + id + "/feedback")
                            .param("correto", "true").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            total += (Integer) JsonPath.read(resposta, "$.pontosGanhos");
        }
        assertThat(total).isEqualTo(PontuacaoService.LIMITE_FEEDBACK_DIARIO * 2);
    }

    @Test
    void naoPodeDarFeedbackNaIdentificacaoDeOutroUsuario() throws Exception {
        String dono = cadastrar("Dono", null);
        String outro = cadastrar("Outro", null);
        Long id = identificar(dono, "METAL");
        mvc.perform(multipart("/api/v1/identificacoes/" + id + "/feedback")
                        .param("correto", "true").header("Authorization", outro))
                .andExpect(status().isNotFound());
    }

    @Test
    void exclusaoDeContaAnonimizaIdentificacoes() throws Exception {
        String token = cadastrar("Eva", "SUL");
        Long id = identificar(token, "METAL");

        mvc.perform(delete("/api/v1/me").header("Authorization", token)).andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT usuario_id FROM identificacao WHERE id = ?").param(id)
                .query(Long.class).optional()).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM identificacao").query(Long.class).single()).isEqualTo(1);
        // O token da conta excluída deixa de valer na hora
        mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void rankingPorZonaMostraNomeAbreviado() throws Exception {
        String sul = cadastrar("Fernanda Oliveira Santos", "SUL");
        String norte = cadastrar("Gabriel", "NORTE");
        for (String token : new String[]{sul, sul, norte}) {
            Long id = identificar(token, "PAPEL");
            mvc.perform(multipart("/api/v1/identificacoes/" + id + "/feedback")
                    .param("correto", "true").header("Authorization", token));
        }
        mvc.perform(get("/api/v1/ranking"))
                .andExpect(jsonPath("$[0].nome").value("Fernanda S."))
                .andExpect(jsonPath("$[0].pontos").value(4))
                .andExpect(jsonPath("$[1].nome").value("Gabriel"));
        mvc.perform(get("/api/v1/ranking").param("zona", "NORTE"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].posicao").value(1));
    }

    @Test
    void rankingIgnoraAdministradorEZonaInexistenteDa404() throws Exception {
        String token = cadastrar("Igor Cidadão", "SUL");
        Long id = identificar(token, "PAPEL");
        mvc.perform(multipart("/api/v1/identificacoes/" + id + "/feedback")
                .param("correto", "true").header("Authorization", token));
        // Pontos dados ao administrador diretamente no banco não podem aparecer no ranking
        jdbc.sql("""
                        INSERT INTO evento_pontuacao (usuario_id, tipo, pontos, referencia_id, dia)
                        SELECT id, 'CHECKIN', 500, NULL, CURRENT_DATE FROM usuario WHERE email = ?
                        """).param(ADMIN).update();

        mvc.perform(get("/api/v1/ranking"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Igor C."));
        mvc.perform(get("/api/v1/ranking").param("zona", "OESTE")).andExpect(status().isNotFound());
    }

    @Test
    void exclusaoDeContaApagaAsFotosDoDisco() throws Exception {
        String token = cadastrar("Jade", "LESTE");
        MockMultipartFile foto = new MockMultipartFile("foto", "item.jpg", "image/jpeg", JPEG);

        Long identificacao = identificar(token, "PLASTICO");
        mvc.perform(multipart("/api/v1/identificacoes/" + identificacao + "/feedback").file(foto)
                        .param("correto", "true").param("consentimentoFoto", "true")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        String resposta = mvc.perform(multipart("/api/v1/pontos/sugestoes").file(foto)
                        .param("nome", "PEV da Jade").param("tipo", "PEV").param("endereco", "Rua")
                        .param("latitude", "-5.0892").param("longitude", "-42.8016").param("materiais", "VIDRO")
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ponto = ((Number) JsonPath.read(resposta, "$.id")).longValue();

        Path diretorio = props.fotos().diretorio().toAbsolutePath().normalize();
        List<Path> arquivos = List.of(
                diretorio.resolve(jdbc.sql("SELECT foto_arquivo FROM identificacao WHERE id = ?")
                        .param(identificacao).query(String.class).single()),
                diretorio.resolve(jdbc.sql("SELECT foto_arquivo FROM ponto_coleta WHERE id = ?")
                        .param(ponto).query(String.class).single()));
        assertThat(arquivos).allMatch(Files::exists);

        mvc.perform(delete("/api/v1/me").header("Authorization", token)).andExpect(status().isNoContent());

        assertThat(arquivos).noneMatch(Files::exists);
        // O ponto sugerido continua (pendente de revisão), mas sem autor e sem foto
        assertThat(jdbc.sql("SELECT sugerido_por IS NULL AND foto_arquivo IS NULL FROM ponto_coleta WHERE id = ?")
                .param(ponto).query(Boolean.class).single()).isTrue();
    }
}
