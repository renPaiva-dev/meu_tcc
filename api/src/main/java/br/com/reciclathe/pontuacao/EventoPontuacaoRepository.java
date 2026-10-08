package br.com.reciclathe.pontuacao;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoPontuacaoRepository extends JpaRepository<EventoPontuacao, Long> {

    @Query("SELECT COALESCE(SUM(e.pontos), 0) FROM EventoPontuacao e WHERE e.usuario.id = :usuarioId")
    long somarPontos(@Param("usuarioId") Long usuarioId);

    long countByUsuarioIdAndTipoAndCriadoEmGreaterThanEqual(Long usuarioId, TipoEvento tipo, Instant desde);

    boolean existsByUsuarioIdAndTipoAndReferenciaIdAndCriadoEmGreaterThanEqual(
            Long usuarioId, TipoEvento tipo, Long referenciaId, Instant desde);

    List<EventoPontuacao> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Limit limite);

    @Query("""
            SELECT new br.com.reciclathe.pontuacao.PosicaoRanking(u.id, u.nome, SUM(e.pontos))
            FROM EventoPontuacao e JOIN e.usuario u LEFT JOIN u.zona z
            WHERE (:zona IS NULL OR z.codigo = :zona)
            GROUP BY u.id, u.nome
            ORDER BY SUM(e.pontos) DESC, u.id ASC
            """)
    List<PosicaoRanking> ranking(@Param("zona") String zona, Limit limite);
}
