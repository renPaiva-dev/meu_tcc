package br.com.reciclathe.identificacao;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentificacaoRepository extends JpaRepository<Identificacao, Long> {

    List<Identificacao> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Limit limite);

    List<Identificacao> findByFeedbackCorretoFalseOrderByFeedbackEmDesc(Limit limite);

    @Query("SELECT i.fotoArquivo FROM Identificacao i WHERE i.usuario.id = :usuarioId AND i.fotoArquivo IS NOT NULL")
    List<String> findFotosDoUsuario(@Param("usuarioId") Long usuarioId);

    /** Desvincula o usuário e apaga as fotos (dado pessoal); mantém só os dados agregáveis. */
    @Modifying
    @Query("UPDATE Identificacao i SET i.usuario = null, i.fotoArquivo = null WHERE i.usuario.id = :usuarioId")
    void anonimizarDoUsuario(@Param("usuarioId") Long usuarioId);
}
