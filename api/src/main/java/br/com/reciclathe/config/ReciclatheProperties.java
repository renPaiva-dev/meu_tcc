package br.com.reciclathe.config;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("reciclathe")
public record ReciclatheProperties(
        @NotNull ZoneId fusoHorario,
        @NotNull Jwt jwt,
        @NotNull Fotos fotos,
        @NotNull Admin admin,
        @NotNull Osm osm) {

    /** O segredo assina os tokens com HS256 e precisa de pelo menos 256 bits. */
    public record Jwt(@NotBlank @Size(min = 32) String segredo, @NotNull Duration validade) {
    }

    public record Fotos(@NotNull Path diretorio) {
    }

    /**
     * Importação do OpenStreetMap via API Overpass.
     *
     * @param overpassUrls servidores tentados em ordem (os públicos ficam sobrecarregados com frequência)
     * @param municipio    nome da área administrativa (admin_level 8) consultada
     */
    public record Osm(@NotEmpty List<String> overpassUrls, @NotBlank String municipio, @NotNull Duration timeout) {
    }

    /** Administrador criado na inicialização, se ainda não existir. Senha vazia desativa a criação. */
    public record Admin(String nome, String email, String senha) {
    }
}
