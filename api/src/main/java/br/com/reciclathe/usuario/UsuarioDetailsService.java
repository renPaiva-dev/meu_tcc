package br.com.reciclathe.usuario;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Login do painel web. Qualquer usuário autentica, mas só o perfil ADMIN acessa /admin.
 * Conta bloqueada é recusada pelo próprio Spring Security (DisabledException).
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return User.withUsername(usuario.getEmail())
                .password(usuario.getSenhaHash())
                .roles(usuario.getPerfil().name())
                .disabled(!usuario.isAtivo())
                .build();
    }
}
