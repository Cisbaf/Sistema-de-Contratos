package contratos.api.dto.ContractAttachment;

public record ContractAttachmentFile(Long contractId, String fileName, String contentType, byte[] content) {
}
