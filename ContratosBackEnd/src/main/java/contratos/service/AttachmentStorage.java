package contratos.service;

import contratos.config.AttachmentStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Guarda o conteúdo dos anexos em disco, comprimido (gzip), fora do banco (ANX-10).
 * <p>
 * O banco guarda só o caminho RELATIVO devolvido por {@link #save}, no formato {@code {contractId}/{uuid}.gz}. O nome
 * enviado pelo usuário nunca entra no caminho, e todo caminho recebido é conferido para não sair da pasta-base.
 * <p>
 * O disco não participa da transação do banco, então quem chama deve combinar: gravar o arquivo novo ANTES;
 * {@link #deleteOnRollback} para apagá-lo se a transação for desfeita; {@link #deleteAfterCommit} para apagar um
 * arquivo antigo só depois que o banco confirmou. Se o processo morrer entre a gravação e o commit, sobra um arquivo
 * órfão (e, no meio de uma gravação, um {@code upload-*.tmp}); isso é aceito e inofensivo.
 * <p>
 * Os arquivos nascem com permissão só do dono ({@code rw-------}, padrão de {@code Files.createTempFile}): o processo
 * de backup precisa rodar com o mesmo usuário ou ser root.
 */
@Component
public class AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorage.class);
    private static final String EXTENSION = ".gz";

    private final Path root;

    public AttachmentStorage(AttachmentStorageProperties properties) {
        this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta de anexos " + root
                    + " (attachments.storage-dir): " + e, e);
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("A pasta de anexos " + root
                    + " (attachments.storage-dir) não é uma pasta com permissão de escrita.");
        }
        log.info("Anexos gravados em: {}", root);
    }

    /** Comprime e grava o conteúdo; devolve o caminho relativo a guardar no banco. */
    public String save(Long contractId, byte[] content) throws IOException {
        if (contractId == null) {
            throw new IllegalArgumentException("O id do contrato é obrigatório para gravar o anexo");
        }
        if (content == null) {
            throw new IllegalArgumentException("O conteúdo do anexo é obrigatório");
        }
        Path dir = root.resolve(contractId.toString());
        Files.createDirectories(dir);

        String relative = contractId + "/" + UUID.randomUUID() + EXTENSION;
        Path target = root.resolve(relative);

        // Grava num temporário na MESMA pasta e só então move: quem lê nunca vê um arquivo pela metade.
        Path temp = Files.createTempFile(dir, "upload-", ".tmp");
        try {
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temp))) {
                out.write(content);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
        return relative;
    }

    /** Lê e descomprime. Arquivo inexistente: {@code NoSuchFileException}; arquivo corrompido: {@code ZipException}. */
    public byte[] read(String storagePath) throws IOException {
        Path file = resolve(storagePath);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return in.readAllBytes();
        }
    }

    /** {@code true} se o arquivo existe em disco. Caminho inválido ou fora da pasta-base lança {@link IllegalArgumentException}. */
    public boolean exists(String storagePath) {
        return Files.isRegularFile(resolve(storagePath));
    }

    /**
     * Apaga o arquivo. {@code true} se existia e foi apagado; {@code false} se não existia ou se o disco recusou
     * (a falha de E/S é registrada no log, não lançada: arquivo sobrando é inofensivo, linha sem arquivo não).
     * Caminho inválido ou fora da pasta-base lança {@link IllegalArgumentException}.
     */
    public boolean delete(String storagePath) {
        Path file = resolve(storagePath);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Não foi possível apagar o anexo {}: {}", storagePath, e.toString());
            return false;
        }
    }

    /** Apaga o arquivo só depois que a transação atual for confirmada (troca de documento, remoção, exclusão). */
    public void deleteAfterCommit(String storagePath) {
        requireTransaction("deleteAfterCommit");
        resolve(storagePath); // valida agora, dentro da transação, em vez de só falhar depois do commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteSafely(storagePath);
            }
        });
    }

    /** Apaga o arquivo se a transação atual NÃO for confirmada (rollback), para não deixar órfão do upload. */
    public void deleteOnRollback(String storagePath) {
        requireTransaction("deleteOnRollback");
        resolve(storagePath);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteSafely(storagePath);
                }
            }
        });
    }

    private void deleteSafely(String storagePath) {
        try {
            delete(storagePath);
        } catch (RuntimeException e) {
            log.error("Falha ao apagar o anexo {}: {}", storagePath, e.toString());
        }
    }

    private static void requireTransaction(String operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(operation + " exige uma transação ativa (chame de um método @Transactional)");
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Não foi possível apagar o temporário {}: {}", file, e.toString());
        }
    }

    /** Converte o caminho relativo guardado no banco no caminho real, recusando o que sair da pasta-base. */
    private Path resolve(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("O caminho do anexo está vazio");
        }
        if (storagePath.indexOf('\\') >= 0 || storagePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Caminho de anexo inválido");
        }
        Path relative = Path.of(storagePath);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Caminho de anexo inválido: deve ser relativo à pasta de anexos");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Caminho de anexo fora da pasta de anexos");
        }
        return resolved;
    }
}
