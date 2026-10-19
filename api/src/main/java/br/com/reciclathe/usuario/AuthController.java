package br.com.reciclathe.usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.reciclathe.pontuacao.PontuacaoService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UsuarioService usuarios;
    private final TokenService tokens;
    private final PontuacaoService pontuacao;

    public AuthController(UsuarioService usuarios, TokenService tokens, PontuacaoService pontuacao) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.pontuacao = pontuacao;
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse cadastrar(@Valid @RequestBody CadastroRequest req) {
        Usuario usuario = usuarios.cadastrar(req.nome(), req.email(), req.senha(), req.zona());
        return new TokenResponse(tokens.gerar(usuario), UsuarioResponse.de(usuario, 0));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario usuario = usuarios.autenticar(req.email(), req.senha());
        return new TokenResponse(tokens.gerar(usuario), UsuarioResponse.de(usuario, pontuacao.total(usuario.getId())));
    }

    public record CadastroRequest(
            @NotBlank @Size(max = 120) String nome,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = UsuarioService.SENHA_MINIMA, max = 72) String senha,
            String zona) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {
    }

    public record TokenResponse(String token, UsuarioResponse usuario) {
    }
}
