package contratos.service;

import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroHistoricoResponse;
import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroRequest;
import contratos.api.dto.LancamentoFinanceiro.LancamentoFinanceiroResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.LancamentoFinanceiro;
import contratos.domain.LancamentoFinanceiroHistorico;
import contratos.domain.enums.TipoEventoLancamento;
import contratos.exception.ConflictException;
import contratos.repository.ContractRepository;
import contratos.repository.LancamentoFinanceiroHistoricoRepository;
import contratos.repository.LancamentoFinanceiroRepository;
import contratos.repository.UserRepository;
import contratos.security.ContractAuthorization;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {
    private final LancamentoFinanceiroRepository repository;
    private final LancamentoFinanceiroHistoricoRepository historicoRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ContractAuthorization authorization;

    // READ_COMMITTED + trava do contrato: quem chega depois espera e já enxerga o saldo atualizado
    // (no REPEATABLE READ padrão do MySQL ele leria um saldo antigo e poderia estourar o contrato).
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LancamentoFinanceiroResponse criar(Long contratoId, LancamentoFinanceiroRequest request, String username) {
        Contract contrato = bloquearContrato(contratoId);
        AppUser usuario = buscarUsuario(username);
        String notaFiscal = request.notaFiscal().trim();

        // Sem filtro de ativo: a constraint física nota_fiscal + contrato_id também vale para desativados.
        if (repository.existsByContrato_IdAndNotaFiscal(contratoId, notaFiscal)) {
            throw new ConflictException("Já existe um lançamento com a nota fiscal " + notaFiscal + " neste contrato");
        }

        BigDecimal saldoProjetado = calcularSaldo(contrato).subtract(request.valorNota());
        validarSaldo(saldoProjetado);

        LancamentoFinanceiro lancamento = repository.save(new LancamentoFinanceiro(
                request.numeroProcesso().trim(),
                notaFiscal,
                request.competencia().withDayOfMonth(1),
                vazioParaNull(request.parcela()),
                request.valorNota(),
                vazioParaNull(request.observacoes()),
                contrato,
                usuario));
        return mapResponse(lancamento);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LancamentoFinanceiroResponse editar(Long lancamentoId, LancamentoFinanceiroRequest request,
                                               Authentication authentication) {
        LancamentoFinanceiro lancamento = buscarLancamentoAtivo(lancamentoId, authentication);
        AppUser usuario = buscarUsuario(authentication.getName());
        Contract contrato = lancamento.getContrato();
        String notaFiscal = request.notaFiscal().trim();

        // Nada mudou: não grava histórico nem atualiza "atualizado por/em".
        if (semAlteracao(lancamento, request, notaFiscal)) {
            return mapResponse(lancamento);
        }

        // Só checa duplicidade se a nota fiscal mudou; senão o próprio lançamento seria contado como duplicado.
        // equalsIgnoreCase porque a collation padrão do MySQL não diferencia maiúsculas de minúsculas.
        if (!notaFiscal.equalsIgnoreCase(lancamento.getNotaFiscal())
                && repository.existsByContrato_IdAndNotaFiscal(contrato.getId(), notaFiscal)) {
            throw new ConflictException("Já existe um lançamento com a nota fiscal " + notaFiscal + " neste contrato");
        }

        // O valor antigo do próprio lançamento volta para o saldo antes de descontar o novo.
        BigDecimal saldoProjetado = calcularSaldo(contrato)
                .add(lancamento.getValorNota())
                .subtract(request.valorNota());
        validarSaldo(saldoProjetado);

        // Snapshot ANTES do update, senão o histórico guardaria os valores novos.
        historicoRepository.save(new LancamentoFinanceiroHistorico(lancamento, TipoEventoLancamento.EDICAO, usuario));

        lancamento.updateLancamento(
                request.numeroProcesso().trim(),
                notaFiscal,
                request.competencia().withDayOfMonth(1),
                vazioParaNull(request.parcela()),
                request.valorNota(),
                vazioParaNull(request.observacoes()),
                usuario);
        return mapResponse(lancamento);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void excluir(Long lancamentoId, Authentication authentication) {
        LancamentoFinanceiro lancamento = buscarLancamentoAtivo(lancamentoId, authentication);

        // Exclusão híbrida: nunca editado -> DELETE físico, sem rastro, nota fiscal liberada.
        if (!historicoRepository.existsByLancamento_Id(lancamentoId)) {
            repository.delete(lancamento);
            return;
        }

        // Já editado -> só desativa e registra a exclusão; a nota fiscal continua reservada.
        AppUser usuario = buscarUsuario(authentication.getName());
        historicoRepository.save(new LancamentoFinanceiroHistorico(lancamento, TipoEventoLancamento.EXCLUSAO, usuario));
        lancamento.desativaLancamento(usuario);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularSaldo(Long contratoId) {
        return calcularSaldo(buscarContrato(contratoId));
    }

    @Transactional(readOnly = true)
    public List<LancamentoFinanceiroResponse> listarPorContrato(Long contratoId) {
        buscarContrato(contratoId);
        return repository.findByContrato_IdAndAtivoTrue(contratoId).stream().map(this::mapResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LancamentoFinanceiroHistoricoResponse> listarHistoricoPorContrato(Long contratoId) {
        buscarContrato(contratoId);
        return historicoRepository.findByContrato_IdOrderByAlteradoEmDesc(contratoId).stream()
                .map(this::mapHistoricoResponse).toList();
    }

    private BigDecimal calcularSaldo(Contract contrato) {
        BigDecimal totalLancado = repository.findByContrato_IdAndAtivoTrue(contrato.getId()).stream()
                .map(LancamentoFinanceiro::getValorNota)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return contrato.getValueGlobal().subtract(totalLancado);
    }

    private void validarSaldo(BigDecimal saldoProjetado) {
        if (saldoProjetado.signum() < 0) {
            throw new ConflictException("Valor da nota excede o saldo do contrato em " + saldoProjetado.abs());
        }
    }

    private boolean semAlteracao(LancamentoFinanceiro atual, LancamentoFinanceiroRequest novo, String notaFiscal) {
        return atual.getNumeroProcesso().equals(novo.numeroProcesso().trim())
                && atual.getNotaFiscal().equals(notaFiscal)
                && atual.getCompetencia().equals(novo.competencia().withDayOfMonth(1))
                && atual.getValorNota().compareTo(novo.valorNota()) == 0
                && Objects.equals(atual.getParcela(), vazioParaNull(novo.parcela()))
                && Objects.equals(atual.getObservacoes(), vazioParaNull(novo.observacoes()));
    }

    private String vazioParaNull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Contract bloquearContrato(Long contratoId) {
        return contractRepository.findByIdForUpdate(contratoId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado com o id: " + contratoId));
    }

    private Contract buscarContrato(Long contratoId) {
        return contractRepository.findById(contratoId)
                .orElseThrow(() -> new EntityNotFoundException("Contrato não encontrado com o id: " + contratoId));
    }

    private AppUser buscarUsuario(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + username));
    }

    /** Autorização vem antes da checagem de "ativo" para não revelar o estado de lançamentos de contratos alheios. */
    private LancamentoFinanceiro buscarLancamentoAtivo(Long lancamentoId, Authentication authentication) {
        // Trava o lançamento e depois o contrato (sempre nessa ordem, para não gerar deadlock) ANTES de ler
        // o saldo: o valor antigo e a soma dos outros lançamentos precisam estar atualizados.
        LancamentoFinanceiro lancamento = repository.findByIdForUpdate(lancamentoId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento não encontrado com o id: " + lancamentoId));
        bloquearContrato(lancamento.getContrato().getId());
        if (!authorization.canRead(lancamento.getContrato().getId(), authentication)) {
            throw new AccessDeniedException("Usuário não tem permissão para alterar este lançamento");
        }
        if (!lancamento.isAtivo()) {
            throw new ConflictException("Este lançamento já foi excluído");
        }
        return lancamento;
    }

    private LancamentoFinanceiroResponse mapResponse(LancamentoFinanceiro lancamento) {
        return new LancamentoFinanceiroResponse(
                lancamento.getId(),
                lancamento.getContrato().getId(),
                lancamento.getNumeroProcesso(),
                lancamento.getNotaFiscal(),
                lancamento.getParcela(),
                lancamento.getCompetencia(),
                lancamento.getValorNota(),
                lancamento.getObservacoes(),
                lancamento.getCriadoEm(),
                EntityMapper.user(lancamento.getCriadoPor()),
                lancamento.getAtualizadoEm(),
                lancamento.getAtualizadoPor() == null ? null : EntityMapper.user(lancamento.getAtualizadoPor()));
    }

    private LancamentoFinanceiroHistoricoResponse mapHistoricoResponse(LancamentoFinanceiroHistorico historico) {
        return new LancamentoFinanceiroHistoricoResponse(
                historico.getId(),
                historico.getLancamento().getId(),
                historico.getContrato().getId(),
                historico.getTipoEvento(),
                historico.getNumeroProcesso(),
                historico.getNotaFiscal(),
                historico.getParcela(),
                historico.getCompetencia(),
                historico.getValorNota(),
                historico.getObservacoes(),
                historico.getAlteradoEm(),
                EntityMapper.user(historico.getAlteradoPor()));
    }
}
