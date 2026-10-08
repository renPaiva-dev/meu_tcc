package br.com.reciclathe.usuario;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.reciclathe.identificacao.IdentificacaoController.IdentificacaoResponse;
import br.com.reciclathe.identificacao.IdentificacaoService;
import br.com.reciclathe.pontuacao.EventoPontuacao;
import br.com.reciclathe.pontuacao.PontuacaoService;

/** Dados do próprio usuário autenticado. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UsuarioService usuarios;
    private final PontuacaoService pontuacao;
    private final IdentificacaoService identificacoes;

    public MeController(UsuarioService usuarios, PontuacaoService pontuacao, IdentificacaoService identificacoes) {
        this.usuarios = usuarios;
        this.pontuacao = pontuacao;
        this.identificacoes = identificacoes;
    }

    @GetMapping
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        return UsuarioResponse.de(usuarios.buscar(id), pontuacao.total(id));
    }

    @PatchMapping
    public UsuarioResponse atualizar(@AuthenticationPrincipal Jwt jwt, @RequestBody AtualizacaoRequest req) {
        Long id = Long.valueOf(jwt.getSubject());
        return UsuarioResponse.de(usuarios.alterarZona(id, req.zona()), pontuacao.total(id));
    }

    @GetMapping("/historico")
    public HistoricoResponse historico(@AuthenticationPrincipal Jwt jwt) {
        Long id = Long.valueOf(jwt.getSubject());
        return new HistoricoResponse(
                identificacoes.historico(id, 50).stream().map(IdentificacaoResponse::de).toList(),
                pontuacao.recentes(id, 50).stream().map(EventoResponse::de).toList());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@AuthenticationPrincipal Jwt jwt) {
        usuarios.excluirConta(Long.valueOf(jwt.getSubject()));
    }

    public record AtualizacaoRequest(String zona) {
    }

    public record HistoricoResponse(List<IdentificacaoResponse> identificacoes, List<EventoResponse> pontos) {
    }

    public record EventoResponse(String tipo, int pontos, Long referenciaId, Instant criadoEm) {
        static EventoResponse de(EventoPontuacao e) {
            return new EventoResponse(e.getTipo().name(), e.getPontos(), e.getReferenciaId(), e.getCriadoEm());
        }
    }
}
