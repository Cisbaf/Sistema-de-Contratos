package contratos.repository;

import contratos.domain.LancamentoFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {
    List<LancamentoFinanceiro> findByContrato_Id(Long contratoId);
}
