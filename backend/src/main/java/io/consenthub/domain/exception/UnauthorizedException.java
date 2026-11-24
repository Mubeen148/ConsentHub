package io.consenthub.domain.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ConsentHubException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
