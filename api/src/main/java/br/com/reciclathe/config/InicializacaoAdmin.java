package br.com.reciclathe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.usuario.Perfil;
import br.com.reciclathe.usuario.Usuario;
import br.com.reciclathe.usuario.UsuarioRepository;

/** Cria o primeiro administrador a partir da configuração (variáveis ADMIN_EMAIL / ADMIN_SENHA). */
@Component
public class InicializacaoAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializacaoAdmin.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final ReciclatheProperties props;

    public InicializacaoAdmin(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, ReciclatheProperties props) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ReciclatheProperties.Admin admin = props.admin();
        if (admin.email() == null || admin.senha() == null || admin.senha().isBlank()) {
            if (usuarios.countByPerfil(Perfil.ADMIN) == 0) {
                log.warn("Nenhum administrador cadastrado. Defina ADMIN_EMAIL e ADMIN_SENHA para criar um.");
            }
            return;
        }
        if (!usuarios.existsByEmailIgnoreCase(admin.email())) {
            usuarios.save(new Usuario(admin.nome(), admin.email().toLowerCase(),
                    passwordEncoder.encode(admin.senha()), Perfil.ADMIN));
            log.info("Administrador {} criado.", admin.email());
        }
    }
}
