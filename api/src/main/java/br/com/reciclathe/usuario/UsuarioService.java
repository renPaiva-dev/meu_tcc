package br.com.reciclathe.usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.ConflitoException;
import br.com.reciclathe.comum.ContaBloqueadaException;
import br.com.reciclathe.comum.CredenciaisInvalidasException;
import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.identificacao.IdentificacaoRepository;
import br.com.reciclathe.ponto.PontoColetaRepository;
import br.com.reciclathe.zona.ZonaService;

@Service
public class UsuarioService {

    /** Mínimo de caracteres da senha (cadastro, novo administrador e troca de senha). */
    public static final int SENHA_MINIMA = 8;

    private final UsuarioRepository usuarios;
    private final IdentificacaoRepository identificacoes;
    private final PontoColetaRepository pontos;
    private final ZonaService zonas;
    private final PasswordEncoder passwordEncoder;
    private final ArmazenamentoFotos fotos;

    public UsuarioService(UsuarioRepository usuarios, IdentificacaoRepository identificacoes,
                          PontoColetaRepository pontos, ZonaService zonas, PasswordEncoder passwordEncoder,
                          ArmazenamentoFotos fotos) {
        this.usuarios = usuarios;
        this.identificacoes = identificacoes;
        this.pontos = pontos;
        this.zonas = zonas;
        this.passwordEncoder = passwordEncoder;
        this.fotos = fotos;
    }

    @Transactional
    public Usuario cadastrar(String nome, String email, String senha, String zona) {
        Usuario usuario = novo(nome, email, senha, Perfil.CIDADAO);
        if (zona != null && !zona.isBlank()) {
            usuario.setZona(zonas.buscar(zona));
        }
        return gravarNovo(usuario);
    }

    /** Outro administrador, criado pelo painel. */
    @Transactional
    public Usuario criarAdmin(String nome, String email, String senha) {
        return gravarNovo(novo(nome, email, senha, Perfil.ADMIN));
    }

    public Usuario autenticar(String email, String senha) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email.strip())
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .orElseThrow(CredenciaisInvalidasException::new);
        if (!usuario.isAtivo()) {
            throw new ContaBloqueadaException();
        }
        return usuario;
    }

    public Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return email == null ? Optional.empty() : usuarios.findByEmailIgnoreCase(email);
    }

    /** Busca travando a linha do usuário: usado onde há limite diário que duas requisições simultâneas furariam. */
    @Transactional
    public Usuario buscarParaAtualizar(Long id) {
        return usuarios.findParaAtualizar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<Usuario> listar(Pageable pagina) {
        return usuarios.findAllByOrderByCriadoEmDesc(pagina);
    }

    @Transactional
    public Usuario alterarZona(Long id, String zona) {
        Usuario usuario = buscar(id);
        usuario.setZona(zona == null || zona.isBlank() ? null : zonas.buscar(zona));
        return usuario;
    }

    /** Bloqueia ou desbloqueia uma conta. O administrador não pode bloquear a si mesmo. */
    @Transactional
    public Usuario alterarAtivo(Long id, boolean ativo, String emailResponsavel) {
        Usuario usuario = buscar(id);
        if (!ativo && usuario.getEmail().equalsIgnoreCase(emailResponsavel)) {
            throw new RegraNegocioException("Você não pode bloquear a sua própria conta");
        }
        usuario.setAtivo(ativo);
        return usuario;
    }

    @Transactional
    public void trocarSenha(String email, String senhaAtual, String novaSenha) {
        Usuario usuario = buscarPorEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual não confere");
        }
        validarSenha(novaSenha);
        usuario.alterarSenha(passwordEncoder.encode(novaSenha));
    }

    /**
     * Exclusão de conta (LGPD, art. 18): apaga os dados pessoais e as fotos enviadas, inclusive as
     * dos pontos que o usuário sugeriu. As identificações ficam anônimas para não distorcer as
     * estatísticas do painel, e os pontos sugeridos continuam no mapa, sem o autor.
     */
    @Transactional
    public void excluirConta(Long id) {
        Usuario usuario = buscar(id);
        List<String> arquivos = new ArrayList<>(identificacoes.findFotosDoUsuario(id));
        arquivos.addAll(pontos.findFotosSugeridasPor(id));
        identificacoes.anonimizarDoUsuario(id);
        pontos.desvincularSugestoesDe(id);
        usuarios.delete(usuario);
        fotos.excluirAposCommit(arquivos);
    }

    private Usuario novo(String nome, String email, String senha, Perfil perfil) {
        validarSenha(senha);
        String emailNormalizado = email.strip().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("Já existe uma conta com este e-mail");
        }
        return new Usuario(nome.strip(), emailNormalizado, passwordEncoder.encode(senha), perfil);
    }

    /** O índice único em lower(email) pega o cadastro simultâneo que passou pela verificação acima. */
    private Usuario gravarNovo(Usuario usuario) {
        try {
            return usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new ConflitoException("Já existe uma conta com este e-mail");
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new RegraNegocioException("A senha deve ter pelo menos %d caracteres".formatted(SENHA_MINIMA));
        }
    }
}
