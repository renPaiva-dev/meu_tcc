package br.com.reciclathe.zona;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zonas")
public class ZonaController {

    private final ZonaService service;

    public ZonaController(ZonaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ZonaResponse> listar() {
        return service.listar().stream().map(ZonaResponse::de).toList();
    }

    /** Descobre a zona a partir da localização; 404 se o ponto não cair em nenhum bairro importado. */
    @GetMapping("/localizar")
    public ResponseEntity<LocalizacaoResponse> localizar(@RequestParam double lat, @RequestParam double lon) {
        return ResponseEntity.of(service.localizar(lat, lon)
                .map(l -> new LocalizacaoResponse(ZonaResponse.de(l.zona()), l.bairro())));
    }

    public record ZonaResponse(String codigo, String nome, String telefoneColeta, String fonte) {
        static ZonaResponse de(Zona z) {
            return new ZonaResponse(z.getCodigo(), z.getNome(), z.getTelefoneColeta(), z.getFonte());
        }
    }

    public record LocalizacaoResponse(ZonaResponse zona, String bairro) {
    }
}
