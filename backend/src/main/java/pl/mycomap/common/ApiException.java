package pl.mycomap.common;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ApiError error;
    public ApiException(HttpStatus status, String code, String message) {
        this(status, new ApiError(code, message));
    }
    public ApiException(HttpStatus status, ApiError error) {
        super(error.message());
        this.status = status;
        this.error = error;
    }
    public HttpStatus status() { return status; }
    public ApiError error() { return error; }
    public static ApiException invalidPassword() {
        return new ApiException(HttpStatus.BAD_REQUEST, new ApiError("VALIDATION_ERROR", "Sprawdź dane formularza.",
                Map.of("password", "Hasło może zajmować maksymalnie 72 bajty UTF-8.")));
    }
}
