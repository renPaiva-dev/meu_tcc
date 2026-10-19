package br.com.reciclathe.pontuacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoPontuacaoRepository extends JpaRepository<EventoPontuacao, Long> {

    @Query("SELECT COALESCE(SUM(e.pontos), 0) FROM EventoPontuacao e WHERE e.usuario.id = :usuarioId")
    long somarPontos(@Param("usuarioId") Long usuarioId);

    long countByUsuarioIdAndTipoAndDia(Long usuarioId, TipoEvento tipo, LocalDate dia);

    boolean existsByUsuarioIdAndTipoAndReferenciaIdAndDia(Long usuarioId, TipoEvento tipo, Long referenciaId,
                                                          LocalDate dia);

    boolean existsByTipoAndReferenciaId(TipoEvento tipo, Long referenciaId);

    List<EventoPontuacao> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Limit limite);

    /** Só cidadãos ativos concorrem: administradores e contas bloqueadas ficam de fora. */
    @Query("""
            SELECT new br.com.reciclathe.pontuacao.PosicaoRanking(u.id, u.nome, SUM(e.pontos))
            FROM EventoPontuacao e JOIN e.usuario u LEFT JOIN u.zona z
            WHERE u.perfil = br.com.reciclathe.usuario.Perfil.CIDADAO
              AND u.ativo = true
              AND (:zona IS NULL OR z.codigo = :zona)
            GROUP BY u.id, u.nome
            ORDER BY SUM(e.pontos) DESC, u.id ASC
            """)
    List<PosicaoRanking> ranking(@Param("zona") String zona, Limit limite);
}
