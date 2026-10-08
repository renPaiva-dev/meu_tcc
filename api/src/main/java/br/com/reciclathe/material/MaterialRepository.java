package br.com.reciclathe.material;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, String> {

    List<Material> findAllByOrderByOrdemAsc();
}
