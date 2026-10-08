package br.com.reciclathe.ponto;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de um ponto de coleta, usados tanto na sugestão pelo app (multipart) quanto no
 * formulário do painel. Coordenadas limitadas a um retângulo que cobre o município de Teresina.
 */
public record DadosPonto(
        @NotBlank @Size(max = 160) String nome,
        @NotNull TipoPonto tipo,
        @NotBlank @Size(max = 255) String endereco,
        @NotNull @DecimalMin(value = "-5.60", message = "fora de Teresina") @DecimalMax(value = "-4.75", message = "fora de Teresina") Double latitude,
        @NotNull @DecimalMin(value = "-43.05", message = "fora de Teresina") @DecimalMax(value = "-42.55", message = "fora de Teresina") Double longitude,
        @Size(max = 255) String horario,
        @Size(max = 30) String telefone,
        @Size(max = 500) String fonte,
        @NotEmpty List<String> materiais) {
}
