package contratos.api.dto;

import java.util.Map;

public record ApiErrorResponse(String message, Map<String, String> fields) {
    public ApiErrorResponse(String message) {
        this(message, Map.of());
    }
}
