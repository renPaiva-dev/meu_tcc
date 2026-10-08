package br.com.reciclathe.usuario;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.reciclathe.config.ReciclatheProperties;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final ReciclatheProperties props;

    public TokenService(JwtEncoder encoder, ReciclatheProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    /** O subject do token é o id do usuário; a API não depende do e-mail depois do login. */
    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("reciclathe")
                .subject(usuario.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(props.jwt().validade()))
                .claim("perfil", usuario.getPerfil().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
