package br.com.reciclathe.zona;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaRepository extends JpaRepository<Zona, String> {

    List<Zona> findAllByOrderByNomeAsc();
}
