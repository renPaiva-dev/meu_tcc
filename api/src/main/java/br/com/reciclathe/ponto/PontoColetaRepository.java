package br.com.reciclathe.ponto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Os @EntityGraph trazem numa só consulta o que a resposta vai usar, evitando N+1 e LazyInitialization. */
public interface PontoColetaRepository extends JpaRepository<PontoColeta, Long> {

    @EntityGraph(attributePaths = "materiais")
    List<PontoColeta> findByStatusOrderByNomeAsc(StatusPonto status);

    /** Lista do painel: os materiais vêm em lote (@BatchSize) dentro da transação do serviço. */
    @EntityGraph(attributePaths = {"sugeridoPor", "revisadoPor"})
    Page<PontoColeta> findByStatusOrderByNomeAsc(StatusPonto status, Pageable pagina);

    @EntityGraph(attributePaths = {"sugeridoPor", "revisadoPor"})
    Page<PontoColeta> findAllByOrderByStatusAscNomeAsc(Pageable pagina);

    @EntityGraph(attributePaths = {"materiais", "sugeridoPor", "revisadoPor"})
    @Query("SELECT p FROM PontoColeta p WHERE p.id = :id")
    Optional<PontoColeta> findComDetalhesById(@Param("id") Long id);

    @EntityGraph(attributePaths = "materiais")
    @Query("SELECT p FROM PontoColeta p WHERE p.id IN :ids")
    List<PontoColeta> findComMateriaisByIdIn(@Param("ids") Collection<Long> ids);

    long countByStatus(StatusPonto status);

    long countBySugeridoPorIdAndStatus(Long usuarioId, StatusPonto status);

    @EntityGraph(attributePaths = "materiais")
    Optional<PontoColeta> findByOsmTipoAndOsmId(String osmTipo, Long osmId);

    @Query("SELECT p.fotoArquivo FROM PontoColeta p WHERE p.sugeridoPor.id = :usuarioId AND p.fotoArquivo IS NOT NULL")
    List<String> findFotosSugeridasPor(@Param("usuarioId") Long usuarioId);

    /** Exclusão de conta: o ponto fica, mas perde o vínculo com o autor e a foto que ele enviou. */
    @Modifying
    @Query("UPDATE PontoColeta p SET p.sugeridoPor = null, p.fotoArquivo = null WHERE p.sugeridoPor.id = :usuarioId")
    void desvincularSugestoesDe(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query(value = "INSERT INTO osm_ignorado (osm_tipo, osm_id) VALUES (:tipo, :id) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void ignorarOsm(@Param("tipo") String osmTipo, @Param("id") Long osmId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM osm_ignorado WHERE osm_tipo = :tipo AND osm_id = :id)",
            nativeQuery = true)
    boolean osmIgnorado(@Param("tipo") String osmTipo, @Param("id") Long osmId);
}
