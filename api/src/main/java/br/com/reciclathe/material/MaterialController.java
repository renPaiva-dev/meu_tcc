package br.com.reciclathe.material;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Materiais e orientações de descarte; o app baixa esta lista para funcionar offline. */
@RestController
@RequestMapping("/api/v1/materiais")
public class MaterialController {

    private final MaterialService service;

    public MaterialController(MaterialService service) {
        this.service = service;
    }

    @GetMapping
    public List<MaterialResponse> listar() {
        return service.listar().stream().map(MaterialResponse::de).toList();
    }

    @GetMapping("/{codigo}")
    public MaterialResponse buscar(@PathVariable String codigo) {
        return MaterialResponse.de(service.buscar(codigo));
    }

    public record MaterialResponse(String codigo, String nome, String corHex, String corNome, boolean reciclavel,
                                   String comoPreparar, String ondeDescartar, String observacoes, String fonte,
                                   Instant atualizadoEm) {
        static MaterialResponse de(Material m) {
            return new MaterialResponse(m.getCodigo(), m.getNome(), m.getCorHex(), m.getCorNome(), m.isReciclavel(),
                    m.getComoPreparar(), m.getOndeDescartar(), m.getObservacoes(), m.getFonte(), m.getAtualizadoEm());
        }
    }
}
