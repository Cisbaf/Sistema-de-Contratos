package contratos.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectorRequest(
        @NotBlank(message = "Informe o nome do setor")
        @Size(max = 200)
        String name
) {}
