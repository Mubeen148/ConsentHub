package io.consenthub.domain.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ConsentHubException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
