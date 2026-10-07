package contratos.service.Notification;

import contratos.validation.DocumentoFiscal;
import contratos.api.dto.Notificacao.NotificationMessage;
import contratos.api.dto.Notificacao.Recipient;
import contratos.domain.AppUser;
import contratos.domain.Contract;
import contratos.domain.enums.NotificationAlertType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/** Monta assunto e corpo dos alertas (função pura, sem Spring nem banco). */
public final class NotificationMessages {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private NotificationMessages() {
    }

    public static NotificationMessage build(Contract contract, NotificationAlertType alertType,
                                            Recipient recipient, LocalDate today) {
        String endDate = contract.getEndDate().format(DATE_FMT);
        boolean reinforcement = alertType == NotificationAlertType.FOUR_MONTHS;

        String subject = (reinforcement ? "[CISBAF] REFORÇO: " : "[CISBAF] ")
                + "Contrato " + contract.getNumberContract() + " vence em " + endDate;

        String intro = reinforcement
                ? "Este é um REFORÇO do aviso automático enviado anteriormente sobre o contrato abaixo."
                : "Este é um aviso automático do Sistema de Controle de Contratos do CISBAF.\n"
                  + "O contrato abaixo tem a vigência próxima do fim:";

        String action = reinforcement
                ? "Se a renovação ainda não foi encaminhada, é hora de agir."
                : "Providencie, se for o caso, as etapas de renovação no sistema.";

        String fiscais = contract.getFiscais().stream()
                .map(AppUser::getName)
                .sorted()
                .collect(Collectors.joining(", "));

        String text = "Prezado(a) " + recipient.name() + ",\n\n"
                + intro + "\n\n"
                + "Contrato: " + contract.getNumberContract() + "\n"
                + "Empresa: " + contract.getCompany() + " (" + DocumentoFiscal.rotulo(contract.getCnpj()) + " " + contract.getCnpj() + ")\n"
                + "Objeto: " + contract.getObject() + "\n"
                + "Processo: " + contract.getNumberProcess() + "\n"
                + "Término da vigência: " + endDate + "\n"
                + "Fiscal(is): " + fiscais + "\n\n"
                + remaining(today, contract.getEndDate()) + " " + action + "\n\n"
                + "Esta mensagem foi enviada automaticamente. Não responda este e-mail.\n\n"
                + "CISBAF – Consórcio Intermunicipal de Saúde da Baixada Fluminense\n";

        return new NotificationMessage(oneLine(subject), text);
    }

    /** Meses até o fim, arredondando para cima (3 meses e 20 dias = 4). */
    static long monthsRemaining(LocalDate today, LocalDate endDate) {
        long months = ChronoUnit.MONTHS.between(today, endDate);
        if (today.plusMonths(months).isBefore(endDate)) {
            months++;
        }
        return months;
    }

    private static String remaining(LocalDate today, LocalDate endDate) {
        long months = monthsRemaining(today, endDate);
        if (months <= 0) {
            return "O término da vigência é hoje.";
        }
        return "Faltam cerca de " + months + (months == 1 ? " mês" : " meses") + " para o término.";
    }

    /** Cabeçalho de e-mail não pode ter quebra de linha (evita injeção de cabeçalho). */
    private static String oneLine(String value) {
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }
}
