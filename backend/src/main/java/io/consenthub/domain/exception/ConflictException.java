package io.consenthub.domain.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ConsentHubException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
