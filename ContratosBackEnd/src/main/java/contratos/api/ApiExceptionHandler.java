package contratos.api;

import contratos.api.dto.ApiErrorResponse;
import contratos.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String,String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiErrorResponse("Dados inválidos", fields));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(EntityNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> databaseConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "Operação não pôde ser concluída por conflito de dados");
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> businessConflict(ConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> unauthorized() {
        return response(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden() {
        return response(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        String responseMessage = message == null ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiErrorResponse(responseMessage));
    }
}
