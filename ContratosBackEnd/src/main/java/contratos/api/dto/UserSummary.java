package contratos.api.dto;

public record UserSummary(
        Long id,
        String name,
        String username,
        String email,
        String cellPhone,
        SectorSummary sector,
        boolean admin,
        String perfil
) {
    public record SectorSummary(Long id, String name) {}
}
