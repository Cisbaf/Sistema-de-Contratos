package contratos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Onde os arquivos dos anexos ficam gravados (prefixo {@code attachments.*}).
 * Pela variável de ambiente: {@code ATTACHMENTS_STORAGE_DIR}.
 * <p>
 * A pasta fica FORA do banco e FORA da imagem Docker: em produção é um bind mount do servidor
 * (ex.: {@code ./storage/anexos:/app/storage/anexos}). Caminho relativo vale a partir do diretório de trabalho da
 * JVM; no IntelliJ prefira o caminho absoluto, para não criar a pasta em dois lugares.
 *
 * @param storageDir pasta-base dos anexos; em branco derruba a subida com mensagem clara
 */
@ConfigurationProperties(prefix = "attachments")
public record AttachmentStorageProperties(@DefaultValue("storage/anexos") String storageDir) {

    public AttachmentStorageProperties {
        if (storageDir == null || storageDir.isBlank()) {
            throw new IllegalArgumentException(
                    "attachments.storage-dir (ATTACHMENTS_STORAGE_DIR) não pode ficar em branco: "
                            + "informe a pasta onde os anexos serão gravados.");
        }
        storageDir = storageDir.trim();
    }
}
