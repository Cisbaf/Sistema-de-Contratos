package contratos.api.dto;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CNPJ;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record ContractRequest(
        @NotBlank @Size(max = 200) String numberContract,
        @NotBlank @Size(max = 200) String numberProcess,
        @NotBlank String object,
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 30) @CNPJ(message = "CNPJ inválido") String cnpj,
        @NotNull @DecimalMin("0.00") BigDecimal valueGlobal,
        @NotNull @DecimalMin("0.00") BigDecimal valueMensal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 200)  String font, //fonte de recurso
        @Size(max = 10)  String ta, //termo aditivo
        @NotEmpty(message = "Fiscais são obrigatórios") Set<Long> fiscalIds
) {}
