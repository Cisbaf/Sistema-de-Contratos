package contratos.api;

import contratos.api.dto.GeneratedDocument.GeneratedDocumentResponse;
import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroHistoricoResponse;
import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroRequest;
import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroResponse;
import contratos.api.dto.LancamentoFinanceiro.PaymentChecklistPreviewResponse;
import contratos.api.dto.LancamentoFinanceiro.SaldoContratoResponse;
import contratos.service.LancamentoFinanceiroService;
import contratos.service.PaymentChecklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LancamentoFinanceiroController {
    private final LancamentoFinanceiroService service;
    private final PaymentChecklistService checklistService;

    @PostMapping("/contracts/{contratoId}/lancamentos")
    @PreAuthorize("@contractAuthorization.canRead(#contratoId, authentication)")
    public ResponseEntity<LancamentoFinanceiroResponse> criar(@PathVariable Long contratoId,
                                                              @Valid @RequestBody LancamentoFinanceiroRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criar(contratoId, request, authentication.getName()));
    }

    @GetMapping("/contracts/{contratoId}/lancamentos")
    @PreAuthorize("@contractAuthorization.canRead(#contratoId, authentication)")
    public ResponseEntity<List<LancamentoFinanceiroResponse>> listar(@PathVariable Long contratoId) {
        return ResponseEntity.ok(service.listarPorContrato(contratoId));
    }

    @GetMapping("/contracts/{contratoId}/lancamentos/saldo")
    @PreAuthorize("@contractAuthorization.canRead(#contratoId, authentication)")
    public ResponseEntity<SaldoContratoResponse> saldo(@PathVariable Long contratoId) {
        return ResponseEntity.ok(new SaldoContratoResponse(service.calcularSaldo(contratoId)));
    }

    @GetMapping("/contracts/{contratoId}/lancamentos/historico")
    @PreAuthorize("@contractAuthorization.canRead(#contratoId, authentication)")
    public ResponseEntity<List<LancamentoFinanceiroHistoricoResponse>> historico(@PathVariable Long contratoId) {
        return ResponseEntity.ok(service.listarHistoricoPorContrato(contratoId));
    }

    @PutMapping("/lancamentos/{id}")
    public ResponseEntity<LancamentoFinanceiroResponse> editar(@PathVariable Long id,
                                                               @Valid @RequestBody LancamentoFinanceiroRequest request,
                                                               Authentication authentication) {
        return ResponseEntity.ok(service.editar(id, request, authentication));
    }

    @DeleteMapping("/lancamentos/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, Authentication authentication) {
        service.excluir(id, authentication);
        return ResponseEntity.noContent().build();
    }

    // Ateste dos fiscais ("Gerar Checklist"). Autorização dentro do service, pelo contrato do lançamento.
    @GetMapping("/lancamentos/{id}/checklist/preview")
    public ResponseEntity<PaymentChecklistPreviewResponse> previewChecklist(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(new PaymentChecklistPreviewResponse(checklistService.preview(id, authentication)));
    }

    @PostMapping("/lancamentos/{id}/checklist")
    public ResponseEntity<GeneratedDocumentResponse> gerarChecklist(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.gerar(id, authentication));
    }
}
