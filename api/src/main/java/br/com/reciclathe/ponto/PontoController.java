package br.com.reciclathe.ponto;

import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.material.Material;
import br.com.reciclathe.pontuacao.EventoPontuacao;

@RestController
@RequestMapping("/api/v1/pontos")
public class PontoController {

    private final PontoService service;

    public PontoController(PontoService service) {
        this.service = service;
    }

    /**
     * Com lat/lon: pontos aprovados mais próximos (opcionalmente filtrados por material).
     * Sem lat/lon: todos os pontos aprovados, usado pelo app para o cache offline.
     */
    @GetMapping
    public List<PontoResponse> listar(@RequestParam(required = false) Double lat,
                                      @RequestParam(required = false) Double lon,
                                      @RequestParam(defaultValue = "10") double raioKm,
                                      @RequestParam(required = false) String material,
                                      @RequestParam(defaultValue = "50") int limite) {
        if (lat == null || lon == null) {
            return service.listarAprovados().stream().map(p -> PontoResponse.de(p, null)).toList();
        }
        double raio = Math.min(raioKm, 50) * 1000;
        return service.buscarProximos(lat, lon, raio, material, Math.min(limite, 200)).stream()
                .map(pp -> PontoResponse.de(pp.ponto(), pp.distanciaMetros()))
                .toList();
    }

    @GetMapping("/{id}")
    public PontoResponse buscar(@PathVariable Long id) {
        return PontoResponse.de(service.buscarAprovado(id), null);
    }

    @PostMapping(path = "/sugestoes", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public SugestaoResponse sugerir(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @ModelAttribute DadosPonto dados,
                                    @RequestPart(required = false) MultipartFile foto) {
        PontoColeta ponto = service.sugerir(Long.valueOf(jwt.getSubject()), dados, foto);
        return new SugestaoResponse(ponto.getId(), ponto.getStatus());
    }

    @PostMapping("/{id}/checkin")
    public CheckinResponse checkin(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                   @Valid @RequestBody CheckinRequest req) {
        EventoPontuacao evento = service.checkin(Long.valueOf(jwt.getSubject()), id, req.latitude(), req.longitude());
        return new CheckinResponse(evento.getPontos());
    }

    public record PontoResponse(Long id, String nome, String tipo, String endereco, double latitude, double longitude,
                                String horario, String telefone, String fonte, List<String> materiais,
                                Double distanciaMetros) {
        static PontoResponse de(PontoColeta p, Double distancia) {
            List<String> materiais = p.getMateriais().stream()
                    .sorted(Comparator.comparingInt(Material::getOrdem))
                    .map(Material::getCodigo)
                    .toList();
            return new PontoResponse(p.getId(), p.getNome(), p.getTipo().name(), p.getEndereco(), p.getLatitude(),
                    p.getLongitude(), p.getHorario(), p.getTelefone(), p.getFonte(), materiais, distancia);
        }
    }

    public record SugestaoResponse(Long id, StatusPonto status) {
    }

    public record CheckinRequest(@NotNull Double latitude, @NotNull Double longitude) {
    }

    public record CheckinResponse(int pontosGanhos) {
    }
}
