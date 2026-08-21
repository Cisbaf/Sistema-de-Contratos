package br.org.cisbaf.contratos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record ContractRequest(
        @NotBlank @Size(max = 200) String numberContract,
        @NotBlank @Size(max = 200) String numberProcess,
        @NotBlank String object,
        @NotBlank @Size(max = 200) String company,
        @NotBlank @Size(max = 30) String cnpjCpf,
        @NotNull @DecimalMin("0.00") BigDecimal valueGlobal,
        @NotNull @DecimalMin("0.00") BigDecimal valueMensal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 200) String font,
        @Size(max = 10) String ta,
        Set<Long> fiscalIds
) {}
