package br.com.reciclathe.ponto;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import br.com.reciclathe.config.ReciclatheProperties;
import br.com.reciclathe.material.Material;
import br.com.reciclathe.material.MaterialService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importa do OpenStreetMap os locais ligados a resíduos no município (contêineres de recicláveis,
 * centros de reciclagem e estações de resíduos).
 *
 * <p>Os pontos entram sempre como PENDENTES: no OSM a maioria não tem nome nem lista de materiais,
 * então o administrador revisa e completa antes de publicar. Uma nova importação atualiza os pontos
 * ainda pendentes e não mexe nos que o administrador já aprovou ou recusou, nem recria os que ele excluiu
 * (tabela osm_ignorado).
 */
@Service
public class ImportacaoOsmService {

    /** Etiqueta "recycling:*" do OSM → material do ReciclaTHE. Etiquetas não listadas são ignoradas. */
    private static final Map<String, String> MATERIAIS_OSM = Map.ofEntries(
            Map.entry("plastic", "PLASTICO"),
            Map.entry("plastic_bottles", "PLASTICO"),
            Map.entry("plastic_packaging", "PLASTICO"),
            Map.entry("plastic_bags", "PLASTICO"),
            Map.entry("pet", "PLASTICO"),
            Map.entry("paper", "PAPEL"),
            Map.entry("cardboard", "PAPEL"),
            Map.entry("newspaper", "PAPEL"),
            Map.entry("magazines", "PAPEL"),
            Map.entry("paper_packaging", "PAPEL"),
            Map.entry("beverage_cartons", "PAPEL"),
            Map.entry("cans", "METAL"),
            Map.entry("scrap_metal", "METAL"),
            Map.entry("aluminium", "METAL"),
            Map.entry("glass", "VIDRO"),
            Map.entry("glass_bottles", "VIDRO"),
            Map.entry("batteries", "ELETRONICO"),
            Map.entry("car_batteries", "ELETRONICO"),
            Map.entry("electrical_appliances", "ELETRONICO"),
            Map.entry("small_appliances", "ELETRONICO"),
            Map.entry("electronics", "ELETRONICO"),
            Map.entry("mobile_phones", "ELETRONICO"),
            Map.entry("computers", "ELETRONICO"),
            Map.entry("organic", "ORGANICO"),
            Map.entry("green_waste", "ORGANICO"));

    private final ClienteOverpass overpass;
    private final PontoColetaRepository pontos;
    private final MaterialService materiais;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transacao;
    private final String municipio;

    public ImportacaoOsmService(ClienteOverpass overpass, PontoColetaRepository pontos, MaterialService materiais,
                                JsonMapper jsonMapper, PlatformTransactionManager transacoes,
                                ReciclatheProperties props) {
        this.overpass = overpass;
        this.pontos = pontos;
        this.materiais = materiais;
        this.jsonMapper = jsonMapper;
        this.transacao = new TransactionTemplate(transacoes);
        this.municipio = props.osm().municipio();
    }

    /**
     * A consulta ao Overpass pode levar mais de um minuto: é feita antes e fora da transação,
     * para não segurar uma conexão do banco enquanto espera a rede.
     */
    public Resultado importar() {
        JsonNode elementos = consultarOsm();
        return transacao.execute(status -> gravar(elementos));
    }

    private Resultado gravar(JsonNode elementos) {
        int novos = 0;
        int atualizados = 0;
        int jaRevisados = 0;
        int foraDaArea = 0;
        for (JsonNode elemento : elementos) {
            String tipoOsm = elemento.path("type").asString("");
            long idOsm = elemento.path("id").asLong();
            JsonNode tags = elemento.path("tags");
            // Nós trazem lat/lon direto; ways e relations trazem o centro calculado ("out center")
            JsonNode posicao = elemento.has("lat") ? elemento : elemento.path("center");
            double lat = posicao.path("lat").asDouble(Double.NaN);
            double lon = posicao.path("lon").asDouble(Double.NaN);
            if (!dentroDeTeresina(lat, lon)) {
                foraDaArea++;
                continue;
            }

            var existente = pontos.findByOsmTipoAndOsmId(tipoOsm, idOsm);
            boolean excluidoPeloAdmin = existente.isEmpty() && pontos.osmIgnorado(tipoOsm, idOsm);
            if (excluidoPeloAdmin || existente.isPresent() && existente.get().getStatus() != StatusPonto.PENDENTE) {
                jaRevisados++;
                continue;
            }
            PontoColeta ponto = existente.orElseGet(() -> new PontoColeta(StatusPonto.PENDENTE));
            TipoPonto tipo = tipo(tags);
            ponto.atualizar(
                    limitar(tags.path("name").asString(nomePadrao(tipo)), 160),
                    tipo,
                    limitar(endereco(tags), 255),
                    lat,
                    lon,
                    limitar(texto(tags, "opening_hours"), 255),
                    limitar(texto(tags, "phone", "contact:phone"), 30),
                    "OpenStreetMap (© colaboradores do OpenStreetMap, licença ODbL): https://www.openstreetmap.org/%s/%d"
                            .formatted(tipoOsm, idOsm),
                    materiais(tags));
            ponto.setOrigemOsm(tipoOsm, idOsm);
            pontos.save(ponto);
            if (existente.isPresent()) {
                atualizados++;
            } else {
                novos++;
            }
        }
        return new Resultado(novos, atualizados, jaRevisados, foraDaArea);
    }

    String consulta() {
        return """
                [out:json][timeout:60];
                area["boundary"="administrative"]["admin_level"="8"]["name"="%s"]->.municipio;
                (
                  nwr["amenity"="recycling"](area.municipio);
                  nwr["amenity"="waste_transfer_station"](area.municipio);
                );
                out center tags;
                """.formatted(municipio);
    }

    private JsonNode consultarOsm() {
        String resposta;
        try {
            resposta = overpass.consultar(consulta());
        } catch (RestClientException e) {
            throw new FalhaImportacaoException("Não foi possível consultar o OpenStreetMap agora. Tente mais tarde.");
        }
        try {
            JsonNode elementos = jsonMapper.readTree(resposta).path("elements");
            if (!elementos.isArray()) {
                throw new FalhaImportacaoException("Resposta inesperada do OpenStreetMap.");
            }
            return elementos;
        } catch (JacksonException e) {
            throw new FalhaImportacaoException("Resposta inválida do OpenStreetMap.");
        }
    }

    private static TipoPonto tipo(JsonNode tags) {
        if ("waste_transfer_station".equals(tags.path("amenity").asString(""))) {
            return TipoPonto.OUTRO;
        }
        return "centre".equals(tags.path("recycling_type").asString(""))
                ? TipoPonto.CENTRO_RECICLAGEM
                : TipoPonto.PEV;
    }

    private static String nomePadrao(TipoPonto tipo) {
        return switch (tipo) {
            case PEV -> "Contêiner de recicláveis (sem nome no OSM)";
            case CENTRO_RECICLAGEM -> "Centro de reciclagem (sem nome no OSM)";
            default -> "Estação de resíduos (sem nome no OSM)";
        };
    }

    private Set<Material> materiais(JsonNode tags) {
        Set<String> codigos = tags.propertyNames().stream()
                .filter(chave -> chave.startsWith("recycling:"))
                .filter(chave -> "yes".equals(tags.path(chave).asString("")))
                .map(chave -> MATERIAIS_OSM.get(chave.substring("recycling:".length())))
                .filter(codigo -> codigo != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return codigos.isEmpty() ? Set.of() : materiais.buscarTodos(codigos);
    }

    private static String endereco(JsonNode tags) {
        String rua = Stream.of(texto(tags, "addr:street"), texto(tags, "addr:housenumber"))
                .filter(t -> t != null)
                .collect(Collectors.joining(", "));
        String completo = Stream.of(rua.isEmpty() ? null : rua, texto(tags, "addr:suburb"))
                .filter(t -> t != null)
                .collect(Collectors.joining(" - "));
        return completo.isEmpty() ? "Endereço não informado no OpenStreetMap" : completo;
    }

    private static String texto(JsonNode tags, String... chaves) {
        for (String chave : chaves) {
            String valor = tags.path(chave).asString("").strip();
            if (!valor.isEmpty()) {
                return valor;
            }
        }
        return null;
    }

    private static String limitar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    /** Mesmo retângulo usado na validação de pontos (DadosPonto). */
    private static boolean dentroDeTeresina(double lat, double lon) {
        return lat >= -5.60 && lat <= -4.75 && lon >= -43.05 && lon <= -42.55;
    }

    public record Resultado(int novos, int atualizados, int jaRevisados, int foraDaArea) {
    }

    public static class FalhaImportacaoException extends RuntimeException {
        public FalhaImportacaoException(String mensagem) {
            super(mensagem);
        }
    }
}
