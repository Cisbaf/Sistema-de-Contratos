package contratos.repository;

import contratos.domain.LancamentoFinanceiroHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LancamentoFinanceiroHistoricoRepository extends JpaRepository<LancamentoFinanceiroHistorico, Long> {

    List<LancamentoFinanceiroHistorico> findByContrato_IdOrderByAlteradoEmDesc(Long contratoId);
    boolean existsByLancamento_Id(Long lancamentoId);
}
