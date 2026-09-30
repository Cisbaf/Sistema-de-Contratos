package contratos.domain;

import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"contract_id", "document_type", "version"}))
public class GeneratedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private int version;

    @Column(columnDefinition = "LONGBLOB", nullable = false)
    private byte[] content;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser generatedBy;

    @Enumerated(EnumType.STRING)
    private DocumentFormat format;

    @Enumerated(EnumType.STRING)
    private DocumentTemplateType documentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Contract contract;

    // Opcional: só preenchido pro ateste dos fiscais (PAYMENT_CHECKLIST), pra saber a qual lançamento cada
    // documento pertence e poder reaproveitar um já gerado em vez de criar versão nova a cada clique.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lancamento_id")
    private LancamentoFinanceiro lancamento;

    public GeneratedDocument(String fileName, int version, byte[] content, LocalDateTime generatedAt, AppUser generatedBy, DocumentFormat format, DocumentTemplateType documentType, Contract contract) {
        this(fileName, version, content, generatedAt, generatedBy, format, documentType, contract, null);
    }

    public GeneratedDocument(String fileName, int version, byte[] content, LocalDateTime generatedAt, AppUser generatedBy, DocumentFormat format, DocumentTemplateType documentType, Contract contract, LancamentoFinanceiro lancamento) {
        this.fileName = fileName;
        this.version = version;
        this.content = content;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.format = format;
        this.documentType = documentType;
        this.contract = contract;
        this.lancamento = lancamento;
    }
}
