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
