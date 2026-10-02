package contratos.service;

import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.ContractStatusHistory;
import contratos.domain.Sector;
import contratos.domain.enums.ContractStatus;
import contratos.domain.enums.ContractStatusTrigger;
import contratos.domain.enums.PerfilUsuario;
import contratos.repository.ContractRepository;
import contratos.repository.ContractStatusHistoryRepository;
import contratos.repository.SectorRepository;
import contratos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TA-10.2 — domínio e histórico do Termo Aditivo: {@link Contract#registerAmendment} e
 * {@link ContractStatusService#advanceAfterAmendmentRegistered}.
 *
 * O serviço é chamado dentro de uma transação e as conferências são feitas em transações novas,
 * para provar que a mudança foi realmente gravada (e não só alterada na memória).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:amendtest;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ContractAmendmentStatusTest {

    private static final LocalDate END = LocalDate.of(2026, 12, 1);
    private static final LocalDate NEW_END = LocalDate.of(2027, 12, 1);

    @Autowired ContractStatusService service;
    @Autowired ContractStatusHistoryRepository history;
    @Autowired ContractRepository contracts;
    @Autowired UserRepository users;
    @Autowired SectorRepository sectors;
    @Autowired PlatformTransactionManager tm;

    TransactionTemplate tx;
    AppUser ci;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(tm);
        history.deleteAll();
        contracts.deleteAll();
        users.deleteAll();
        sectors.deleteAll();

        Sector setor = sectors.save(new Sector("Controle Interno"));
        ci = users.save(new AppUser("ci", "x", "Controle Interno", "ci@cisbaf.org.br", null, setor,
                PerfilUsuario.CONTROLE_INTERNO));
    }

    // ---------- domínio (Contract.registerAmendment) ----------

    @Test
    void emRenovacaoTrocaStatusDataETaEDevolveTrue() {
        Contract c = contractIn(ContractStatus.RENOVACAO_ABERTA_SEI);

        boolean changed = c.registerAmendment(NEW_END, "TA 1");

        assertThat(changed).isTrue();
        assertThat(c.getStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
        assertThat(c.getEndDate()).isEqualTo(NEW_END);
        assertThat(c.getTa()).isEqualTo("TA 1");
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"EM_VIGENCIA", "AGUARDANDO_EMAIL_INTERESSE", "EMAIL_ENVIADO"})
    void foraDeRenovacaoNaoMudaNadaEDevolveFalse(ContractStatus status) {
        Contract c = contractIn(status);
        String taAntes = c.getTa();

        boolean changed = c.registerAmendment(NEW_END, "TA 1");

        assertThat(changed).isFalse();
        assertThat(c.getStatus()).isEqualTo(status);
        assertThat(c.getEndDate()).isEqualTo(END);
        assertThat(c.getTa()).isEqualTo(taAntes);
    }

    // ---------- serviço (advanceAfterAmendmentRegistered) ----------

    @Test
    void grava_contrato_e_uma_linha_de_historico_com_o_gatilho_novo() {
        Long id = savedContractIn(ContractStatus.RENOVACAO_ABERTA_SEI);

        boolean changed = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, NEW_END, "TA 1"));

        assertThat(changed).isTrue();
        inTx(() -> {
            Contract c = contracts.findById(id).orElseThrow();
            assertThat(c.getStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
            assertThat(c.getEndDate()).isEqualTo(NEW_END);
            assertThat(c.getTa()).isEqualTo("TA 1");

            List<ContractStatusHistory> rows = historyOf(id);
            assertThat(rows).hasSize(1);
            ContractStatusHistory h = rows.get(0);
            assertThat(h.getPreviousStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
            assertThat(h.getNewStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
            assertThat(h.getStatusTrigger()).isEqualTo(ContractStatusTrigger.ADITIVO_REGISTRADO);
            assertThat(h.getChangedBy().getId()).isEqualTo(ci.getId());
            assertThat(h.getChangedAt()).isNotNull();
            return null;
        });
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"EM_VIGENCIA", "AGUARDANDO_EMAIL_INTERESSE", "EMAIL_ENVIADO"})
    void statusErradoNaoMudaContratoNemGravaHistorico(ContractStatus status) {
        Long id = savedContractIn(status);

        boolean changed = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, NEW_END, "TA 1"));

        assertThat(changed).isFalse();
        inTx(() -> {
            Contract c = contracts.findById(id).orElseThrow();
            assertThat(c.getStatus()).isEqualTo(status);
            assertThat(c.getEndDate()).isEqualTo(END);
            assertThat(historyOf(id)).isEmpty();
            return null;
        });
    }

    @Test
    void dataOuTaNulosSaoRecusadosSemMudarNada() {
        Long id = savedContractIn(ContractStatus.RENOVACAO_ABERTA_SEI);

        boolean semData = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, null, "TA 1"));
        boolean semTa = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, NEW_END, null));

        assertThat(semData).isFalse();
        assertThat(semTa).isFalse();
        inTx(() -> {
            Contract c = contracts.findById(id).orElseThrow();
            assertThat(c.getStatus()).isEqualTo(ContractStatus.RENOVACAO_ABERTA_SEI);
            assertThat(c.getEndDate()).isEqualTo(END);
            assertThat(historyOf(id)).isEmpty();
            return null;
        });
    }

    @Test
    void registrarDeNovoLogoEmSeguidaNaoDuplicaHistorico() {
        Long id = savedContractIn(ContractStatus.RENOVACAO_ABERTA_SEI);

        boolean primeira = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, NEW_END, "TA 1"));
        boolean segunda = inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, NEW_END.plusMonths(6), "TA 2"));

        assertThat(primeira).isTrue();
        assertThat(segunda).isFalse();
        inTx(() -> {
            Contract c = contracts.findById(id).orElseThrow();
            assertThat(c.getEndDate()).isEqualTo(NEW_END);
            assertThat(c.getTa()).isEqualTo("TA 1");
            assertThat(historyOf(id)).hasSize(1);
            return null;
        });
    }

    // ---------- a régua recomeça em cima da data nova ----------

    @Test
    void dataNovaALongePrazoNaoReabreARenovacaoPeloJob() {
        Long id = savedContractIn(ContractStatus.RENOVACAO_ABERTA_SEI);
        LocalDate hoje = LocalDate.of(2026, 10, 1);
        LocalDate novaDataLonge = LocalDate.of(2027, 6, 1); // 6 meses antes = 01/12/2026, depois de "hoje"

        inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, novaDataLonge, "TA 1"));
        boolean mudou = inTx(() -> service.updateByDeadline(contracts.findById(id).orElseThrow(), hoje));

        assertThat(mudou).isFalse();
        inTx(() -> {
            assertThat(contracts.findById(id).orElseThrow().getStatus()).isEqualTo(ContractStatus.EM_VIGENCIA);
            assertThat(historyOf(id)).hasSize(1);
            return null;
        });
    }

    @Test
    void dataNovaDentroDosSeisMesesFazOJobReabrirOCicloComHistoricoProprio() {
        Long id = savedContractIn(ContractStatus.RENOVACAO_ABERTA_SEI);
        LocalDate hoje = LocalDate.of(2026, 10, 1);
        LocalDate novaDataPerto = LocalDate.of(2027, 1, 15); // 6 meses antes = 15/07/2026, antes de "hoje"

        inTx(() -> service.advanceAfterAmendmentRegistered(
                contracts.findById(id).orElseThrow(), ci, novaDataPerto, "TA 1"));
        boolean mudou = inTx(() -> service.updateByDeadline(contracts.findById(id).orElseThrow(), hoje));

        assertThat(mudou).isTrue();
        inTx(() -> {
            assertThat(contracts.findById(id).orElseThrow().getStatus())
                    .isEqualTo(ContractStatus.AGUARDANDO_EMAIL_INTERESSE);
            assertThat(historyOf(id)).extracting(ContractStatusHistory::getStatusTrigger)
                    .containsExactlyInAnyOrder(ContractStatusTrigger.ADITIVO_REGISTRADO, ContractStatusTrigger.DEADLINE);
            return null;
        });
    }

    // ---------- apoio ----------

    private <T> T inTx(Supplier<T> action) {
        return tx.execute(status -> action.get());
    }

    private List<ContractStatusHistory> historyOf(Long contractId) {
        return history.findAll().stream()
                .filter(h -> h.getContract().getId().equals(contractId))
                .toList();
    }

    /** Contrato novo, em memória, levado ao status pedido só pelos métodos de transição do domínio. */
    private Contract contractIn(ContractStatus target) {
        Contract c = new Contract();
        c.update("AD-001/2026", "PROC", "Objeto", "Empresa Ltda", "11222333000181",
                new BigDecimal("1000.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), END, null, "TA 03", Set.of(ci), "SEI-AD-001", 12);
        LocalDate dentroDosSeisMeses = END.minusMonths(1);
        if (target != ContractStatus.EM_VIGENCIA) {
            c.updateStatusByDeadline(dentroDosSeisMeses);
        }
        if (target == ContractStatus.EMAIL_ENVIADO || target == ContractStatus.RENOVACAO_ABERTA_SEI) {
            c.markInterestEmailSent();
        }
        if (target == ContractStatus.RENOVACAO_ABERTA_SEI) {
            c.markTechnicalOpinionGenerated();
        }
        assertThat(c.getStatus()).as("o apoio do teste tem que chegar ao status pedido").isEqualTo(target);
        return c;
    }

    private Long savedContractIn(ContractStatus target) {
        return contracts.save(contractIn(target)).getId();
    }
}
