package contratos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.StringJoiner;

public class AuditChangeLog {
    private final StringJoiner parts = new StringJoiner("; ");

    public <T> AuditChangeLog field(String label, T before, T after){
        if (!Objects.equals(before, after)){
            parts.add(label + ": " + format(before) + " -> " + format(after));
        }
        return this;
    }

    // Overload dedicado para BigDecimal: Objects.equals (usado acima) compara também a escala,
    // então "96000.00" (vindo do banco) e "96000.0" (vindo do JSON da requisição) seriam tratados
    // como valores diferentes mesmo sendo o mesmo número. Aqui a comparação é por valor (compareTo)
    // e a formatação é sempre com 2 casas, para o texto não ficar inconsistente entre antes/depois.
    public AuditChangeLog field(String label, BigDecimal before, BigDecimal after){
        boolean changed = (before == null) != (after == null)
                || (before != null && before.compareTo(after) != 0);
        if (changed){
            parts.add(label + ": " + formatDecimal(before) + " -> " + formatDecimal(after));
        }
        return this;
    }

    private String format(Object value){
        return value == null ? "(vazio)" : value.toString();
    }

    private String formatDecimal(BigDecimal value){
        return value == null ? "(vazio)" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String build() {
        return parts.length() == 0 ? null : parts.toString();
    }
}
