package contratos.service;

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
    private String format(Object value){
        return value == null ? "(vazio)" : value.toString();
    }
    public String build() {
        return parts.length() == 0 ? null : parts.toString();
    }
}
