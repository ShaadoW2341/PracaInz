package pl.mycomap.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) { return ResponseEntity.status(e.status()).body(e.error()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        var fields = new LinkedHashMap<String, String>();
        e.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Sprawdź dane formularza.", fields));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> duplicate() {
        return ResponseEntity.status(409).body(new ApiError("ACCOUNT_EXISTS", "Nazwa użytkownika lub e-mail są już zajęte."));
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> credentials() {
        return ResponseEntity.status(401).body(new ApiError("INVALID_CREDENTIALS", "Nieprawidłowa nazwa użytkownika lub hasło."));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson() { return ResponseEntity.badRequest().body(new ApiError("INVALID_JSON", "Nieprawidłowy format danych.")); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound() { return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Nie znaleziono zasobu.")); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method() { return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", "Niedozwolona metoda żądania.")); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> media() { return ResponseEntity.status(415).body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "Oczekiwano danych JSON.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Błąd obsługi API: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "Wystąpił błąd serwera. Spróbuj ponownie później."));
    }
}
