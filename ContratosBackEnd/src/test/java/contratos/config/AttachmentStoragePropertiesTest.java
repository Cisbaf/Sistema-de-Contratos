package contratos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ANX-10.1 — leitura de {@code attachments.storage-dir}: padrão, valor informado e valor em branco. */
class AttachmentStoragePropertiesTest {

    private static AttachmentStorageProperties bind(Map<String, String> valores) {
        return new Binder(new MapConfigurationPropertySource(valores))
                .bindOrCreate("attachments", AttachmentStorageProperties.class);
    }

    @Test
    void semConfiguracaoUsaStorageAnexosRelativoAoDiretorioDeTrabalho() {
        assertThat(bind(Map.of()).storageDir()).isEqualTo("storage/anexos");
    }

    @Test
    void usaOValorInformadoSemEspacosNasPontas() {
        assertThat(bind(Map.of("attachments.storage-dir", "  /app/storage/anexos  ")).storageDir())
                .isEqualTo("/app/storage/anexos");
    }

    @Test
    void valorEmBrancoDerrubaASubidaComMensagemClara() {
        assertThatThrownBy(() -> bind(Map.of("attachments.storage-dir", "   ")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "attachments.storage-dir (ATTACHMENTS_STORAGE_DIR) não pode ficar em branco: "
                                + "informe a pasta onde os anexos serão gravados.");
    }

    @Test
    void construtorDiretoTambemRecusaNuloEBranco() {
        assertThatThrownBy(() -> new AttachmentStorageProperties(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AttachmentStorageProperties("")).isInstanceOf(IllegalArgumentException.class);
    }
}
