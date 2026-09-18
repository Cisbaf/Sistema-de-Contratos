package contratos.api.dto.Contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import contratos.api.dto.User.UserSummary;
import contratos.domain.enums.ContractStatus;

public record ContractResponse(
        Long id,
        String numberContract,
        String numberProcess,
        String object,
        String company,
        String cnpj,
        BigDecimal valueGlobal,
        BigDecimal valueMensal,
        List<UserSummary> fiscais,
        LocalDate startDate,
        LocalDate endDate,
        String font,
        String ta,
        ContractStatus status,
        List<Long> fiscaisConfirmadosEnvioInteresse,
        String seiProcessNumber,
        Integer maxExtensionMonths
) {}
