package contratos.repository;

import contratos.domain.LancamentoFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {
    List<LancamentoFinanceiro> findByContrato_IdAndAtivoTrue(Long contratoId);

    boolean existsByContrato_IdAndNotaFiscal(Long contratoId, String notaFiscal);


    /** Trava o lançamento antes de editar/excluir: duas edições do mesmo lançamento não se misturam. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from LancamentoFinanceiro l where l.id = :id")
    java.util.Optional<LancamentoFinanceiro> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
