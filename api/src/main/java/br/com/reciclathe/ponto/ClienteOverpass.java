package br.com.reciclathe.ponto;

import java.net.http.HttpClient;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.reciclathe.config.ReciclatheProperties;

/**
 * Acesso à API Overpass (consultas ao OpenStreetMap). Isolado num componente próprio
 * para os testes poderem substituí-lo sem acessar a internet.
 */
@Component
public class ClienteOverpass {

    private static final Logger log = LoggerFactory.getLogger(ClienteOverpass.class);

    private final RestClient http;
    private final List<String> servidores;

    public ClienteOverpass(ReciclatheProperties props) {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(props.osm().timeout())
                .build());
        fabrica.setReadTimeout(props.osm().timeout());
        this.http = RestClient.builder()
                .requestFactory(fabrica)
                // A política de uso do Overpass recusa requisições sem identificação (HTTP 406)
                .defaultHeader("User-Agent", "ReciclaTHE/0.1 (TCC; orientacao de descarte em Teresina-PI)")
                .build();
        this.servidores = props.osm().overpassUrls();
    }

    /**
     * Executa uma consulta Overpass QL e devolve o JSON bruto. Os servidores públicos ficam
     * sobrecarregados com frequência (HTTP 429/504), então tenta cada um da lista em ordem.
     */
    public String consultar(String consulta) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("data", consulta);
        RestClientException ultimaFalha = null;
        for (String servidor : servidores) {
            try {
                return http.post()
                        .uri(servidor)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(formulario)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                log.warn("Servidor Overpass {} falhou: {}", servidor, e.getMessage());
                ultimaFalha = e;
            }
        }
        throw ultimaFalha;
    }
}
