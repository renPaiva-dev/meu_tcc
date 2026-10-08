package br.com.reciclathe.usuario;

public record UsuarioResponse(Long id, String nome, String email, String zona, long pontos) {

    public static UsuarioResponse de(Usuario u, long pontos) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(),
                u.getZona() == null ? null : u.getZona().getCodigo(), pontos);
    }
}
