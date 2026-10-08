package br.com.reciclathe.ponto;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PontoColetaRepository extends JpaRepository<PontoColeta, Long> {

    List<PontoColeta> findByStatusOrderByNomeAsc(StatusPonto status);

    List<PontoColeta> findAllByOrderByStatusAscNomeAsc();

    long countByStatus(StatusPonto status);

    Optional<PontoColeta> findByOsmTipoAndOsmId(String osmTipo, Long osmId);
}
