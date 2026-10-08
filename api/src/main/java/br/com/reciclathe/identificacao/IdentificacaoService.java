package br.com.reciclathe.identificacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.material.Material;
import br.com.reciclathe.material.MaterialService;
import br.com.reciclathe.pontuacao.EventoPontuacao;
import br.com.reciclathe.pontuacao.PontuacaoService;
import br.com.reciclathe.usuario.Usuario;
import br.com.reciclathe.usuario.UsuarioService;
import br.com.reciclathe.zona.Zona;
import br.com.reciclathe.zona.ZonaService;

@Service
public class IdentificacaoService {

    private final IdentificacaoRepository identificacoes;
    private final UsuarioService usuarios;
    private final MaterialService materiais;
    private final ZonaService zonas;
    private final PontuacaoService pontuacao;
    private final ArmazenamentoFotos fotos;

    public IdentificacaoService(IdentificacaoRepository identificacoes, UsuarioService usuarios,
                                MaterialService materiais, ZonaService zonas, PontuacaoService pontuacao,
                                ArmazenamentoFotos fotos) {
        this.identificacoes = identificacoes;
        this.usuarios = usuarios;
        this.materiais = materiais;
        this.zonas = zonas;
        this.pontuacao = pontuacao;
        this.fotos = fotos;
    }

    @Transactional
    public Identificacao registrar(Long usuarioId, String material, float confianca, String zona, String versaoModelo) {
        Usuario usuario = usuarios.buscar(usuarioId);
        Zona zonaIdentificacao = zona != null && !zona.isBlank() ? zonas.buscar(zona) : usuario.getZona();
        return identificacoes.save(new Identificacao(usuario, materiais.buscar(material), confianca,
                zonaIdentificacao, versaoModelo));
    }

    /**
     * Feedback do usuário sobre a classificação. Pontua igualmente acerto e erro, para não
     * incentivar o usuário a marcar "errado" só para ganhar pontos.
     */
    @Transactional
    public Optional<EventoPontuacao> registrarFeedback(Long usuarioId, Long identificacaoId, boolean correto,
                                                       String materialCorreto, boolean consentimentoFoto,
                                                       MultipartFile foto) {
        Identificacao identificacao = identificacoes.findById(identificacaoId)
                .filter(i -> i.pertenceA(usuarioId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Identificação não encontrada"));
        if (identificacao.temFeedback()) {
            throw new RegraNegocioException("Esta identificação já recebeu feedback");
        }
        Material corrigido = null;
        if (!correto) {
            if (materialCorreto == null || materialCorreto.isBlank()) {
                throw new RegraNegocioException("Informe qual é o material correto");
            }
            corrigido = materiais.buscar(materialCorreto);
            if (corrigido.getCodigo().equals(identificacao.getMaterialPrevisto().getCodigo())) {
                throw new RegraNegocioException("O material correto é igual ao identificado");
            }
        }
        // Sem consentimento explícito, a foto é descartada mesmo que tenha sido enviada
        String arquivo = consentimentoFoto && foto != null && !foto.isEmpty() ? fotos.salvar(foto) : null;
        identificacao.registrarFeedback(correto, corrigido, arquivo);
        return pontuacao.registrarFeedback(identificacao.getUsuario(), identificacao.getId());
    }

    public List<Identificacao> historico(Long usuarioId, int limite) {
        return identificacoes.findByUsuarioIdOrderByCriadoEmDesc(usuarioId, Limit.of(limite));
    }

    public List<Identificacao> errosRecentes(int limite) {
        return identificacoes.findByFeedbackCorretoFalseOrderByFeedbackEmDesc(Limit.of(limite));
    }

    public Identificacao buscar(Long id) {
        return identificacoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Identificação não encontrada"));
    }
}
