package contratos.service;

import java.util.Comparator;
import java.util.List;

import contratos.api.dto.ContractResponse;
import contratos.api.dto.UserSummary;
import contratos.domain.AppUser;
import contratos.domain.Contract;

public final class EntityMapper {
    private EntityMapper() {}

    public static UserSummary user(AppUser value) {
        UserSummary.SectorSummary sector = value.getSector() == null ? null
                : new UserSummary.SectorSummary(value.getSector().getId(), value.getSector().getName());
        return new UserSummary(value.getId(), value.getName(),value.getUsername(), value.getEmail(), value.getCellPhone(), sector,
                value.isAdmin(), value.getPerfil().name());
    }

    public static ContractResponse contract(Contract value, List<Long> confirmados) {
        return new ContractResponse(
                value.getId(), value.getNumberContract(), value.getNumberProcess(), value.getObject(),
                value.getCompany(), value.getCnpj(), value.getValueGlobal(), value.getValueMensal(),
                value.getFiscais().stream().map(EntityMapper::user)
                        .sorted(Comparator.comparing(UserSummary::name)).toList(),
                value.getStartDate(), value.getEndDate(), value.getFont(), value.getTa(),value.getStatus(), confirmados);
    }
}
