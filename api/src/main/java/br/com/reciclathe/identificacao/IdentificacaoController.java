package br.com.reciclathe.identificacao;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.pontuacao.EventoPontuacao;

@RestController
@RequestMapping("/api/v1/identificacoes")
public class IdentificacaoController {

    private final IdentificacaoService service;

    public IdentificacaoController(IdentificacaoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdentificacaoResponse registrar(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody IdentificacaoRequest req) {
        return IdentificacaoResponse.de(service.registrar(Long.valueOf(jwt.getSubject()), req.material(),
                req.confianca(), req.zona(), req.versaoModelo()));
    }

    /** Multipart para permitir anexar a foto (só guardada com consentimentoFoto=true). */
    @PostMapping(path = "/{id}/feedback", consumes = "multipart/form-data")
    public FeedbackResponse feedback(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @RequestParam boolean correto,
                                     @RequestParam(required = false) String materialCorreto,
                                     @RequestParam(defaultValue = "false") boolean consentimentoFoto,
                                     @RequestPart(required = false) MultipartFile foto) {
        int pontos = service.registrarFeedback(Long.valueOf(jwt.getSubject()), id, correto, materialCorreto,
                        consentimentoFoto, foto)
                .map(EventoPontuacao::getPontos)
                .orElse(0);
        return new FeedbackResponse(pontos);
    }

    public record IdentificacaoRequest(
            @NotBlank String material,
            @NotNull @DecimalMin("0") @DecimalMax("1") Float confianca,
            String zona,
            @Size(max = 40) String versaoModelo) {
    }

    public record IdentificacaoResponse(Long id, String material, float confianca, String zona,
                                        Boolean feedbackCorreto, String materialCorrigido, Instant criadoEm) {
        public static IdentificacaoResponse de(Identificacao i) {
            return new IdentificacaoResponse(i.getId(), i.getMaterialPrevisto().getCodigo(), i.getConfianca(),
                    i.getZona() == null ? null : i.getZona().getCodigo(), i.getFeedbackCorreto(),
                    i.getMaterialCorrigido() == null ? null : i.getMaterialCorrigido().getCodigo(), i.getCriadoEm());
        }
    }

    public record FeedbackResponse(int pontosGanhos) {
    }
}
