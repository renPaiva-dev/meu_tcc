package br.com.reciclathe.ponto;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.comum.ArmazenamentoFotos;
import br.com.reciclathe.comum.RecursoNaoEncontradoException;
import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.config.ReciclatheProperties;
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

    /** Tamanho da coluna motivo_recusa. */
    public static final int MOTIVO_MAXIMO = 255;

    private final PontoColetaRepository pontos;
    private final BuscaGeografica busca;
    private final MaterialService materiais;
    private final UsuarioService usuarios;
    private final PontuacaoService pontuacao;
    private final ArmazenamentoFotos fotos;
    private final int limiteSugestoesPendentes;

    public PontoService(PontoColetaRepository pontos, BuscaGeografica busca, MaterialService materiais,
                        UsuarioService usuarios, PontuacaoService pontuacao, ArmazenamentoFotos fotos,
                        ReciclatheProperties props) {
        this.pontos = pontos;
        this.busca = busca;
        this.materiais = materiais;
        this.usuarios = usuarios;
        this.pontuacao = pontuacao;
        this.fotos = fotos;
        this.limiteSugestoesPendentes = props.limites().sugestoesPendentes();
    }

    /** Ponto com materiais, autor da sugestão e revisor já carregados. */
    public PontoColeta buscar(Long id) {
        return pontos.findComDetalhesById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ponto não encontrado"));
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

    /** Página da lista do painel. Os materiais são carregados aqui porque a tela é renderizada fora da transação. */
    @Transactional(readOnly = true)
    public Page<PontoColeta> listar(StatusPonto status, Pageable pagina) {
        Page<PontoColeta> resultado = status == null
                ? pontos.findAllByOrderByStatusAscNomeAsc(pagina)
                : pontos.findByStatusOrderByNomeAsc(status, pagina);
        resultado.forEach(p -> Hibernate.initialize(p.getMateriais()));
        return resultado;
    }

    public long contar(StatusPonto status) {
        return pontos.countByStatus(status);
    }

    public List<PontoProximo> buscarProximos(double latitude, double longitude, double raioMetros,
                                             String material, int limite) {
        List<BuscaGeografica.Resultado> resultados = busca.proximos(latitude, longitude, raioMetros, material, limite);
        if (resultados.isEmpty()) {
            return List.of();
        }
        Map<Long, PontoColeta> porId = pontos.findComMateriaisByIdIn(
                        resultados.stream().map(BuscaGeografica.Resultado::id).toList())
                .stream().collect(Collectors.toMap(PontoColeta::getId, Function.identity()));
        return resultados.stream()
                .filter(r -> porId.containsKey(r.id()))
                .map(r -> new PontoProximo(porId.get(r.id()), r.distanciaMetros()))
                .sorted(Comparator.comparingDouble(PontoProximo::distanciaMetros))
                .toList();
    }

    /**
     * Sugestão de um cidadão: entra como PENDENTE e só aparece no mapa após aprovação.
     * Cada usuário pode ter um número limitado de sugestões aguardando revisão.
     */
    @Transactional
    public PontoColeta sugerir(Long usuarioId, DadosPonto dados, MultipartFile foto) {
        Usuario usuario = usuarios.buscar(usuarioId);
        if (pontos.countBySugeridoPorIdAndStatus(usuarioId, StatusPonto.PENDENTE) >= limiteSugestoesPendentes) {
            throw new RegraNegocioException(("Você já tem %d sugestões aguardando revisão. "
                    + "Aguarde a análise antes de enviar outras.").formatted(limiteSugestoesPendentes));
        }
        PontoColeta ponto = new PontoColeta(StatusPonto.PENDENTE);
        preencher(ponto, dados);
        ponto.setSugeridoPor(usuario);
        if (foto != null && !foto.isEmpty()) {
            // Se a transação for desfeita, o próprio ArmazenamentoFotos apaga o arquivo
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

    /**
     * Aprova o ponto, registrando o administrador responsável. A sugestão de um cidadão pontua uma
     * única vez, inclusive quando foi recusada antes e aprovada depois.
     */
    @Transactional
    public void aprovar(Long id, String emailRevisor) {
        PontoColeta ponto = buscar(id);
        if (ponto.getStatus() == StatusPonto.APROVADO) {
            return;
        }
        if (ponto.getMateriais().isEmpty()) {
            throw new RegraNegocioException("Informe os materiais aceitos antes de aprovar o ponto");
        }
        ponto.aprovar(usuarios.buscarPorEmail(emailRevisor).orElse(null));
        if (ponto.getSugeridoPor() != null && !pontuacao.sugestaoJaPontuada(ponto.getId())) {
            pontuacao.registrar(ponto.getSugeridoPor(), TipoEvento.SUGESTAO_APROVADA, ponto.getId());
        }
    }

    @Transactional
    public void recusar(Long id, String motivo, String emailRevisor) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraNegocioException("Informe o motivo da recusa");
        }
        if (motivo.strip().length() > MOTIVO_MAXIMO) {
            throw new RegraNegocioException("O motivo da recusa deve ter no máximo %d caracteres".formatted(MOTIVO_MAXIMO));
        }
        buscar(id).recusar(motivo.strip(), usuarios.buscarPorEmail(emailRevisor).orElse(null));
    }

    /**
     * Exclui o ponto. Se veio do OpenStreetMap, o elemento entra na lista de ignorados para a próxima
     * importação não o recriar. A foto só é apagada do disco depois do commit.
     */
    @Transactional
    public void excluir(Long id) {
        PontoColeta ponto = buscar(id);
        if (ponto.getOsmId() != null) {
            pontos.ignorarOsm(ponto.getOsmTipo(), ponto.getOsmId());
        }
        pontos.delete(ponto);
        fotos.excluirAposCommit(ponto.getFotoArquivo());
    }

    @Transactional
    public EventoPontuacao checkin(Long usuarioId, Long pontoId, double latitude, double longitude) {
        PontoColeta ponto = buscarAprovado(pontoId);
        double distancia = busca.distancia(ponto.getId(), latitude, longitude);
        if (distancia > RAIO_CHECKIN_METROS) {
            throw new RegraNegocioException(
                    "Você está a %.0f m do ponto. Aproxime-se para fazer o check-in.".formatted(distancia));
        }
        // Trava o usuário: dois check-ins simultâneos não furam o limite diário
        Usuario usuario = usuarios.buscarParaAtualizar(usuarioId);
        return pontuacao.registrarCheckin(usuario, pontoId);
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
