package contratos.domain;

import contratos.domain.enums.DocumentTemplateType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private DocumentTemplateType templateType;
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private AppUser updatedBy;

    public DocumentTemplate(DocumentTemplateType templateType, String content, LocalDateTime updatedAt, AppUser updatedBy) {
        this.templateType = templateType;
        this.content = content;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public void updateContent(String content, LocalDateTime updatedAt, AppUser updatedBy){
        this.content = content;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
