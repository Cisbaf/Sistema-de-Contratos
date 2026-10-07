package contratos;

import contratos.api.dto.Contract.ContractResponse;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.InterestEmailConfirmation;
import contratos.domain.Sector;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.ContractRepository;
import contratos.repository.InterestEmailConfirmationRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import contratos.service.ContractService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PG-10.4 — {@code fiscaisConfirmadosEnvioInteresse} na lista de contratos: cada contrato recebe só as confirmações
 * dele e o número de consultas não cresce com o número de contratos (antes: uma consulta por contrato).
 * H2 isolado ("confirmadoslistatest"), sem transação no teste, com as estatísticas do Hibernate ligadas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:confirmadoslistatest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
class ContractListConfirmadosIntegrationTest {

    @Autowired ContractService service;
    @Autowired ContractRepository contracts;
    @Autowired InterestEmailConfirmationRepository interests;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired EntityManagerFactory emf;

    Sector setor;
    AppUser f1;
    AppUser f2;
    AppUser f3;
    AppUser semContrato;
    Contract a;
    Contract b;
    Contract c;

    @BeforeEach
    void setUp() {
        interests.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        setor = sectors.save(new Sector("Setor Confirmados"));
        f1 = fiscal("f1");
        f2 = fiscal("f2");
        f3 = fiscal("f3");
        semContrato = fiscal("sem.contrato");

        a = contrato("CONF-A/2026", f1, f2);
        b = contrato("CONF-B/2026", f2, f3);
        c = contrato("CONF-C/2026", f1);

        // A: confirmaram f2 e depois f1 (a ordem de gravação é a ordem esperada); B: só f3; C: ninguém
        confirmar(a, f2);
        confirmar(a, f1);
        confirmar(b, f3);
    }

    @Test
    void cadaContratoRecebeSoAsConfirmacoesDele() {
        Map<String, List<Long>> porNumero = confirmadosPorNumero(service.findAll());

        assertThat(porNumero.get("CONF-A/2026")).containsExactly(f2.getId(), f1.getId());
        assertThat(porNumero.get("CONF-B/2026")).containsExactly(f3.getId());
        assertThat(porNumero.get("CONF-C/2026")).isEmpty();
        assertThat(porNumero).hasSize(3);
    }

    @Test
    void contratoSemConfirmacaoDevolveListaVaziaEnaoNulo() {
        ContractResponse resposta = service.findAll().stream()
                .filter(r -> r.numberContract().equals("CONF-C/2026")).findFirst().orElseThrow();

        assertThat(resposta.fiscaisConfirmadosEnvioInteresse()).isNotNull().isEmpty();
    }

    @Test
    void minhaListaTrazSoOsContratosDoFiscalComAsConfirmacoesDeCadaUm() {
        Map<String, List<Long>> doF1 = confirmadosPorNumero(service.findMine(f1.getUsername()));
        assertThat(doF1).containsOnlyKeys("CONF-A/2026", "CONF-C/2026");
        assertThat(doF1.get("CONF-A/2026")).containsExactly(f2.getId(), f1.getId());
        assertThat(doF1.get("CONF-C/2026")).isEmpty();

        Map<String, List<Long>> doF3 = confirmadosPorNumero(service.findMine(f3.getUsername()));
        assertThat(doF3).containsOnlyKeys("CONF-B/2026");
        assertThat(doF3.get("CONF-B/2026")).containsExactly(f3.getId());
    }

    @Test
    void fiscalSemContratoEListaVaziaNaoQuebram() {
        assertThat(service.findMine(semContrato.getUsername())).isEmpty();

        interests.deleteAll();
        contracts.deleteAll();
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void contratoUnicoContinuaComAsConfirmacoes() {
        assertThat(service.findById(a.getId()).fiscaisConfirmadosEnvioInteresse())
                .containsExactlyInAnyOrder(f1.getId(), f2.getId());
        assertThat(service.findById(c.getId()).fiscaisConfirmadosEnvioInteresse()).isEmpty();
    }

    @Test
    void numeroDeConsultasNaoCresceComONumeroDeContratos() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        stats.clear();
        service.findAll();
        long comTres = stats.getPrepareStatementCount();

        for (int i = 0; i < 6; i++) {
            Contract extra = contrato("CONF-X" + i + "/2026", f1, f3);
            confirmar(extra, f1);
        }

        stats.clear();
        List<ContractResponse> nove = service.findAll();
        long comNove = stats.getPrepareStatementCount();

        assertThat(nove).hasSize(9);
        assertThat(comNove).as("consultas com 9 contratos x com 3").isEqualTo(comTres);
    }

    // ------------------------------------------------------------------ apoio

    private Map<String, List<Long>> confirmadosPorNumero(List<ContractResponse> lista) {
        return lista.stream().collect(Collectors.toMap(ContractResponse::numberContract,
                ContractResponse::fiscaisConfirmadosEnvioInteresse, (x, y) -> x));
    }

    private AppUser fiscal(String nome) {
        return users.save(new AppUser(nome + "@test.local", "{noop}x", "Fiscal " + nome, nome + "@test.local",
                null, setor, PerfilUsuario.FISCAL));
    }

    private Contract contrato(String numero, AppUser... fiscais) {
        Contract contract = new Contract();
        contract.update(numero, "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 1),
                null, "2", Set.of(fiscais), "SEI-" + numero, 12);
        return contracts.save(contract);
    }

    private void confirmar(Contract contract, AppUser quem) {
        interests.save(new InterestEmailConfirmation(contract, quem, LocalDateTime.of(2026, 9, 1, 10, 0)));
    }
}
