package br.com.reciclathe.pontuacao;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.comum.RegraNegocioException;
import br.com.reciclathe.config.ReciclatheProperties;
import br.com.reciclathe.usuario.Usuario;

@Service
public class PontuacaoService {

    /** Feedbacks pontuados por dia; acima disso o feedback é aceito, mas não rende pontos. */
    public static final int LIMITE_FEEDBACK_DIARIO = 5;

    private final EventoPontuacaoRepository eventos;
    private final ZoneId fuso;
    private final int limiteCheckinsPorDia;

    public PontuacaoService(EventoPontuacaoRepository eventos, ReciclatheProperties props) {
        this.eventos = eventos;
        this.fuso = props.fusoHorario();
        this.limiteCheckinsPorDia = props.limites().checkinsPorDia();
    }

    public long total(Long usuarioId) {
        return eventos.somarPontos(usuarioId);
    }

    @Transactional
    public Optional<EventoPontuacao> registrarFeedback(Usuario usuario, Long identificacaoId) {
        long hoje = eventos.countByUsuarioIdAndTipoAndDia(usuario.getId(), TipoEvento.FEEDBACK, hoje());
        if (hoje >= LIMITE_FEEDBACK_DIARIO) {
            return Optional.empty();
        }
        return Optional.of(registrar(usuario, TipoEvento.FEEDBACK, identificacaoId));
    }

    /**
     * Check-in: um por ponto por dia e no máximo {@code reciclathe.limites.checkins-por-dia} no total,
     * para que circular entre pontos vizinhos não vire fonte infinita de pontos.
     */
    @Transactional
    public EventoPontuacao registrarCheckin(Usuario usuario, Long pontoId) {
        LocalDate hoje = hoje();
        if (eventos.existsByUsuarioIdAndTipoAndReferenciaIdAndDia(usuario.getId(), TipoEvento.CHECKIN, pontoId, hoje)) {
            throw new RegraNegocioException("Você já fez check-in neste ponto hoje");
        }
        if (eventos.countByUsuarioIdAndTipoAndDia(usuario.getId(), TipoEvento.CHECKIN, hoje) >= limiteCheckinsPorDia) {
            throw new RegraNegocioException(
                    "Você já fez %d check-ins hoje, o limite diário. Volte amanhã!".formatted(limiteCheckinsPorDia));
        }
        return registrar(usuario, TipoEvento.CHECKIN, pontoId);
    }

    /** Uma sugestão só pontua uma vez, mesmo que seja recusada e aprovada de novo depois. */
    public boolean sugestaoJaPontuada(Long pontoId) {
        return eventos.existsByTipoAndReferenciaId(TipoEvento.SUGESTAO_APROVADA, pontoId);
    }

    @Transactional
    public EventoPontuacao registrar(Usuario usuario, TipoEvento tipo, Long referenciaId) {
        return eventos.save(new EventoPontuacao(usuario, tipo, referenciaId, hoje()));
    }

    public List<EventoPontuacao> recentes(Long usuarioId, int limite) {
        return eventos.findByUsuarioIdOrderByCriadoEmDesc(usuarioId, Limit.of(limite));
    }

    public List<PosicaoRanking> ranking(String zona, int limite) {
        return eventos.ranking(zona, Limit.of(limite));
    }

    /** "Hoje" no fuso de Teresina, para os limites diários não virarem às 21h. */
    private LocalDate hoje() {
        return LocalDate.now(fuso);
    }
}
