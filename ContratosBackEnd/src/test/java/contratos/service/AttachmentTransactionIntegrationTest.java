package contratos.service;

import contratos.config.AttachmentStorageProperties;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractAttachment;
import contratos.domain.Sector;
import contratos.domain.enums.AttachmentType;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.AuditLogRepository;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.SectorRepository;
import contratos.repository.TechnicalOpinionRepository;
import contratos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ANX-10.2 — o disco não participa da transação do banco, então a regra é: arquivo novo gravado antes e apagado se
 * houver rollback; arquivo antigo apagado só depois do commit. Aqui as transações são abertas e desfeitas de verdade
 * (H2 isolado "attachtxtest"), chamando os services direto.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:attachtxtest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AttachmentTransactionIntegrationTest {

    private static final byte[] OLD = "%PDF-1.4 arquivo antigo".getBytes();
    private static final byte[] NEW = "%PDF-1.4 arquivo novo".getBytes();

    /** Corpo de transação que pode lançar exceção verificada (o service de aditivo declara IOException). */
    @FunctionalInterface
    interface Body<T> {
        T run() throws Exception;
    }

    static class ForcedRollback extends RuntimeException {
        ForcedRollback() {
            super("rollback forçado pelo teste");
        }
    }

    @Autowired ContractAttachmentService attachmentService;
    @Autowired ContractAmendmentService amendmentService;
    @Autowired ContractService contractService;
    @Autowired AttachmentStorage storage;
    @Autowired AttachmentStorageProperties storageProps;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired ContractRepository contracts;
    @Autowired ContractAttachmentRepository attachments;
    @Autowired ContractStatusHistoryRepository history;
    @Autowired InterestEmailConfirmationRepository interests;
    @Autowired TechnicalOpinionRepository opinions;
    @Autowired AuditLogRepository audit;
    @Autowired PlatformTransactionManager tm;

    TransactionTemplate tx;
    AppUser admin;
    Contract contract;
    Long contractId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(tm);

        audit.deleteAll();
        history.deleteAll();
        interests.deleteAll();
        opinions.deleteAll();
        attachments.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Setor Tx"));
        admin = users.save(new AppUser("admin.tx@test.local", "{noop}x", "Admin Tx", "admin.tx@test.local", null,
                setor, PerfilUsuario.ADMIN));
        AppUser fiscal = users.save(new AppUser("fiscal.tx@test.local", "{noop}x", "Fiscal Tx", "fiscal.tx@test.local",
                null, setor, PerfilUsuario.FISCAL));

        Contract c = new Contract();
        c.update("TX-001/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscal), "SEI-TX", 12);
        contract = contracts.save(c);
        contractId = contract.getId();
    }

    // ------------------------------------------------------------------ gravar (upload / registro do aditivo)

    @Test
    void transacaoConfirmadaMantemOsArquivosGravados() throws Exception {
        List<ContractAttachment> novos = inTx(() -> attachmentService.storeNew(
                List.of(pdf("a.pdf", NEW), pdf("b.pdf", OLD)), contract, admin, AttachmentType.GERAL));

        assertThat(novos).hasSize(2);
        assertThat(arquivosDoContrato()).hasSize(2);
        for (ContractAttachment novo : novos) {
            assertThat(storage.exists(novo.getStoragePath())).isTrue();
        }
    }

    @Test
    void transacaoDesfeitaApagaOsArquivosQueTinhamSidoGravados() throws Exception {
        List<String> caminhos = new java.util.ArrayList<>();

        assertThatThrownBy(() -> inTx(() -> {
            attachmentService.storeNew(List.of(pdf("a.pdf", NEW), pdf("b.pdf", OLD)), contract, admin, AttachmentType.GERAL)
                    .forEach(a -> caminhos.add(a.getStoragePath()));
            assertThat(caminhos).hasSize(2);
            assertThat(storage.exists(caminhos.get(0))).as("durante a transação já está em disco").isTrue();
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);

        assertThat(caminhos).hasSize(2);
        for (String caminho : caminhos) {
            assertThat(storage.exists(caminho)).as("rollback apaga o arquivo").isFalse();
        }
        assertThat(arquivosDoContrato()).isEmpty();
    }

    @Test
    void loteComArquivoInvalidoNaoGravaNenhumDosValidos() {
        assertThatThrownBy(() -> inTx(() -> attachmentService.storeNew(
                List.of(pdf("a.pdf", NEW), new MockMultipartFile("files", "nota.txt", "text/plain", "oi".getBytes())),
                contract, admin, AttachmentType.GERAL)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(arquivosDoContrato()).isEmpty();
    }

    // ------------------------------------------------------------------ trocar o documento do aditivo

    @Test
    void trocaDesfeitaMantemOArquivoAntigoEApagaONovo() throws Exception {
        Long aditivoId = aditivoComArquivo();
        String caminhoAntigo = attachments.findById(aditivoId).orElseThrow().getStoragePath();

        assertThatThrownBy(() -> inTx(() -> {
            amendmentService.replaceDocument(contractId, aditivoId, pdf("novo.pdf", NEW), admin);
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);

        ContractAttachment depois = attachments.findById(aditivoId).orElseThrow();
        assertThat(depois.getStoragePath()).as("a linha voltou ao estado anterior").isEqualTo(caminhoAntigo);
        assertThat(depois.getFileName()).isEqualTo("antigo.pdf");
        assertThat(storage.exists(caminhoAntigo)).as("o antigo NÃO é apagado se a troca foi desfeita").isTrue();
        assertThat(storage.read(caminhoAntigo)).isEqualTo(OLD);
        assertThat(arquivosDoContrato()).as("o novo, gravado antes, foi apagado: só o antigo restou").hasSize(1);
    }

    @Test
    void trocaConfirmadaApagaOAntigoSoDepoisDoCommit() throws Exception {
        Long aditivoId = aditivoComArquivo();
        String caminhoAntigo = attachments.findById(aditivoId).orElseThrow().getStoragePath();

        inTx(() -> {
            amendmentService.replaceDocument(contractId, aditivoId, pdf("novo.pdf", NEW), admin);
            assertThat(storage.exists(caminhoAntigo)).as("antes do commit o antigo ainda existe").isTrue();
            return null;
        });

        ContractAttachment depois = attachments.findById(aditivoId).orElseThrow();
        assertThat(storage.exists(caminhoAntigo)).as("depois do commit o antigo some").isFalse();
        assertThat(storage.read(depois.getStoragePath())).isEqualTo(NEW);
        assertThat(arquivosDoContrato()).hasSize(1);
    }

    // ------------------------------------------------------------------ remover anexo

    @Test
    void remocaoDesfeitaNaoApagaOArquivoENaoRemoveOAnexo() throws Exception {
        Long id = anexoComArquivo(AttachmentType.GERAL);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();

        assertThatThrownBy(() -> inTx(() -> {
            attachmentService.removeFiles(id, admin.getUsername());
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);

        ContractAttachment depois = attachments.findById(id).orElseThrow();
        assertThat(depois.isAtivo()).isTrue();
        assertThat(depois.getStoragePath()).isEqualTo(caminho);
        assertThat(storage.read(caminho)).isEqualTo(OLD);
    }

    @Test
    void remocaoConfirmadaApagaOArquivoSoDepoisDoCommit() throws Exception {
        Long id = anexoComArquivo(AttachmentType.GERAL);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();

        inTx(() -> {
            attachmentService.removeFiles(id, admin.getUsername());
            assertThat(storage.exists(caminho)).as("antes do commit o arquivo ainda existe").isTrue();
            return null;
        });

        assertThat(storage.exists(caminho)).isFalse();
        assertThat(attachments.findById(id).orElseThrow().isAtivo()).isFalse();
    }

    // ------------------------------------------------------------------ excluir contrato

    @Test
    void exclusaoDeContratoDesfeitaMantemContratoEArquivos() throws Exception {
        Long id = anexoComArquivo(AttachmentType.GERAL);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();

        assertThatThrownBy(() -> inTx(() -> {
            contractService.delete(contractId, admin);
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);

        assertThat(contracts.existsById(contractId)).isTrue();
        assertThat(attachments.existsById(id)).isTrue();
        assertThat(storage.read(caminho)).isEqualTo(OLD);
    }

    @Test
    void exclusaoDeContratoConfirmadaApagaOsArquivosSoDepoisDoCommit() throws Exception {
        Long id = anexoComArquivo(AttachmentType.TERMO_ADITIVO);
        String caminho = attachments.findById(id).orElseThrow().getStoragePath();

        inTx(() -> {
            contractService.delete(contractId, admin);
            assertThat(storage.exists(caminho)).as("antes do commit o arquivo ainda existe").isTrue();
            return null;
        });

        assertThat(contracts.existsById(contractId)).isFalse();
        assertThat(storage.exists(caminho)).isFalse();
    }

    // ------------------------------------------------------------------ apoio

    private <T> T inTx(Body<T> body) {
        return tx.execute(status -> {
            try {
                return body.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private Long aditivoComArquivo() throws IOException {
        return salvar("antigo.pdf", AttachmentType.TERMO_ADITIVO);
    }

    private Long anexoComArquivo(AttachmentType type) throws IOException {
        return salvar("antigo.pdf", type);
    }

    private Long salvar(String nome, AttachmentType type) throws IOException {
        return attachments.save(new ContractAttachment(contract, nome, "application/pdf", OLD.length,
                storage.save(contractId, OLD), type, admin)).getId();
    }

    private static MockMultipartFile pdf(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/pdf", content);
    }

    private List<Path> arquivosDoContrato() {
        Path dir = Path.of(storageProps.storageDir()).toAbsolutePath().resolve(contractId.toString());
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
