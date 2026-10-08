package br.com.reciclathe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.material.Material;
import br.com.reciclathe.ponto.ClienteOverpass;
import br.com.reciclathe.ponto.ImportacaoOsmService;
import br.com.reciclathe.ponto.PontoColeta;
import br.com.reciclathe.ponto.PontoColetaRepository;
import br.com.reciclathe.ponto.PontoService;
import br.com.reciclathe.ponto.StatusPonto;
import br.com.reciclathe.ponto.TipoPonto;

class ImportacaoOsmTest extends IntegracaoTest {

    /** Resposta no formato da API Overpass, com os casos que existem hoje em Teresina. */
    static final String RESPOSTA = """
            {"elements": [
              {"type": "node", "id": 8259815076, "lat": -5.0698, "lon": -42.8015,
               "tags": {"amenity": "recycling", "recycling_type": "container",
                        "recycling:cans": "yes", "recycling:glass_bottles": "yes", "recycling:paper": "yes",
                        "recycling:plastic": "yes", "recycling:scrap_metal": "yes", "recycling:clothes": "yes"}},
              {"type": "node", "id": 12264317369, "lat": -5.0956, "lon": -42.7389,
               "tags": {"amenity": "recycling", "recycling_type": "centre", "name": "Sucata MM",
                        "opening_hours": "Seg a Sex - 08h às 12h e 14h às 18h30",
                        "addr:street": "Rua Exemplo", "addr:housenumber": "100", "addr:suburb": "Bairro Exemplo"}},
              {"type": "way", "id": 888422583, "center": {"lat": -5.0644, "lon": -42.8362},
               "tags": {"amenity": "waste_transfer_station",
                        "name": "Estação de Transbordo de Resíduos Sólidos - Lagoas do Norte"}},
              {"type": "node", "id": 1, "lat": -23.55, "lon": -46.63, "tags": {"amenity": "recycling"}}
            ]}
            """;

    @MockitoBean
    ClienteOverpass overpass;

    @Autowired
    ImportacaoOsmService importacao;

    @Autowired
    PontoColetaRepository pontos;

    @Autowired
    PontoService pontoService;

    @Test
    void importaComoPendenteMapeandoTagsDoOsm() {
        when(overpass.consultar(anyString())).thenReturn(RESPOSTA);

        ImportacaoOsmService.Resultado r = importacao.importar();

        assertThat(r).isEqualTo(new ImportacaoOsmService.Resultado(3, 0, 0, 1));
        List<PontoColeta> importados = pontos.findByStatusOrderByNomeAsc(StatusPonto.PENDENTE);
        assertThat(importados).hasSize(3);

        PontoColeta conteiner = pontos.findByOsmTipoAndOsmId("node", 8259815076L).orElseThrow();
        assertThat(conteiner.getTipo()).isEqualTo(TipoPonto.PEV);
        assertThat(conteiner.getNome()).isEqualTo("Contêiner de recicláveis (sem nome no OSM)");
        assertThat(conteiner.getEndereco()).isEqualTo("Endereço não informado no OpenStreetMap");
        assertThat(conteiner.getMateriais().stream().sorted(Comparator.comparingInt(Material::getOrdem))
                .map(Material::getCodigo)).containsExactly("PLASTICO", "PAPEL", "METAL", "VIDRO");
        assertThat(conteiner.getFonte()).contains("ODbL").endsWith("/node/8259815076");

        PontoColeta sucata = pontos.findByOsmTipoAndOsmId("node", 12264317369L).orElseThrow();
        assertThat(sucata.getTipo()).isEqualTo(TipoPonto.CENTRO_RECICLAGEM);
        assertThat(sucata.getEndereco()).isEqualTo("Rua Exemplo, 100 - Bairro Exemplo");
        assertThat(sucata.getHorario()).startsWith("Seg a Sex");
        assertThat(sucata.getMateriais()).isEmpty();

        PontoColeta transbordo = pontos.findByOsmTipoAndOsmId("way", 888422583L).orElseThrow();
        assertThat(transbordo.getTipo()).isEqualTo(TipoPonto.OUTRO);
        assertThat(transbordo.getLatitude()).isEqualTo(-5.0644);
    }

    @Test
    void reimportarAtualizaPendentesENaoMexeNosRevisados() {
        when(overpass.consultar(anyString())).thenReturn(RESPOSTA);
        importacao.importar();
        Long conteiner = pontos.findByOsmTipoAndOsmId("node", 8259815076L).orElseThrow().getId();
        pontoService.aprovar(conteiner);
        Long transbordo = pontos.findByOsmTipoAndOsmId("way", 888422583L).orElseThrow().getId();
        pontoService.recusar(transbordo, "Não recebe resíduos do cidadão");

        ImportacaoOsmService.Resultado r = importacao.importar();

        assertThat(r).isEqualTo(new ImportacaoOsmService.Resultado(0, 1, 2, 1));
        assertThat(pontos.count()).isEqualTo(3);
        assertThat(pontoService.buscar(conteiner).getStatus()).isEqualTo(StatusPonto.APROVADO);
        assertThat(pontoService.buscar(transbordo).getStatus()).isEqualTo(StatusPonto.RECUSADO);
    }

    @Test
    void naoAprovaPontoSemMateriais() {
        when(overpass.consultar(anyString())).thenReturn(RESPOSTA);
        importacao.importar();
        Long sucata = pontos.findByOsmTipoAndOsmId("node", 12264317369L).orElseThrow().getId();

        assertThatThrownBy(() -> pontoService.aprovar(sucata))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("materiais");
    }

    @Test
    void falhaDeRedeViraMensagemNoPainel() throws Exception {
        when(overpass.consultar(anyString())).thenThrow(new ResourceAccessException("timeout"));

        mvc.perform(post("/admin/pontos/importar-osm").with(user("admin@teste.local").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/pontos?status=PENDENTE"))
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("OpenStreetMap")));
    }
}
