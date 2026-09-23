package contratos.service;

import contratos.api.dto.ContractAttachment.ContractAttachmentFile;
import contratos.api.dto.ContractAttachment.ContractAttachmentResponse;
import contratos.domain.ContractAttachment;
import contratos.repository.ContractAttachmentRepository;
import contratos.repository.ContractRepository;
import contratos.repository.UserRepository;
import contratos.security.ContractAuthorization;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractAttachmentService {
    private static final int MAX_FILES_PER_UPLOAD = 5;
    private static final int MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT = 10;

    private final ContractAttachmentRepository attachmentRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ContractAuthorization contractAuthorization;

    @Transactional(readOnly = true)
    public List<ContractAttachmentResponse> listarAtivos(Long contractId) {
        if (contractId == null) {
            throw new IllegalArgumentException("O id do contrato não pode ser nullo");
        }
        var attachment = attachmentRepository.findByContract_IdAndAtivoTrueOrderByUploadedAtDesc(contractId);
        return attachment.stream().map(this::mapAttachmentResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ContractAttachmentResponse> listarTimeline(Long contractId) {
        if (contractId == null) {
            throw new IllegalArgumentException("O id do contrato não pode ser nullo");
        }
        return attachmentRepository.findByContract_IdOrderByUploadedAtAsc(contractId).stream().map(this::mapAttachmentResponse).toList();
    }

    @Transactional
    public List<ContractAttachmentResponse> uploadFiles(Long contractId, List<MultipartFile> files, String username) throws IOException {
        if (files.isEmpty() || files.size() > MAX_FILES_PER_UPLOAD) {
            throw new IllegalArgumentException("É necessário no mínimo 1 e no máximo " + MAX_FILES_PER_UPLOAD + " arquivos");
        }
        var contract = contractRepository.findById(contractId).orElseThrow(() -> new EntityNotFoundException("Não existe contrato atrelado ao id: " + contractId));

        // Só anexos ativos contam: um anexo removido já teve o conteúdo apagado e libera a vaga.
        long ativos = attachmentRepository.countByContract_IdAndAtivoTrue(contractId);
        if (ativos + files.size() > MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT) {
            long vagas = Math.max(0, MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT - ativos);
            throw new IllegalArgumentException("Limite de " + MAX_ACTIVE_ATTACHMENTS_PER_CONTRACT
                    + " anexos por contrato. Este contrato já tem " + ativos + " e comporta mais " + vagas + ".");
        }
        var user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Não existe usuário com o nome: " + username));

        List<ContractAttachment> attachments = new ArrayList<>();

        for (MultipartFile file : files) {

            if (file.getSize() == 0 || file.getContentType() == null || file.getContentType().isEmpty()) {
                throw new IllegalArgumentException("Um ou mais arquivos estão vazios ou quebrados");
            }
            if (file.getOriginalFilename() == null) {
                throw new IllegalArgumentException("Um ou mais arquivos estão sem nome ou quebrados");
            }
            var contem = file.getOriginalFilename().toLowerCase();
            if (contem.endsWith(".pdf") || contem.endsWith(".doc") || contem.endsWith(".docx")) {
                var contractAttachments = new ContractAttachment(contract, file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getBytes(), user);
                attachments.add(contractAttachments);
            } else {
                throw new IllegalArgumentException("Apenas arquivos pdf, doc e docx são permitidos");
            }

        }
        return attachmentRepository.saveAll(attachments).stream().map(this::mapAttachmentResponse).toList();
    }

    @Transactional
    public void removeFiles(Long attachmentId, String username) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("O id do arquivo não pode ser nullo");
        }

        var attachment = attachmentRepository.findById(attachmentId).orElseThrow(() -> new EntityNotFoundException("Não existe arquivo atrelado ao id: " + attachmentId));

        if (!attachment.isAtivo()) return;

        var user = userRepository.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Não existe usuário com o nome: " + username));

        attachment.removeAttachment(user);
    }

    @Transactional(readOnly = true)
    public ContractAttachmentFile baixarConteudo(Long attachmentId, Authentication authentication) {
        if (attachmentId == null) {
            throw new IllegalArgumentException("O id do arquivo não pode ser nullo");
        }
        var attachment = attachmentRepository.findById(attachmentId).orElseThrow(() -> new EntityNotFoundException("Não existe arquivo atrelado ao id: " + attachmentId));

        if (!contractAuthorization.canRead(attachment.getContract().getId(), authentication)) {
            throw new AccessDeniedException("O usuário não tem autorização para realizar essa operação");
        }

        if (!attachment.isAtivo()) {
            throw new EntityNotFoundException("Este anexo foi removido e o conteúdo não está mais disponível");
        }

        return new ContractAttachmentFile(
                attachment.getContract().getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getContent());
    }

    private ContractAttachmentResponse mapAttachmentResponse(ContractAttachment attachment) {
        var removedBy = attachment.getRemovedBy();
        return new ContractAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedAt(),
                EntityMapper.user(attachment.getUploadedBy()),
                attachment.isAtivo(),
                attachment.getRemovedAt(),
                removedBy == null ? null : EntityMapper.user(removedBy)
        );
    }

}
