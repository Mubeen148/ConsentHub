package io.consenthub.domain.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ConsentHubException {

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
