package br.org.cisbaf.contratos.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 100) String cellPhone,
        @NotNull Long sectorId,
        @Size(min = 6, max = 100) String password,
        boolean admin,
        String perfil
) {}
