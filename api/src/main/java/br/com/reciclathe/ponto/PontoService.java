package br.com.reciclathe.ponto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.material.MaterialService;
import br.com.reciclathe.pontuacao.EventoPontuacao;
import br.com.reciclathe.pontuacao.PontuacaoService;
import br.com.reciclathe.pontuacao.TipoEvento;
import br.com.reciclathe.usuario.Usuario;
import br.com.reciclathe.usuario.UsuarioService;

@Service
public class PontoService {

    /** Distância máxima, em metros, para o check-in ser aceito (tolera a imprecisão do GPS). */
    public static final double RAIO_CHECKIN_METROS = 150;

    private final PontoColetaRepository pontos;
    private final BuscaGeografica busca;
    private final MaterialService materiais;
    private final UsuarioService usuarios;
    private final PontuacaoService pontuacao;
    private final ArmazenamentoFotos fotos;

    public PontoService(PontoColetaRepository pontos, BuscaGeografica busca, MaterialService materiais,
                        UsuarioService usuarios, PontuacaoService pontuacao, ArmazenamentoFotos fotos) {
        this.pontos = pontos;
        this.busca = busca;
        this.materiais = materiais;
        this.usuarios = usuarios;
        this.pontuacao = pontuacao;
        this.fotos = fotos;
    }

    public PontoColeta buscar(Long id) {
        return pontos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ponto não encontrado"));
    }

    public PontoColeta buscarAprovado(Long id) {
        PontoColeta ponto = buscar(id);
        if (ponto.getStatus() != StatusPonto.APROVADO) {
            throw new RecursoNaoEncontradoException("Ponto não encontrado");
        }
        return ponto;
    }

    public List<PontoColeta> listarAprovados() {
        return pontos.findByStatusOrderByNomeAsc(StatusPonto.APROVADO);
    }

    public List<PontoColeta> listar(StatusPonto status) {
        return status == null ? pontos.findAllByOrderByStatusAscNomeAsc() : pontos.findByStatusOrderByNomeAsc(status);
    }

    public long contar(StatusPonto status) {
        return pontos.countByStatus(status);
    }

    public List<PontoProximo> buscarProximos(double latitude, double longitude, double raioMetros,
                                             String material, int limite) {
        List<BuscaGeografica.Resultado> resultados = busca.proximos(latitude, longitude, raioMetros, material, limite);
        Map<Long, PontoColeta> porId = pontos.findAllById(resultados.stream().map(BuscaGeografica.Resultado::id).toList())
                .stream().collect(Collectors.toMap(PontoColeta::getId, Function.identity()));
        return resultados.stream()
                .map(r -> new PontoProximo(porId.get(r.id()), r.distanciaMetros()))
                .sorted(Comparator.comparingDouble(PontoProximo::distanciaMetros))
                .toList();
    }

    /** Sugestão de um cidadão: entra como PENDENTE e só aparece no mapa após aprovação. */
    @Transactional
    public PontoColeta sugerir(Long usuarioId, DadosPonto dados, MultipartFile foto) {
        PontoColeta ponto = new PontoColeta(StatusPonto.PENDENTE);
        preencher(ponto, dados);
        ponto.setSugeridoPor(usuarios.buscar(usuarioId));
        if (foto != null && !foto.isEmpty()) {
            ponto.setFotoArquivo(fotos.salvar(foto));
        }
        return pontos.save(ponto);
    }

    /** Cadastro/edição feito pelo administrador; pontos criados por ele já nascem aprovados. */
    @Transactional
    public PontoColeta salvar(Long id, DadosPonto dados) {
        PontoColeta ponto = id == null ? new PontoColeta(StatusPonto.APROVADO) : buscar(id);
        preencher(ponto, dados);
        return pontos.save(ponto);
    }

    @Transactional
    public void aprovar(Long id) {
        PontoColeta ponto = buscar(id);
        if (ponto.getStatus() == StatusPonto.APROVADO) {
            return;
        }
        if (ponto.getMateriais().isEmpty()) {
            throw new RegraNegocioException("Informe os materiais aceitos antes de aprovar o ponto");
        }
        boolean eraSugestaoPendente = ponto.getStatus() == StatusPonto.PENDENTE && ponto.getSugeridoPor() != null;
        ponto.aprovar();
        if (eraSugestaoPendente) {
            pontuacao.registrar(ponto.getSugeridoPor(), TipoEvento.SUGESTAO_APROVADA, ponto.getId());
        }
    }

    @Transactional
    public void recusar(Long id, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da recusa");
        }
        buscar(id).recusar(motivo.strip());
    }

    @Transactional
    public void excluir(Long id) {
        PontoColeta ponto = buscar(id);
        pontos.delete(ponto);
        fotos.excluir(ponto.getFotoArquivo());
    }

    @Transactional
    public EventoPontuacao checkin(Long usuarioId, Long pontoId, double latitude, double longitude) {
        PontoColeta ponto = buscarAprovado(pontoId);
        double distancia = busca.distancia(ponto.getId(), latitude, longitude);
        if (distancia > RAIO_CHECKIN_METROS) {
            throw new RegraNegocioException(
                    "Você está a %.0f m do ponto. Aproxime-se para fazer o check-in.".formatted(distancia));
        }
        if (pontuacao.fezCheckinHoje(usuarioId, pontoId)) {
            throw new RegraNegocioException("Você já fez check-in neste ponto hoje");
        }
        Usuario usuario = usuarios.buscar(usuarioId);
        return pontuacao.registrar(usuario, TipoEvento.CHECKIN, pontoId);
    }

    private void preencher(PontoColeta ponto, DadosPonto d) {
        ponto.atualizar(d.nome().strip(), d.tipo(), d.endereco().strip(), d.latitude(), d.longitude(),
                vazioParaNulo(d.horario()), vazioParaNulo(d.telefone()), vazioParaNulo(d.fonte()),
                materiais.buscarTodos(d.materiais()));
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    public record PontoProximo(PontoColeta ponto, double distanciaMetros) {
    }
}
