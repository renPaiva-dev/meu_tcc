package br.com.reciclathe.comum;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.config.ReciclatheProperties;

/**
 * Guarda as fotos enviadas pelos usuários em disco, fora da pasta pública.
 * Os nomes são sempre gerados aqui (UUID), o que impede path traversal na leitura.
 */
@Component
public class ArmazenamentoFotos {

    private static final Map<String, String> EXTENSOES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");
    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");

    private final Path diretorio;

    public ArmazenamentoFotos(ReciclatheProperties props) {
        this.diretorio = props.fotos().diretorio().toAbsolutePath().normalize();
    }

    public String salvar(MultipartFile foto) {
        String extensao = EXTENSOES.get(foto.getContentType());
        if (extensao == null) {
            throw new RegraNegocioException("A foto deve ser JPEG, PNG ou WebP");
        }
        String nome = UUID.randomUUID() + extensao;
        try (InputStream entrada = foto.getInputStream()) {
            Files.createDirectories(diretorio);
            Files.copy(entrada, diretorio.resolve(nome));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao salvar a foto", e);
        }
        return nome;
    }

    public Resource carregar(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
        Path arquivo = diretorio.resolve(nome);
        if (!Files.exists(arquivo)) {
            throw new RecursoNaoEncontradoException("Foto não encontrada");
        }
        return new PathResource(arquivo);
    }

    public void excluir(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(diretorio.resolve(nome));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao excluir a foto", e);
        }
    }
}
