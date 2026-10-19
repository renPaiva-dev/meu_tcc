package br.com.reciclathe.identificacao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentificacaoRepository extends JpaRepository<Identificacao, Long> {

    List<Identificacao> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Limit limite);

    /**
     * Identificações com feedback, para o painel. Com {@code todos=false}, filtra pelo valor de {@code correto};
     * com {@code comFoto=true}, só as que têm foto compartilhada com consentimento (insumo para retreino).
     */
    @EntityGraph(attributePaths = {"materialPrevisto", "materialCorrigido"})
    @Query("""
            SELECT i FROM Identificacao i
            WHERE i.feedbackCorreto IS NOT NULL
              AND (:todos = true OR i.feedbackCorreto = :correto)
              AND (:comFoto = false OR i.fotoArquivo IS NOT NULL)
            ORDER BY i.feedbackEm DESC
            """)
    Page<Identificacao> buscarFeedbacks(@Param("todos") boolean todos, @Param("correto") boolean correto,
                                        @Param("comFoto") boolean comFoto, Pageable pagina);

    /** Trava a identificação: dois feedbacks simultâneos não passam juntos pela verificação "já tem feedback". */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Identificacao i WHERE i.id = :id")
    Optional<Identificacao> findParaAtualizar(@Param("id") Long id);

    @Query("SELECT i.fotoArquivo FROM Identificacao i WHERE i.usuario.id = :usuarioId AND i.fotoArquivo IS NOT NULL")
    List<String> findFotosDoUsuario(@Param("usuarioId") Long usuarioId);

    /** Desvincula o usuário e apaga as fotos (dado pessoal); mantém só os dados agregáveis. */
    @Modifying
    @Query("UPDATE Identificacao i SET i.usuario = null, i.fotoArquivo = null WHERE i.usuario.id = :usuarioId")
    void anonimizarDoUsuario(@Param("usuarioId") Long usuarioId);
}
