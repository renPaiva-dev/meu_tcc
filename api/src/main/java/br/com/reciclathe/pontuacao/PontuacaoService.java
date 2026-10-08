package br.com.reciclathe.pontuacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.reciclathe.config.ReciclatheProperties;
import br.com.reciclathe.usuario.Usuario;

@Service
public class PontuacaoService {

    /** Feedbacks pontuados por dia; acima disso o feedback é aceito, mas não rende pontos. */
    public static final int LIMITE_FEEDBACK_DIARIO = 5;

    private final EventoPontuacaoRepository eventos;
    private final ZoneId fuso;

    public PontuacaoService(EventoPontuacaoRepository eventos, ReciclatheProperties props) {
        this.eventos = eventos;
        this.fuso = props.fusoHorario();
    }

    public long total(Long usuarioId) {
        return eventos.somarPontos(usuarioId);
    }

    @Transactional
    public Optional<EventoPontuacao> registrarFeedback(Usuario usuario, Long identificacaoId) {
        long hoje = eventos.countByUsuarioIdAndTipoAndCriadoEmGreaterThanEqual(
                usuario.getId(), TipoEvento.FEEDBACK, inicioDoDia());
        if (hoje >= LIMITE_FEEDBACK_DIARIO) {
            return Optional.empty();
        }
        return Optional.of(eventos.save(new EventoPontuacao(usuario, TipoEvento.FEEDBACK, identificacaoId)));
    }

    public boolean fezCheckinHoje(Long usuarioId, Long pontoId) {
        return eventos.existsByUsuarioIdAndTipoAndReferenciaIdAndCriadoEmGreaterThanEqual(
                usuarioId, TipoEvento.CHECKIN, pontoId, inicioDoDia());
    }

    @Transactional
    public EventoPontuacao registrar(Usuario usuario, TipoEvento tipo, Long referenciaId) {
        return eventos.save(new EventoPontuacao(usuario, tipo, referenciaId));
    }

    public List<EventoPontuacao> recentes(Long usuarioId, int limite) {
        return eventos.findByUsuarioIdOrderByCriadoEmDesc(usuarioId, Limit.of(limite));
    }

    public List<PosicaoRanking> ranking(String zona, int limite) {
        return eventos.ranking(zona, Limit.of(limite));
    }

    /** "Hoje" no fuso de Teresina, para os limites diários não virarem às 21h. */
    private Instant inicioDoDia() {
        return LocalDate.now(fuso).atStartOfDay(fuso).toInstant();
    }
}
