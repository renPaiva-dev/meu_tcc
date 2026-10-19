package br.com.reciclathe.usuario;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdAndAtivoTrue(Long id);

    long countByPerfil(Perfil perfil);

    Page<Usuario> findAllByOrderByCriadoEmDesc(Pageable pagina);

    /** Trava a linha do usuário até o fim da transação, serializando ações simultâneas dele. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> findParaAtualizar(@Param("id") Long id);
}
