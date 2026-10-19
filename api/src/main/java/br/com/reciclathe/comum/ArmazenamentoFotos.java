package br.com.reciclathe.comum;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import br.com.reciclathe.config.ReciclatheProperties;

/**
 * Guarda as fotos enviadas pelos usuários em disco, fora da pasta pública.
 * Os nomes são sempre gerados aqui (UUID), o que impede path traversal na leitura.
 *
 * <p>O disco não participa da transação do banco, então as operações se alinham a ela:
 * a foto gravada é apagada se a transação for desfeita, e a exclusão só acontece depois do commit.
 */
@Component
public class ArmazenamentoFotos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoFotos.class);

    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final Path diretorio;
    private final long tamanhoMaximo;

    public ArmazenamentoFotos(ReciclatheProperties props) {
        this.diretorio = props.fotos().diretorio().toAbsolutePath().normalize();
        this.tamanhoMaximo = props.fotos().tamanhoMaximo().toBytes();
    }

    public String salvar(MultipartFile foto) {
        if (foto.getSize() > tamanhoMaximo) {
            throw new RegraNegocioException("A foto deve ter no máximo %d MB".formatted(tamanhoMaximo / (1024 * 1024)));
        }
        // O tipo vem dos primeiros bytes do arquivo, e não do Content-Type, que o cliente pode forjar
        String extensao;
        try (InputStream entrada = foto.getInputStream()) {
            extensao = extensao(entrada.readNBytes(12));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a foto", e);
        }
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
        apagarSeTransacaoForDesfeita(nome);
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

    /**
     * Apaga as fotos depois do commit da transação atual (ou na hora, se não houver transação).
     * Assim, se a exclusão no banco falhar, os registros não ficam apontando para arquivos apagados.
     */
    public void excluirAposCommit(Collection<String> nomes) {
        List<String> validos = nomes.stream().filter(n -> n != null && NOME_VALIDO.matcher(n).matches()).toList();
        if (validos.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            validos.forEach(this::apagar);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                validos.forEach(ArmazenamentoFotos.this::apagar);
            }
        });
    }

    public void excluirAposCommit(String nome) {
        excluirAposCommit(Arrays.asList(nome));
    }

    private void apagarSeTransacaoForDesfeita(String nome) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    apagar(nome);
                }
            }
        });
    }

    /** Roda depois do commit: uma falha aqui só gera aviso, porque o banco já foi alterado. */
    private void apagar(String nome) {
        try {
            Files.deleteIfExists(diretorio.resolve(nome));
        } catch (IOException e) {
            log.warn("Não foi possível apagar a foto {}: {}", nome, e.getMessage());
        }
    }

    /** Reconhece JPEG, PNG e WebP pela assinatura ("magic bytes") do início do arquivo. */
    static String extensao(byte[] inicio) {
        if (inicio.length >= 3 && (inicio[0] & 0xFF) == 0xFF && (inicio[1] & 0xFF) == 0xD8 && (inicio[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (inicio.length >= 8 && Arrays.equals(Arrays.copyOf(inicio, 8), ASSINATURA_PNG)) {
            return ".png";
        }
        if (inicio.length >= 12
                && "RIFF".equals(new String(inicio, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(inicio, 8, 4, StandardCharsets.US_ASCII))) {
            return ".webp";
        }
        return null;
    }
}
