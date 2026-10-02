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
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;
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

    public ContractAttachment(Contract contract, String fileName, String contentType, long sizeBytes, byte[] content, AttachmentType attType,
                              AppUser uploadedBy) {
        this.contract = contract;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.attType = attType;
        this.content = content;
        this.uploadedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.uploadedBy = uploadedBy;
    }

    /**
     * Substituição do arquivo (usada no documento do Termo Aditivo): sobrescreve a mesma linha, sem criar outra, para
     * não inflar a contagem de aditivos nem o limite de anexos. Quem enviou e quando passam a ser os da troca; o
     * arquivo anterior só fica na auditoria (nome). Tipo, contrato e situação (ativo) não mudam.
     */
    public void replaceFile(String fileName, String contentType, long sizeBytes, byte[] content, AppUser user) {
        if (!ativo) {
            throw new IllegalStateException("Não é possível substituir um anexo removido");
        }
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.uploadedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.uploadedBy = user;
    }

    /**
     * Remoção lógica: mantém o registro (nome, tamanho original, quem enviou/removeu e quando)
     * para o histórico, mas descarta o conteúdo do arquivo para liberar espaço no banco.
     * A coluna é NOT NULL, então o conteúdo vira um array vazio em vez de null.
     */
    public void removeAttachment(AppUser removedBy) {
        this.content = new byte[0];
        this.removedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.removedBy = removedBy;
        this.ativo = false;
    }
}
