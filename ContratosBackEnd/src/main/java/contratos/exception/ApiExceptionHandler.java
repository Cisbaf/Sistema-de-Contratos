package contratos.exception;

import contratos.api.dto.ApiErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
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

    @ExceptionHandler(AttachmentStorageException.class)
    ResponseEntity<ApiErrorResponse> attachmentStorage(AttachmentStorageException exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> badMessageRequest() {
        return response(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou malformado");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiErrorResponse> unauthorized() {
        return response(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbiddenPreRole() {
        return response(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbiddenAccessOperation() {
        return response(HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> maxUploadSize() {
        return response(HttpStatus.BAD_REQUEST, "Arquivos enviados passam do tamanho máximo permitido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> argumentType(MethodArgumentTypeMismatchException e) {
        var tipo = e.getRequiredType();
        String dica;

        if (tipo == LocalDate.class) {
            dica = ": use uma data no formato AAAA-MM-DD";
        } else if (tipo != null && Number.class.isAssignableFrom(tipo)) {
            dica = ": use um número inteiro";
        } else {
            dica = ": valor não reconhecido";
        }

        return response(HttpStatus.BAD_REQUEST, "Parâmetro '" + e.getName() + "' inválido" + dica);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorResponse> MissingParameter(MissingServletRequestParameterException e) {
        return response(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: '" + e.getParameterName() + "'");
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message) {
        String responseMessage = message == null ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status).body(new ApiErrorResponse(responseMessage));
    }
}
