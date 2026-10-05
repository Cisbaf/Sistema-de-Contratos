package contratos.service;

import contratos.config.AttachmentStorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ANX-10.1 — gravação em disco, comprimida, com caminho seguro e apagamento atrelado à transação. Sem Spring. */
class AttachmentStorageTest {

    @TempDir
    Path base;

    private AttachmentStorage storage;

    @BeforeEach
    void criarStorage() {
        storage = new AttachmentStorage(new AttachmentStorageProperties(base.toString()));
    }

    @AfterEach
    void limparTransacaoSimulada() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static byte[] textoRepetido(int vezes) {
        return "Termo Aditivo ao contrato — cláusula primeira. ".repeat(vezes).getBytes(StandardCharsets.UTF_8);
    }

    private long arquivosNaPasta() throws IOException {
        try (Stream<Path> todos = Files.walk(base)) {
            return todos.filter(Files::isRegularFile).count();
        }
    }

    // ---------- ida e volta, formato e compressão ----------

    @Test
    void gravarELerDevolveOsMesmosBytes() throws IOException {
        byte[] original = new byte[200_000];
        new Random(42).nextBytes(original); // conteúdo incompressível, com todos os valores de byte

        String caminho = storage.save(7L, original);

        assertThat(storage.read(caminho)).isEqualTo(original);
    }

    @Test
    void textoComAcentosVoltaIntacto() throws IOException {
        byte[] original = "Prorrogação — vigência até 31/12/2027, ç ã é ü".getBytes(StandardCharsets.UTF_8);

        assertThat(storage.read(storage.save(1L, original))).isEqualTo(original);
    }

    @Test
    void conteudoVazioVoltaVazio() throws IOException {
        assertThat(storage.read(storage.save(1L, new byte[0]))).isEmpty();
    }

    @Test
    void oArquivoNoDiscoEhGzipEMenorQueOOriginalQuandoComprime() throws IOException {
        byte[] original = textoRepetido(5_000);

        String caminho = storage.save(3L, original);

        byte[] noDisco = Files.readAllBytes(base.resolve(caminho));
        assertThat(noDisco[0]).isEqualTo((byte) 0x1f); // assinatura do gzip
        assertThat(noDisco[1]).isEqualTo((byte) 0x8b);
        assertThat(noDisco.length).isLessThan(original.length / 10);
        assertThat(noDisco).isNotEqualTo(original);
    }

    @Test
    void caminhoTemOFormatoContratoBarraUuidPontoGz() throws IOException {
        String caminho = storage.save(42L, textoRepetido(3));

        assertThat(caminho).matches("42/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.gz");
        assertThat(base.resolve(caminho)).isRegularFile();
    }

    @Test
    void duasGravacoesDoMesmoConteudoGeramCaminhosDiferentes() throws IOException {
        byte[] conteudo = textoRepetido(3);

        assertThat(storage.save(1L, conteudo)).isNotEqualTo(storage.save(1L, conteudo));
    }

    @Test
    void cadaContratoTemASuaPasta() throws IOException {
        String a = storage.save(1L, textoRepetido(2));
        String b = storage.save(2L, textoRepetido(2));

        assertThat(a).startsWith("1/");
        assertThat(b).startsWith("2/");
        assertThat(base.resolve("1")).isDirectory();
        assertThat(base.resolve("2")).isDirectory();
    }

    @Test
    void depoisDeGravarNaoSobraArquivoTemporario() throws IOException {
        storage.save(5L, textoRepetido(10));

        assertThat(arquivosNaPasta()).isEqualTo(1);
        try (Stream<Path> todos = Files.walk(base)) {
            assertThat(todos.filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
    }

    @Test
    void gravarComIdOuConteudoNulosEhRecusado() {
        assertThatThrownBy(() -> storage.save(null, new byte[]{1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.save(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(base.toFile().list()).isEmpty();
    }

    @Test
    void seAPastaDoContratoEstaOcupadaPorUmArquivoGravarFalhaESemLixo() throws IOException {
        Files.writeString(base.resolve("9"), "sou um arquivo, não uma pasta");

        assertThatThrownBy(() -> storage.save(9L, textoRepetido(2))).isInstanceOf(IOException.class);
        assertThat(arquivosNaPasta()).isEqualTo(1); // só o arquivo que o teste criou
    }

    // ---------- leitura ----------

    @Test
    void lerArquivoInexistenteFalhaComNoSuchFile() {
        assertThatThrownBy(() -> storage.read("1/00000000-0000-0000-0000-000000000000.gz"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void lerArquivoQueNaoEhGzipFalhaEmVezDeDevolverLixo() throws IOException {
        Files.createDirectories(base.resolve("1"));
        Files.writeString(base.resolve("1/quebrado.gz"), "isto não é gzip");

        assertThatThrownBy(() -> storage.read("1/quebrado.gz")).isInstanceOf(ZipException.class);
    }

    // ---------- caminho seguro ----------

    @ParameterizedTest(name = "caminho recusado: \"{0}\"")
    @ValueSource(strings = {
            "../fora.gz",
            "1/../../fora.gz",
            "1/../..",
            "..",
            ".",
            "/etc/passwd",
            "1\\..\\fora.gz",
            "1/a\0b.gz",
            "",
            "   "
    })
    void caminhosPerigososOuInvalidosSaoRecusadosEmTodasAsOperacoes(String perigoso) {
        iniciarTransacaoSimulada(); // os helpers transacionais exigem transação; a validação do caminho vem depois
        assertThatThrownBy(() -> storage.read(perigoso)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(perigoso)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deleteAfterCommit(perigoso)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deleteOnRollback(perigoso)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caminhoNuloEhRecusado() {
        assertThatThrownBy(() -> storage.read(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arquivoForaDaPastaBaseNuncaEhApagadoPorCaminhoComPontoPonto() throws IOException {
        // arquivo real ao lado da pasta-base: "../nome" aponta exatamente para ele
        Path fora = Files.createTempFile(base.getParent(), "fora-da-base-", ".txt");
        try {
            String caminho = "../" + fora.getFileName();

            assertThatThrownBy(() -> storage.delete(caminho)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.read(caminho)).isInstanceOf(IllegalArgumentException.class);
            assertThat(fora).exists();
        } finally {
            Files.deleteIfExists(fora);
        }
    }

    // ---------- existe ----------

    @Test
    void existsDizSeOArquivoEstaEmDisco() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));

        assertThat(storage.exists(caminho)).isTrue();
        storage.delete(caminho);
        assertThat(storage.exists(caminho)).isFalse();
    }

    @Test
    void existsNaoConfundePastaComArquivoERecusaCaminhoPerigoso() throws IOException {
        storage.save(1L, textoRepetido(2)); // cria a pasta "1"

        assertThat(storage.exists("1")).as("pasta não é arquivo").isFalse();
        assertThatThrownBy(() -> storage.exists("../fora.gz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.exists(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- apagar ----------

    @Test
    void apagarRemoveOArquivoEDevolveTrue() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));

        assertThat(storage.delete(caminho)).isTrue();
        assertThat(base.resolve(caminho)).doesNotExist();
    }

    @Test
    void apagarDuasVezesNaoDaErro() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));

        assertThat(storage.delete(caminho)).isTrue();
        assertThat(storage.delete(caminho)).isFalse();
    }

    @Test
    void apagarUmArquivoNaoMexeNosOutros() throws IOException {
        String a = storage.save(1L, textoRepetido(2));
        String b = storage.save(1L, textoRepetido(3));

        storage.delete(a);

        assertThat(base.resolve(b)).isRegularFile();
        assertThat(storage.read(b)).isEqualTo(textoRepetido(3));
    }

    @Test
    void falhaDeDiscoAoApagarEhRegistradaENaoLancada() throws IOException {
        // pasta não vazia: deleteIfExists lança DirectoryNotEmptyException, que o storage engole e registra
        Files.createDirectories(base.resolve("1/sub"));
        Files.writeString(base.resolve("1/sub/arquivo.txt"), "x");

        assertThatCode(() -> assertThat(storage.delete("1/sub")).isFalse()).doesNotThrowAnyException();
        assertThat(base.resolve("1/sub/arquivo.txt")).exists();
    }

    // ---------- apagar atrelado à transação ----------

    private void iniciarTransacaoSimulada() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private List<TransactionSynchronization> sincronizacoes() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    @Test
    void deleteAfterCommitSoApagaDepoisDoCommit() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));
        iniciarTransacaoSimulada();

        storage.deleteAfterCommit(caminho);

        assertThat(base.resolve(caminho)).as("antes do commit o arquivo continua lá").exists();
        sincronizacoes().forEach(TransactionSynchronization::afterCommit);
        assertThat(base.resolve(caminho)).as("depois do commit foi apagado").doesNotExist();
    }

    @Test
    void deleteAfterCommitNaoApagaSeATransacaoForDesfeita() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));
        iniciarTransacaoSimulada();

        storage.deleteAfterCommit(caminho);
        // rollback: o Spring chama só afterCompletion(ROLLED_BACK), nunca afterCommit
        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(base.resolve(caminho)).exists();
    }

    @Test
    void deleteOnRollbackApagaQuandoATransacaoEhDesfeita() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));
        iniciarTransacaoSimulada();

        storage.deleteOnRollback(caminho);
        assertThat(base.resolve(caminho)).exists();
        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(base.resolve(caminho)).doesNotExist();
    }

    @Test
    void deleteOnRollbackMantemOArquivoQuandoATransacaoEhConfirmada() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));
        iniciarTransacaoSimulada();

        storage.deleteOnRollback(caminho);
        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        assertThat(base.resolve(caminho)).exists();
    }

    @Test
    void deleteOnRollbackTambemApagaQuandoOResultadoEhDesconhecido() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));
        iniciarTransacaoSimulada();

        storage.deleteOnRollback(caminho);
        sincronizacoes().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));

        assertThat(base.resolve(caminho)).doesNotExist();
    }

    @Test
    void helpersTransacionaisExigemTransacaoAtiva() throws IOException {
        String caminho = storage.save(1L, textoRepetido(2));

        assertThatThrownBy(() -> storage.deleteAfterCommit(caminho))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exige uma transação ativa");
        assertThatThrownBy(() -> storage.deleteOnRollback(caminho))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exige uma transação ativa");
        assertThat(base.resolve(caminho)).exists();
    }

    @Test
    void falhaAoApagarDentroDoCommitNaoPropagaParaQuemConfirmou() throws IOException {
        Files.createDirectories(base.resolve("1/sub"));
        Files.writeString(base.resolve("1/sub/arquivo.txt"), "x");
        iniciarTransacaoSimulada();

        storage.deleteAfterCommit("1/sub");

        assertThatCode(() -> sincronizacoes().forEach(TransactionSynchronization::afterCommit))
                .doesNotThrowAnyException();
    }

    // ---------- inicialização ----------

    @Test
    void criaAPastaBaseInclusiveAninhadaQuandoNaoExiste() {
        Path aninhada = base.resolve("a/b/c/anexos");

        new AttachmentStorage(new AttachmentStorageProperties(aninhada.toString()));

        assertThat(aninhada).isDirectory();
    }

    @Test
    void naoSobeSeAPastaBaseEhNaVerdadeUmArquivo() throws IOException {
        Path arquivo = base.resolve("nao-sou-pasta");
        Files.writeString(arquivo, "x");

        assertThatThrownBy(() -> new AttachmentStorage(new AttachmentStorageProperties(arquivo.toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attachments.storage-dir");
    }

    @Test
    void caminhoRelativoViraAbsolutoNaSubida() {
        // não cria nada fora do TempDir: só confere que a base é resolvida a partir do diretório de trabalho
        Path relativa = Path.of("target", "anexos-teste-relativo-" + System.nanoTime());
        try {
            new AttachmentStorage(new AttachmentStorageProperties(relativa.toString()));

            assertThat(relativa.toAbsolutePath()).isDirectory();
        } finally {
            relativa.toFile().delete();
        }
    }
}
