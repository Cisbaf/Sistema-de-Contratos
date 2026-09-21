package contratos.domain;

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

    public ContractAttachment(Contract contract, String fileName, String contentType, long sizeBytes, byte[] content,
                              AppUser uploadedBy) {
        this.contract = contract;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.content = content;
        this.uploadedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.uploadedBy = uploadedBy;
    }

    public void removeAttachment(AppUser removedBy) {
        this.removedAt = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        this.removedBy = removedBy;
        this.ativo = false;
    }
}
