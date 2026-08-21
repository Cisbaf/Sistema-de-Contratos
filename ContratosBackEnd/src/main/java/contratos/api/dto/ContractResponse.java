package contratos.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ContractResponse(
        Long id,
        String numberContract,
        String numberProcess,
        String object,
        String company,
        String cnpjCpf,
        BigDecimal valueGlobal,
        BigDecimal valueMensal,
        List<UserSummary> fiscais,
        LocalDate startDate,
        LocalDate endDate,
        String font,
        String ta
) {}
