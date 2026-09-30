package contratos.api.dto.GeneratedDocument;

import contratos.domain.enums.DocumentFormat;
import contratos.domain.enums.DocumentTemplateType;

public record GeneratedDocumentRequest(
        Long contractId,
        DocumentTemplateType documentType,
        DocumentFormat format,
        String fileName,
        byte[] content,
        // Opcional: só o ateste dos fiscais (PAYMENT_CHECKLIST) usa isto, pra permitir reaproveitar um documento
        // já gerado pro mesmo lançamento em vez de criar uma versão nova a cada clique.
        Long lancamentoId) {
    public GeneratedDocumentRequest(Long contractId, DocumentTemplateType documentType, DocumentFormat format, String fileName, byte[] content) {
        this(contractId, documentType, format, fileName, content, null);
    }
}
