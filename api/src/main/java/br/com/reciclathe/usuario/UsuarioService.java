package br.com.reciclathe.usuario;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.CredenciaisInvalidasException;
import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.identificacao.IdentificacaoRepository;
import br.com.reciclathe.zona.ZonaService;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final IdentificacaoRepository identificacoes;
    private final ZonaService zonas;
    private final PasswordEncoder passwordEncoder;
    private final ArmazenamentoFotos fotos;

    public UsuarioService(UsuarioRepository usuarios, IdentificacaoRepository identificacoes, ZonaService zonas,
                          PasswordEncoder passwordEncoder, ArmazenamentoFotos fotos) {
        this.usuarios = usuarios;
        this.identificacoes = identificacoes;
        this.zonas = zonas;
        this.passwordEncoder = passwordEncoder;
        this.fotos = fotos;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha, String zona) {
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com este e-mail");
        }
        Usuario usuario = new Usuario(nome.strip(), email.strip().toLowerCase(), passwordEncoder.encode(senha),
                Perfil.CIDADAO);
        if (zona != null && !zona.isBlank()) {
            usuario.setZona(zonas.buscar(zona));
        }
        return usuarios.save(usuario);
    }

    public Usuario autenticar(String email, String senha) {
        return usuarios.findByEmailIgnoreCase(email.strip())
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .orElseThrow(CredenciaisInvalidasException::new);
    }

    public Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    @Transactional
    public Usuario alterarZona(Long id, String zona) {
        Usuario usuario = buscar(id);
        usuario.setZona(zona == null || zona.isBlank() ? null : zonas.buscar(zona));
        return usuario;
    }

    /**
     * Exclusão de conta (LGPD, art. 18): apaga os dados pessoais e as fotos enviadas.
     * As identificações ficam anônimas para não distorcer as estatísticas do painel.
     */
    @Transactional
    public void excluirConta(Long id) {
        Usuario usuario = buscar(id);
        List<String> arquivos = identificacoes.findFotosDoUsuario(id);
        identificacoes.anonimizarDoUsuario(id);
        usuarios.delete(usuario);
        arquivos.forEach(fotos::excluir);
    }
}
