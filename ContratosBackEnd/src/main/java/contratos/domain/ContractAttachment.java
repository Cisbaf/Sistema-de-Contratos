package contratos.domain;

import contratos.domain.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor
public class ContractAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Contract contract;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false)
    private String contentType;
    /** Tamanho ORIGINAL do arquivo (o que o usuário enviou), não o do arquivo comprimido em disco. */
    @Column(nullable = false)
    private long sizeBytes;
    /**
     * Caminho relativo do arquivo (comprimido) dentro da pasta de anexos, ver {@code AttachmentStorage} (ANX-10).
     * Nulo só em anexo removido.
     */
    @Column(length = 255)
    private String storagePath;

    @Enumerated(value = EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'GERAL'", nullable = false)
    private AttachmentType attType;
    @Column(nullable = false)
    private LocalDateTime uploadedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser uploadedBy;
    @Column(nullable = false)
    private boolean ativo = true;
    private LocalDateTime removedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser removedBy;

    public ContractAttachment(Contract contract, String fileName, String contentType, long sizeBytes, String storagePath,
                              AttachmentType attType, AppUser uploadedBy) {
        this.contract = contract;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.attType = attType;
        this.storagePath = storagePath;
        this.uploadedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.uploadedBy = uploadedBy;
    }

    /**
     * Substituição do arquivo (usada no documento do Termo Aditivo): sobrescreve a mesma linha, sem criar outra, para
     * não inflar a contagem de aditivos nem o limite de anexos. Quem enviou e quando passam a ser os da troca; o
     * arquivo anterior só fica na auditoria (nome). Tipo, contrato e situação (ativo) não mudam. O arquivo novo já
     * deve estar gravado em disco; quem chama apaga o antigo depois do commit.
     */
    public void replaceFile(String fileName, String contentType, long sizeBytes, String storagePath, AppUser user) {
        if (!ativo) {
            throw new IllegalStateException("Não é possível substituir um anexo removido");
        }
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.uploadedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.uploadedBy = user;
    }

    /**
     * Remoção lógica: mantém o registro (nome, tamanho original, quem enviou/removeu e quando) para o histórico, mas
     * esquece o arquivo: o caminho é descartado (quem chama apaga o arquivo do disco depois do
     * commit).
     */
    public void removeAttachment(AppUser removedBy) {
        this.storagePath = null;
        this.removedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.removedBy = removedBy;
        this.ativo = false;
    }
}
