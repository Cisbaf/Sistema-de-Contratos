package br.org.cisbaf.contratos.service;

import br.org.cisbaf.contratos.api.dto.ContractResponse;
import br.org.cisbaf.contratos.api.dto.UserSummary;
import br.org.cisbaf.contratos.domain.AppUser;
import br.org.cisbaf.contratos.domain.Contract;
import java.util.Comparator;

public final class EntityMapper {
    private EntityMapper() {}

    public static UserSummary user(AppUser value) {
        UserSummary.SectorSummary sector = value.getSector() == null ? null
                : new UserSummary.SectorSummary(value.getSector().getId(), value.getSector().getName());
        return new UserSummary(value.getId(), value.getName(), value.getEmail(), value.getCellPhone(), sector,
                value.isAdmin(), value.getPerfil().name());
    }

    public static ContractResponse contract(Contract value) {
        return new ContractResponse(
                value.getId(), value.getNumberContract(), value.getNumberProcess(), value.getObject(),
                value.getCompany(), value.getCnpjCpf(), value.getValueGlobal(), value.getValueMensal(),
                value.getFiscais().stream().map(EntityMapper::user)
                        .sorted(Comparator.comparing(UserSummary::name)).toList(),
                value.getStartDate(), value.getEndDate(), value.getFont(), value.getTa());
    }
}
