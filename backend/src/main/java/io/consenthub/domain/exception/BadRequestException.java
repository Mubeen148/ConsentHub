package io.consenthub.domain.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ConsentHubException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(String code, String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, code, message, fieldErrors);
    }
}
